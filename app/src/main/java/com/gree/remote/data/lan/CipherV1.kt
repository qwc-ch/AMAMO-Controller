package com.gree.remote.data.lan

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 一代加密：AES-128-ECB + PKCS7。
 * 用于 bind（通用密钥）以及旧固件的 status/cmd（设备密钥）。
 */
object CipherV1 {

    /** 通用密钥（bind 阶段，用于获取设备专属密钥）。 */
    const val COMMON_KEY = "a3K8Bx%2r8Y7#xDh"

    private fun cipher(mode: Int, key: ByteArray): Cipher =
        Cipher.getInstance("AES/ECB/PKCS5Padding").apply {
            init(mode, SecretKeySpec(key, "AES"))
        }

    /** AES-ECB 加密后返回密文。 */
    fun encrypt(plaintext: String, key: String): ByteArray =
        cipher(Cipher.ENCRYPT_MODE, key.toByteArray(Charsets.US_ASCII)).doFinal(
            plaintext.toByteArray(Charsets.UTF_8)
        )

    /** AES-ECB 解密为 UTF-8 明文。 */
    fun decrypt(ciphertext: ByteArray, key: String): String =
        String(
            cipher(Cipher.DECRYPT_MODE, key.toByteArray(Charsets.US_ASCII))
                .doFinal(ciphertext),
            Charsets.UTF_8
        )
}