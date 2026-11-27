package com.gree.remote.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 格力云端签名用到的时间格式工具。
 * t = UTC 时间 yyyy-MM-dd HH:mm:ss；r = 秒级时间戳（新协议）。
 */
object TimeUtils {

    private const val PATTERN = "yyyy-MM-dd HH:mm:ss"

    /** UTC 字符串，形如 2026-11-01 12:34:56（每次新建 SimpleDateFormat，线程安全）。 */
    fun utcNow(): String = utcFmt().format(Date())

    /** 由指定毫秒时间戳生成 UTC 字符串（保证 t 与 r 同源同一秒）。 */
    fun utcNow(millis: Long): String = utcFmt().format(Date(millis))

    private fun utcFmt() = SimpleDateFormat(PATTERN, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /** 当前秒级时间戳（新协议 r 为秒，旧协议为毫秒）。 */
    fun secondsNow(): Long = System.currentTimeMillis() / 1000L
}
