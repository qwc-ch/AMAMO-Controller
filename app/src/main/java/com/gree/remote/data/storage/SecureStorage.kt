package com.gree.remote.data.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 用 Android Keystore（AES-GCM）加密敏感字符串后存入 SharedPreferences。
 * 密钥不可导出，随设备 Keystore 持久保存。
 */
class SecureStorage(context: Context) {

    private val prefs = context.getSharedPreferences("gree_secure", Context.MODE_PRIVATE)
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private val ALIAS = "gree_remote_master"

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    /** 加密并写盘。 */
    fun put(key: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val record = Base64.encode(iv) + ":" + Base64.encode(encrypted)
        prefs.edit().putString(key, record).apply()
    }

    /** 读取并解密；无记录返回 null。 */
    fun get(key: String): String? {
        val record = prefs.getString(key, null) ?: return null
        val sep = record.indexOf(':')
        val iv = Base64.decode(record.substring(0, sep))
        val data = Base64.decode(record.substring(sep + 1))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

/** 供 SecureStorage 内部使用的 Base64。 */
private object Base64 {
    fun encode(data: ByteArray): String = java.util.Base64.getEncoder().encodeToString(data)
    fun decode(b64: String): ByteArray = java.util.Base64.getDecoder().decode(b64)
}