package com.gree.remote.data.lan

import android.content.Context
import android.net.wifi.WifiManager
import com.gree.remote.data.model.AcState
import com.gree.remote.data.model.CipherVersion
import com.gree.remote.data.model.ScannedDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * 格力局域网协议客户端：scan -> bind -> status / cmd 状态机。
 * 双加密器自动协商：bind 先 V1(ECB)，超时/失败换 V2(GCM)；成功后记住设备代际。
 *
 * 参考实现（协议源头）：
 *  - tomikaa87/gree-remote（scan/bind/status/cmd 报文与 AES-ECB）
 *  - inwaar/gree-hvac-client（AES-GCM 适配，bind 时 ECB 先、GCM 兜底）
 *
 * ⚠️ Android 坑：系统默认过滤组播/广播包，收 UDP 广播响应必须持有
 * [WifiManager.MulticastLock]（权限 CHANGE_WIFI_MULTICAST_STATE 已声明），
 * 否则会出现"PC 能扫到、手机扫不到"。所有 scan 均在锁内进行。
 */
class GreeLanClient(
    private val json: Json,
    context: Context,
) {
    companion object {
        const val PORT = 7000
        const val BROADCAST_ADDR = "255.255.255.255"
        private const val APP_CID = "app"
        private const val SCAN_PACKET_TIMEOUT_MS = 500L
        private const val BIND_TIMEOUT_MS = 3000L
        private const val CMD_TIMEOUT_MS = 1500L

        /** 完整状态查询列（与 gree-remote PythonCLI / status 报文一致）。 */
        val STATUS_COLS = listOf(
            "Pow", "Mod", "SetTem", "WdSpd", "Air", "Blo", "Health", "SwhSlp",
            "Lig", "SwingLfRig", "SwUpDn", "Quiet", "Tur", "StHt", "TemUn",
            "HeatCoolType", "TemRec", "SvSt",
        )
    }

    private val socket = DatagramSocket().apply { broadcast = true }

    /** 组播锁：scan 期间持有，结束即释放（引用计数模式）。 */
    private val multicastLock: WifiManager.MulticastLock =
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createMulticastLock("AMAMO-lan-scan")
            .apply { setReferenceCounted(true) }

    /** 最近一次 scan 到的设备 IP（用于后续单播）。 */
    @Volatile
    private var deviceIp: String? = null

    /**
     * status/cmd 实际生效的密钥缓存（本实例按设备绑定）。
     * ⚠️ 真机实测（V3.4.M，mac c0…8dc8）：bind 返回设备专属 key，但 status/cmd 仍用
     * V1 通用密钥（用专属 key 设备不应答）。因此请求先用 bind key，超时自动换通用密钥，
     * 成功后缓存生效组合，后续命令不再试错。
     */
    @Volatile
    private var effectiveKey: Pair<String, CipherVersion>? = null

    /**
     * 广播发现设备（单台便捷入口）：广播一次，取最先应答的设备。
     */
    suspend fun scan(): ScannedDevice =
        scanAll(timeoutMs = 1500L).firstOrNull()?.device
            ?: throw IOException("未发现局域网空调，请确认手机与空调在同一 WiFi")

    /**
     * 广播发现全部设备：广播一次，在 [timeoutMs] 窗口内收集所有去重后的响应。
     * @return 设备信息 + 应答来源 IP（后续单播 bind/status 用）。
     */
    suspend fun scanAll(timeoutMs: Long = 2000L): List<LanScanResult> =
        withContext(Dispatchers.IO) {
            multicastLock.acquire()
            try {
                val scan = buildJsonObject { put("t", "scan") }
                val bytes = scan.toString().toByteArray(Charsets.UTF_8)
                val broadcast = InetAddress.getByName(BROADCAST_ADDR)
                val pkt = DatagramPacket(bytes, bytes.size, broadcast, PORT)
                socket.soTimeout = SCAN_PACKET_TIMEOUT_MS.toInt()
                socket.send(pkt)

                val deadline = System.currentTimeMillis() + timeoutMs
                val found = LinkedHashMap<String, LanScanResult>()
                val buf = ByteArray(2048)
                while (System.currentTimeMillis() < deadline) {
                    val recv = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(recv)
                    } catch (_: SocketTimeoutException) {
                        continue // 等待窗口内继续收
                    }
                    val ip = recv.address.hostAddress ?: continue
                    val dev = try {
                        parseScan(recv)
                    } catch (_: Exception) {
                        continue // 非设备应答/坏包，跳过
                    }
                    if (dev.mac.isNotBlank() && !found.containsKey(dev.mac)) {
                        found[dev.mac] = LanScanResult(device = dev, ip = ip)
                    }
                }
                found.values.toList()
            } finally {
                if (multicastLock.isHeld) multicastLock.release()
            }
        }

    /** 手动指定设备 IP（应对 AP 隔离/多 AP 场景）。 */
    fun setDeviceIp(ip: String) {
        deviceIp = ip
    }

    /** 当前已知的设备 IP。 */
    fun currentDeviceIp(): String? = deviceIp

    /** 从 scan 响应报文解析设备信息。 */
    private fun parseScan(resp: DatagramPacket): ScannedDevice {
        val text = String(resp.data, 0, resp.length, Charsets.UTF_8)
        val outer = json.parseToJsonElement(text).jsonObject
        // scan 响应是 pack 信封，pack 用通用密钥加密（V1）
        val packB64 = outer["pack"]!!.jsonPrimitive.content
        val innerJson = CipherV1.decrypt(Base64.decode(packB64), CipherV1.COMMON_KEY)
        val inner = json.parseToJsonElement(innerJson).jsonObject
        return ScannedDevice(
            type = inner["t"]?.jsonPrimitive?.contentOrNull ?: "",
            cid = inner["cid"]?.jsonPrimitive?.contentOrNull ?: "",
            mac = inner["mac"]?.jsonPrimitive?.contentOrNull ?: "",
            mid = inner["mid"]?.jsonPrimitive?.contentOrNull ?: "",
            name = inner["name"]?.jsonPrimitive?.contentOrNull ?: "",
            brand = inner["brand"]?.jsonPrimitive?.contentOrNull ?: "",
            bc = inner["bc"]?.jsonPrimitive?.contentOrNull ?: "",
            ver = inner["ver"]?.jsonPrimitive?.contentOrNull ?: "",
            hid = inner["hid"]?.jsonPrimitive?.contentOrNull ?: "",
            pv = inner["pv"]?.jsonPrimitive?.intOrNull ?: 0,
            sub = inner["sub"]?.jsonPrimitive?.booleanOrNull ?: false,
            uid = inner["uid"]?.jsonPrimitive?.intOrNull ?: 0,
        )
    }

    /**
     * 与指定 MAC 绑定，协商加密代际并取得设备专属密钥。
     * @param mac 设备 MAC（大写十六进制无冒号）。
     * @param preferred 从 scan 预判的代际；null 表示未知。
     */
    suspend fun bind(mac: String, preferred: CipherVersion? = null): Pair<CipherVersion, String> {
        val candidates = when (preferred) {
            CipherVersion.V1 -> listOf(CipherVersion.V1, CipherVersion.V2)
            CipherVersion.V2 -> listOf(CipherVersion.V2, CipherVersion.V1)
            null -> listOf(CipherVersion.V1, CipherVersion.V2)
        }
        var lastErr: Exception? = null
        for (ver in candidates) {
            try {
                val key = bindOnce(mac, ver)
                return ver to key
            } catch (e: IOException) {
                lastErr = e
            }
        }
        throw lastErr ?: IOException("bind failed")
    }

    private suspend fun bindOnce(mac: String, ver: CipherVersion): String {
        val inner = buildJsonObject {
            put("mac", mac)
            put("t", "bind")
            put("uid", 0)
        }
        val (pack, tag) = encryptInner(inner.toString(), CipherV1.COMMON_KEY, ver)
        val env = buildEnvelope(pack, tag, mac)
        val resp = sendToMac(mac, env, BIND_TIMEOUT_MS)
        val outer = json.parseToJsonElement(String(resp.data, 0, resp.length, Charsets.UTF_8)).jsonObject
        val respPack = outer["pack"]!!.jsonPrimitive.content
        val respTag = outer["tag"]?.jsonPrimitive?.contentOrNull
        val plain = decryptInner(respPack, CipherV1.COMMON_KEY, ver, respTag)
        val obj = json.parseToJsonElement(plain).jsonObject
        if (obj["t"]?.jsonPrimitive?.contentOrNull != "bindok") {
            throw IOException("bind returned non-bindok")
        }
        return obj["key"]!!.jsonPrimitive.content
    }

    /** 查询状态。bind key 无应答时自动换 V1 通用密钥兜底（见 [effectiveKey] 注释）。 */
    suspend fun status(mac: String, key: String, ver: CipherVersion): AcState {
        val (k, v) = effectiveKey ?: (key to ver)
        return try {
            statusWith(mac, k, v).also { effectiveKey = k to v }
        } catch (e: Exception) {
            if (k == CipherV1.COMMON_KEY && v == CipherVersion.V1) throw e
            statusWith(mac, CipherV1.COMMON_KEY, CipherVersion.V1).also {
                effectiveKey = CipherV1.COMMON_KEY to CipherVersion.V1
            }
        }
    }

    private suspend fun statusWith(mac: String, key: String, ver: CipherVersion): AcState {
        val inner = buildJsonObject {
            put("cols", json.encodeToJsonElement(STATUS_COLS))
            put("mac", mac)
            put("t", "status")
        }
        val (pack, tag) = encryptInner(inner.toString(), key, ver)
        val env = buildEnvelope(pack, tag, mac)
        val resp = sendToMac(mac, env, CMD_TIMEOUT_MS)
        val outer = json.parseToJsonElement(String(resp.data, 0, resp.length, Charsets.UTF_8)).jsonObject
        val respPack = outer["pack"]!!.jsonPrimitive.content
        val respTag = outer["tag"]?.jsonPrimitive?.contentOrNull
        val plain = decryptInner(respPack, key, ver, respTag)
        val obj = json.parseToJsonElement(plain).jsonObject
        return parseStatusDat(obj)
    }

    /**
     * 解析 status 响应的 dat，兼容两种真实设备形态：
     * - 对象形式：{"dat": {"Pow":1, "SetTem":26, ...}}（部分新固件）；
     * - 数组形式：{"cols": [...], "dat": [1, 26, ...]}（旧固件常见，gree-remote 文档形态）。
     */
    private fun parseStatusDat(obj: JsonObject): AcState =
        when (val dat = obj["dat"]) {
            is JsonObject -> json.decodeFromJsonElement(AcState.serializer(), dat)
            is kotlinx.serialization.json.JsonArray -> {
                val cols = obj["cols"]?.jsonArray
                    ?.map { it.jsonPrimitive.content }
                    ?: throw IOException("status array response missing cols")
                val values = dat.map { it.jsonPrimitive.intOrNull ?: 0 }
                if (cols.size != values.size) throw IOException("status cols/dat size mismatch")
                val map = buildJsonObject {
                    cols.zip(values).forEach { (name, value) -> put(name, value) }
                }
                json.decodeFromJsonElement(AcState.serializer(), map)
            }
            else -> throw IOException("unexpected status dat type")
        }

    /** 下发控制命令。opt = 字段名，p = 值。key 兜底逻辑同 [status]。 */
    suspend fun cmd(mac: String, key: String, ver: CipherVersion, opt: String, value: Int) =
        cmd(mac, key, ver, listOf(opt to value))

    /**
     * 批量下发控制命令：opt/p 数组一次报文完成（如 SetTem + TemRec 半度组合）。
     * 命令值为绝对值（幂等），兜底重发安全。
     */
    suspend fun cmd(mac: String, key: String, ver: CipherVersion, opts: List<Pair<String, Int>>) {
        val (k, v) = effectiveKey ?: (key to ver)
        try {
            cmdWith(mac, k, v, opts).also { effectiveKey = k to v }
        } catch (e: Exception) {
            if (k == CipherV1.COMMON_KEY && v == CipherVersion.V1) throw e
            cmdWith(mac, CipherV1.COMMON_KEY, CipherVersion.V1, opts).also {
                effectiveKey = CipherV1.COMMON_KEY to CipherVersion.V1
            }
        }
    }

    private suspend fun cmdWith(
        mac: String,
        key: String,
        ver: CipherVersion,
        opts: List<Pair<String, Int>>,
    ) {
        val inner = buildJsonObject {
            put("opt", json.encodeToJsonElement(opts.map { it.first }))
            put("p", json.encodeToJsonElement(opts.map { it.second }))
            put("t", "cmd")
            put("sub", false)
            put("mac", mac)
            put("uid", 0)
        }
        val (pack, tag) = encryptInner(inner.toString(), key, ver)
        val env = buildEnvelope(pack, tag, mac)
        val resp = sendToMac(mac, env, CMD_TIMEOUT_MS)
        val outer = json.parseToJsonElement(String(resp.data, 0, resp.length, Charsets.UTF_8)).jsonObject
        val respPack = outer["pack"]!!.jsonPrimitive.content
        val respTag = outer["tag"]?.jsonPrimitive?.contentOrNull
        val plain = decryptInner(respPack, key, ver, respTag)
        val obj = json.parseToJsonElement(plain).jsonObject
        if (obj["r"]?.jsonPrimitive?.intOrNull != 200) {
            throw IOException("cmd rejected: $plain")
        }
    }

    // ---- 报文编解码辅助 ----

    private fun buildEnvelope(pack: String, tag: String?, tcid: String): String =
        if (tag != null) {
            buildJsonObject {
                put("cid", APP_CID)
                put("i", 1)
                put("t", "pack")
                put("uid", 0)
                put("tcid", tcid)
                put("tag", tag)
                put("pack", pack)
            }.toString()
        } else {
            buildJsonObject {
                put("cid", APP_CID)
                put("i", 1)
                put("t", "pack")
                put("uid", 0)
                put("tcid", tcid)
                put("pack", pack)
            }.toString()
        }

    private fun encryptInner(plain: String, key: String, ver: CipherVersion): Pair<String, String?> =
        when (ver) {
            CipherVersion.V1 -> CipherV1.encrypt(plain, key).let {
                Base64.encode(it) to null
            }
            CipherVersion.V2 -> {
                val (pack, tag) = CipherV2.encrypt(plain, key)
                pack to tag
            }
        }

    private fun decryptInner(packB64: String, key: String, ver: CipherVersion, tagB64: String?): String =
        when (ver) {
            CipherVersion.V1 -> CipherV1.decrypt(Base64.decode(packB64), key)
            CipherVersion.V2 -> CipherV2.decrypt(packB64, key, tagB64)
        }

    private suspend fun sendToMac(mac: String, envelope: String, timeoutMs: Long): DatagramPacket =
        withContext(Dispatchers.IO) {
            val ip = deviceIp ?: throw IOException("No device IP; run scan() or setDeviceIp() first")
            val bytes = envelope.toByteArray(Charsets.UTF_8)
            val addr = java.net.InetAddress.getByName(ip)
            val send = DatagramPacket(bytes, bytes.size, addr, PORT)
            socket.soTimeout = timeoutMs.toInt()
            socket.send(send)
            val buf = ByteArray(2048)
            val recv = DatagramPacket(buf, buf.size)
            socket.receive(recv)
            recv
        }

    /** 归一化 MAC：去掉冒号并转大写。 */
    fun normalizeMac(mac: String): String =
        mac.replace(":", "").uppercase()

    fun close() {
        socket.close()
    }
}

/** 一次局域网扫描应答：设备信息 + 来源 IP。 */
data class LanScanResult(
    val device: ScannedDevice,
    val ip: String,
)