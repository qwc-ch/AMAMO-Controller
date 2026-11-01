package com.gree.remote.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 格力云端签名用到的时间格式工具。
 * t = UTC 时间 yyyy-MM-dd HH:mm:ss；r = 毫秒时间戳。
 */
object TimeUtils {

    private val utcFmt: SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    /** UTC 字符串，形如 2026-11-01 12:34:56 */
    fun utcNow(): String = utcFmt.format(Date())

    /** 当前毫秒时间戳 */
    fun millisNow(): String = System.currentTimeMillis().toString()
}