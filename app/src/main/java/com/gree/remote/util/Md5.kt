package com.gree.remote.util

import java.security.MessageDigest

/** MD5 十六进制小写字符串。 */
fun md5Hex(input: String): String {
    val digest = MessageDigest.getInstance("MD5")
    return digest.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") {
        "%02x".format(it)
    }
}