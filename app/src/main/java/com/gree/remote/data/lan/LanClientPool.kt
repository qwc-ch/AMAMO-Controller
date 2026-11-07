package com.gree.remote.data.lan

import android.content.Context
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * 局域网客户端池：每台设备（按 MAC）一个 [GreeLanClient] 实例。
 * GreeLanClient 内部持有单个 UDP socket 与"当前设备 IP"状态，
 * 多设备并行控制/轮询必须各用各的 socket，否则会互相串 IP。
 *
 * [scanClient] 专用于广播扫描（无目标状态）；bind/status/cmd 走 [forMac] 的专属实例。
 */
class LanClientPool(
    private val json: Json,
    context: Context,
) : AutoCloseable {

    // 构造参数不能在成员函数中引用，先固化为属性
    private val appContext: Context = context.applicationContext

    private val clients = ConcurrentHashMap<String, GreeLanClient>()

    /** 共享扫描实例（只做广播 scan，不参与 bind/status）。 */
    val scanClient: GreeLanClient by lazy { GreeLanClient(json, appContext) }

    /** 取（或创建）某台设备的专属客户端。 */
    fun forMac(mac: String): GreeLanClient =
        clients.getOrPut(mac) { GreeLanClient(json, appContext) }

    override fun close() {
        clients.values.forEach { runCatching { it.close() } }
        clients.clear()
        runCatching { scanClient.close() }
    }
}
