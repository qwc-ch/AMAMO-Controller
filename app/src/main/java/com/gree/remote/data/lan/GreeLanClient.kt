package com.gree.remote.data.lan

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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket

/**
 * 格力局域网协议客户端：scan -> bind -> status / cmd 状态机。
 * 双加密器自动协商：bind 先 V1(ECB)，超时/失败换 V2(GCM)；成功后记住设备代际。
 *
 * 参考实现（协议源头）：
 *  - tomikaa87/gree-remote（scan/bind/status/cmd 报文与 AES-ECB）
 *  - inwaar/gree-hvac-client（AES-GCM 适配，bind 时 ECB 先、GCM 兜底）
 */
class GreeLanClient(
    private val json: Json,
) {
    companion object {
        const val PORT = 7000
        const val BROADCAST_ADDR = "255.255.255.255"
        private const val APP_CID = "app"
        private const val SCAN_TIMEOUT_MS = 800L
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

    /** 最近一次 scan 到的设备 IP（用于后续单播）。 */
    @Volatile
    private var deviceIp: String? = null

    /**
     * 广播发现设备。返回设备 MAC（大写十六进制无冒号）与响应包。
     */
    suspend fun scan(): ScannedDevice {
        val resp = withContext(Dispatchers.IO) {
            val scan = buildJsonObject { put("t", "scan") }
            val bytes = scan.toString().toByteArray(Charsets.UTF_8)
            val broadcast = java.net.InetAddress.getByName(BROADCAST_ADDR)
            val pkt = DatagramPacket(bytes, bytes.size, broadcast, PORT)
            socket.soTimeout = SCAN_TIMEOUT_MS.toInt()
            socket.send(pkt)
            val buf = ByteArray(2048)
            val recv = DatagramPacket(buf, buf.size)
            socket.receive(recv)
            recv
        }
        deviceIp = resp.address?.hostAddress
        return parseScan(resp)
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

    /** 查询状态。 */
    suspend fun status(mac: String, key: String, ver: CipherVersion): AcState {
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
        val dat = obj["dat"]!!.jsonObject
        return parseDat(dat)
    }

    /** 下发控制命令。opt = 字段名，p = 值。 */
    suspend fun cmd(mac: String, key: String, ver: CipherVersion, opt: String, value: Int) {
        val inner = buildJsonObject {
            put("opt", opt)
            put("p", value)
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

    /** 从 status 响应的 dat 对象解码为 AcState。 */
    private fun parseDat(dat: JsonObject): AcState =
        json.decodeFromJsonElement(AcState.serializer(), dat)

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