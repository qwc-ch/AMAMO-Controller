package com.gree.remote.data.lan

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 二代加密：AES-128-GCM。
 * 用于固件 >=1.21 的 bind / status / cmd。
 * 固定 12 字节 nonce、AAD qualcomm-test；GCM tag 单独放外层 `tag` 字段（base64）。
 */
object CipherV2 {

    /** 通用密钥（bind 阶段）。 */
    const val COMMON_KEY = "{yxAHAY_Lm6pbC/<"

    /** 固定 12 字节 nonce（hex: 5440784449675a516c5e6313）。 */
    private val NONCE: ByteArray = byteArrayOf(
        0x54, 0x40, 0x78, 0x44, 0x49, 0x67, 0x5A, 0x51, 0x6C, 0x5E, 0x63, 0x13
    )

    /** AAD。 */
    private val AAD: ByteArray = "qualcomm-test".toByteArray(Charsets.US_ASCII)

    /** GCM tag 长度（字节）。 */
    private const val TAG_LENGTH_BITS = 128
    private const val TAG_BYTES = 16

    private fun newCipher(mode: Int, key: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LENGTH_BITS, NONCE))
            updateAAD(AAD)
        }

    /**
     * 加密：返回 (密文 base64, tag base64)。Java 的 doFinal 返回 密文||tag，拆出尾部 16 字节作 tag。
     */
    fun encrypt(plaintext: String, key: String): Pair<String, String> {
        val c = newCipher(Cipher.ENCRYPT_MODE, key.toByteArray(Charsets.US_ASCII))
        val out = c.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ciphertext = out.copyOf(out.size - TAG_BYTES)
        val tag = out.copyOfRange(out.size - TAG_BYTES, out.size)
        return Base64.encode(ciphertext) to Base64.encode(tag)
    }

    /**
     * 解密：密文（base64）+ 可选 tag（base64）。
     * JCA 的 Cipher 没有 setAuthTag：外部传入的 tag 需拼回密文尾部一起交给 doFinal；
     * tag 缺失时 GCM 会在 doFinal 校验阶段抛 AEADBadTagException。
     */
    fun decrypt(ciphertextB64: String, key: String, tagB64: String?): String {
        val ciphertext = Base64.decode(ciphertextB64)
        val tag = tagB64?.let { Base64.decode(it) }
        val input = if (tag != null) ciphertext + tag else ciphertext
        val c = newCipher(Cipher.DECRYPT_MODE, key.toByteArray(Charsets.US_ASCII))
        val plain = c.doFinal(input)
        return String(plain, Charsets.UTF_8)
    }
}

/** 统一 Base64 编解码：用 JVM 自带的 java.util.Base64，保证协议层可在 JVM 单测。 */
object Base64 {
    fun encode(data: ByteArray): String = java.util.Base64.getEncoder().encodeToString(data)
    fun decode(b64: String): ByteArray = java.util.Base64.getDecoder().decode(b64)
}