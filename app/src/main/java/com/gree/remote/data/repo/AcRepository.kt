package com.gree.remote.data.repo

import com.gree.remote.data.cloud.GreeCloudClient
import com.gree.remote.data.lan.GreeLanClient
import com.gree.remote.data.model.AcDevice
import com.gree.remote.data.model.AcState
import com.gree.remote.data.model.CipherVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 双通道控制仓储：优先局域网直连（低延迟、不耗云配额），失败降级到云端。
 * 对应技术方案 §2.3 的双通道策略。
 */
class AcRepository(
    private val lanClient: GreeLanClient,
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
     * 发现并控制：优先 UDP 直连。
     * 局域网模式会完成 scan -> bind -> status。
     */
    suspend fun connectLan(manualIp: String? = null): LanSession {
        return withContext(Dispatchers.IO) {
            manualIp?.let { lanClient.setDeviceIp(it) }
            val dev = lanClient.scan()
            val mac = lanClient.normalizeMac(dev.mac)
            val (ver, key) = lanClient.bind(mac)
            val state = lanClient.status(mac, key, ver)
            _activeChannel.value = Channel.LAN
            LanSession(
                device = AcDevice(
                    id = mac, name = dev.name, mac = mac,
                    modelId = dev.mid, cipherVersion = ver,
                ),
                key = key, state = state,
            )
        }
    }

    /** 局域网模式下刷新状态。 */
    suspend fun refreshLanState(session: LanSession): AcState =
        withContext(Dispatchers.IO) {
            lanClient.status(session.device.mac, session.key, session.device.cipherVersion!!)
        }

    /** 局域网模式下下发命令。 */
    suspend fun lanCmd(session: LanSession, opt: String, value: Int): AcState {
        withContext(Dispatchers.IO) {
            lanClient.cmd(session.device.mac, session.key, session.device.cipherVersion!!, opt, value)
        }
        return refreshLanState(session)
    }

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
        lanClient.close()
    }
}

/** 局域网已建立的一次会话。 */
data class LanSession(
    val device: AcDevice,
    val key: String,
    val state: AcState,
)