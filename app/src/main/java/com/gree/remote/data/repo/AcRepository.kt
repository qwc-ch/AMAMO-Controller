package com.gree.remote.data.repo

import com.gree.remote.data.cloud.GreeCloudClient
import com.gree.remote.data.lan.GreeLanClient
import com.gree.remote.data.lan.LanClientPool
import com.gree.remote.data.model.AcDevice
import com.gree.remote.data.model.AcState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 双通道控制仓储：优先局域网直连（低延迟、不耗云配额），失败降级到云端。
 * 对应技术方案 §2.3 的双通道策略。
 *
 * 局域网侧使用 [LanClientPool]：每台设备一个客户端实例，支持首页多设备分页并行。
 */
class AcRepository(
    private val lanPool: LanClientPool,
    private val cloudClient: GreeCloudClient,
) {
    enum class Channel { AUTO, LAN, CLOUD }

    private val _channel = MutableStateFlow<Channel>(Channel.AUTO)
    val channel: StateFlow<Channel> = _channel.asStateFlow()

    private val _activeChannel = MutableStateFlow<Channel>(Channel.LAN)
    /** 当前实际生效的通道（用于 UI 展示）。 */
    val activeChannel: StateFlow<Channel> = _activeChannel.asStateFlow()

    /** 探测通道偏好。 */
    fun setChannel(c: Channel) {
        _channel.value = c
    }

    /**
     * 局域网纯扫描（只发现不 bind）：供首页列出局域网内的空调。
     */
    suspend fun scanLan(): List<LanDiscoveredDevice> =
        withContext(Dispatchers.IO) {
            lanPool.scanClient.scanAll().map { result ->
                val dev = result.device
                LanDiscoveredDevice(
                    mac = lanPool.scanClient.normalizeMac(dev.mac),
                    name = dev.name.ifBlank { "格力空调 ${dev.mac.takeLast(4)}" },
                    ip = result.ip,
                    firmware = dev.ver,
                    modelId = dev.mid,
                    brand = dev.brand,
                )
            }
        }

    /**
     * 建立某台设备的局域网会话：bind -> status（加密代际自动协商）。
     * 已知 [manualIp]（来自首页扫描结果）时直接单播；否则广播 scan 取第一台应答设备。
     */
    suspend fun connectLan(
        manualIp: String? = null,
        targetMac: String? = null,
        firmwareVer: String? = null,
    ): LanSession {
        return withContext(Dispatchers.IO) {
            // 未知目标：先用共享实例广播扫描，拿到 mac + ip
            val scanned = if (targetMac == null || manualIp == null) {
                val dev = lanPool.scanClient.scan()
                dev to lanPool.scanClient.currentDeviceIp()
            } else {
                null
            }
            val mac = (targetMac ?: scanned?.first?.mac)?.let { normalize(it) }!!
            val client = lanPool.forMac(mac)
            (manualIp ?: scanned?.second)?.let { client.setDeviceIp(it) }
            // 实测（V3.4.M，2026-11）：bind 走 V1（AES-ECB），"新固件走 V2"的推测不成立；
            // 固定 V1 优先、V2 自动兜底，V2-only 设备（如有）多一次超时。
            val (ver, key) = client.bind(mac, preferred = null)
            val state = client.status(mac, key, ver)
            _activeChannel.value = Channel.LAN
            LanSession(
                device = AcDevice(
                    id = mac, name = scanned?.first?.name ?: "", mac = mac,
                    modelId = scanned?.first?.mid ?: "", cipherVersion = ver,
                ),
                key = key, state = state,
            )
        }
    }

    /** 局域网模式下刷新状态。 */
    suspend fun refreshLanState(session: LanSession): AcState =
        withContext(Dispatchers.IO) {
            sessionLanClient(session).status(session.device.mac, session.key, session.device.cipherVersion!!)
        }

    /** 局域网模式下下发命令。opt = 字段名，value = 值。 */
    suspend fun lanCmd(session: LanSession, opt: String, value: Int): AcState =
        lanCmdMulti(session, listOf(opt to value))

    /**
     * 批量下发命令：一条报文完成（如 SetTem + TemRec 半度组合），随后刷新状态。
     */
    suspend fun lanCmdMulti(session: LanSession, opts: List<Pair<String, Int>>): AcState {
        withContext(Dispatchers.IO) {
            sessionLanClient(session).cmd(session.device.mac, session.key, session.device.cipherVersion!!, opts)
        }
        return refreshLanState(session)
    }

    /** 会话对应的设备专属客户端。 */
    private fun sessionLanClient(session: LanSession): GreeLanClient =
        lanPool.forMac(session.device.mac)

    /** 云端模式：取设备列表。 */
    suspend fun cloudDevices(): List<AcDevice> =
        withContext(Dispatchers.IO) {
            _activeChannel.value = Channel.CLOUD
            cloudClient.getDevices().devList.map {
                AcDevice(
                    id = it.mac, name = it.name, mac = it.mac,
                    modelId = it.model, online = it.online == 1,
                )
            }
        }

    /** 云端模式下下发命令。⚠️ 端点需 §8 抓包定案。 */
    suspend fun cloudCmd(mac: String, opt: String, value: Int) {
        withContext(Dispatchers.IO) {
            cloudClient.devControl(mac, opt, value)
        }
    }

    fun close() {
        lanPool.close()
    }

    private fun normalize(mac: String): String = mac.replace(":", "").uppercase()
}

/** 局域网已建立的一次会话。 */
data class LanSession(
    val device: AcDevice,
    val key: String,
    val state: AcState,
)

/** 局域网扫描发现的设备（未 bind，供首页设备分页展示与选择）。 */
data class LanDiscoveredDevice(
    val mac: String,
    val name: String,
    val ip: String,
    val firmware: String,
    val modelId: String,
    val brand: String,
)
