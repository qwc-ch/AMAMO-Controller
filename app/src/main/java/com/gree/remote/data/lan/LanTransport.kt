package com.gree.remote.data.lan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * UDP 7000 传输层：封装 DatagramSocket 的发送与接收。
 * 处理广播发现、单播直连、超时。
 */
class LanTransport(
    private val socket: DatagramSocket,
) : AutoCloseable {

    companion object {
        const val PORT = 7000
        const val BROADCAST_ADDR = "255.255.255.255"
        private const val MAX_PACKET_SIZE = 2048
    }

    /** 广播扫描：向 255.255.255.255:7000 发送明文报文，等待首个响应。 */
    suspend fun broadcastScan(payload: ByteArray, timeoutMs: Long): DatagramPacket =
        withContext(Dispatchers.IO) {
            socket.soTimeout = timeoutMs.toInt()
            val broadcast = InetAddress.getByName(BROADCAST_ADDR)
            val send = DatagramPacket(payload, payload.size, broadcast, PORT)
            socket.send(send)
            receive(timeoutMs)
        }

    /** 单播直连：向指定 IP 发送报文。 */
    suspend fun unicast(ip: String, payload: ByteArray, timeoutMs: Long): DatagramPacket =
        withContext(Dispatchers.IO) {
            socket.soTimeout = timeoutMs.toInt()
            val addr = InetAddress.getByName(ip)
            val send = DatagramPacket(payload, payload.size, addr, PORT)
            socket.send(send)
            receive(timeoutMs)
        }

    private fun receive(timeoutMs: Long): DatagramPacket =
        try {
            val buf = ByteArray(MAX_PACKET_SIZE)
            val pkt = DatagramPacket(buf, buf.size)
            socket.receive(pkt)
            pkt
        } catch (e: SocketTimeoutException) {
            throw IOException("UDP receive timeout after ${timeoutMs}ms", e)
        }

    /** 绑定到本地端口（通常 0 表示随机）。 */
    fun bind(localPort: Int = 0) {
        socket.bind(InetSocketAddress(localPort))
    }

    override fun close() {
        socket.close()
    }
}