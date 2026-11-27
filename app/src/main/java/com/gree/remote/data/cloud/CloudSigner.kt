package com.gree.remote.data.cloud

import com.gree.remote.util.md5Hex
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 格力云端签名 + AES 加解密（2025-08 起 grih.gree.com 现用协议）。
 *
 * 与旧协议（xuqiu/hass，2019）完全不同：
 *  - 新常量 APP_ID / APP_HASH / AES_KEY（旧 GREE_KEY 已失效）；
 *  - 请求体整体 AES-128-ECB 加密 + Base64，以 form-urlencoded 提交；
 *  - 响应体为 {"enRes":"<base64(AES-ECB(json))>"}，需解密。
 *
 * 已用真实 HTTP 探针验证（2026-11-27）：
 *  - 签名正确 → 错误账号返回 r=411（用户不存在/密码错误）；
 *  - 签名错误 → 解密后 r=403 "Data vc verify error"。
 *
 * 参考（外部资料）：https://github.com/luc10/gree-api-client
 */
object CloudSigner {

    const val APP_ID = "4920681951525131286"
    const val APP_HASH = "0fa513124aa97781d1f3f40d61ca1a89"
    const val AES_KEY = "#G$&^jgfujy6ujxt"
    const val GAE1 = "5ac2bdf935bcca70"

    private const val SEP = "_"

    /**
     * 保留旧常量名仅为兼容旧单测引用（旧协议已不再使用）。
     * @deprecated 使用 [APP_ID] 代替。
     */
    @Deprecated("旧协议常量，新云端已不再使用", ReplaceWith("APP_ID"))
    const val APP_ID_LEGACY = "5686063144437916735"

    @Deprecated("旧协议常量，新云端已不再使用")
    const val GREE_KEY = "d15cb842b7fd704ebcf8276f34cbd771"

    /**
     * 生成同源的 (t, r)：t = UTC yyyy-MM-dd HH:mm:ss，r = 秒级时间戳。
     * 登录的 psw 与 requestBody 的 api.t 必须用同一个 t，否则跨秒时签名失败。
     */
    fun nowPair(): Pair<String, Long> {
        val millis = System.currentTimeMillis()
        return com.gree.remote.util.TimeUtils.utcNow(millis) to (millis / 1000L)
    }

    /** 构造带 api/datVc 的完整请求体。所有字段用同一个 t/r，避免跨秒签名失败。 */
    fun requestBody(
        fields: JsonObject,
        hashProps: List<String>,
        time: Pair<String, Long> = nowPair(),
    ): JsonObject {
        val (t, r) = time
        val vc = md5Hex(listOf(APP_ID, APP_HASH, t, r.toString()).joinToString(SEP))
        // 与 Python str(value) 对齐：字符串去引号、数字保留文本。
        val values = hashProps.map { it ->
            val el = fields[it] ?: error("missing hash field: $it")
            el.toString().trim('"')
        }
        val signature = datVc(*values.toTypedArray())
        return buildJsonObject {
            put("api", buildJsonObject {
                put("appId", APP_ID)
                put("r", r)
                put("t", t)
                put("vc", vc)
            })
            fields.forEach { (k, v) -> put(k, v) }
            put("datVc", signature)
        }
    }

    /** datVc = md5(APP_HASH + "_" + 各字段按顺序用 "_" 拼接)。 */
    fun datVc(vararg datas: String): String =
        md5Hex(listOf(APP_HASH, *datas).joinToString(SEP))

    /** 登录密码三段 MD5：psw = md5(md5(md5(pwd) + pwd) + t)。 */
    fun psw(pwd: String, t: String): String {
        val inner = md5Hex(pwd) + pwd
        return md5Hex(md5Hex(inner) + t)
    }

    /** AES-128-ECB 加密（PKCS5Padding，等价于 PKCS7 for 16-byte block）。 */
    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(AES_KEY.toByteArray(Charsets.UTF_8), "AES"))
        return cipher.doFinal(plain)
    }

    /** AES-128-ECB 解密。 */
    fun decrypt(cipherText: ByteArray): ByteArray {
        val cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(AES_KEY.toByteArray(Charsets.UTF_8), "AES"))
        return cipher.doFinal(cipherText)
    }
}
