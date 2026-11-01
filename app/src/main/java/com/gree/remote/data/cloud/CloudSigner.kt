package com.gree.remote.data.cloud

import com.gree.remote.util.md5Hex
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 格力云端签名：按公开逆向实现（xuqiu/hass gree/__init__.py）逐请求构造 api 与 datVc。
 *
 * 规则：
 *  - GREE_KEY = d15cb842b7fd704ebcf8276f34cbd771
 *  - appId = 5686063144437916735
 *  - api: {appId, r(毫秒), t(UTC yyyy-MM-dd HH:mm:ss), vc}
 *    vc = md5(appId + "_" + GREE_KEY + "_" + t + "_" + r)
 *  - datVc = md5(GREE_KEY + "_" + 字段依次用 "_" 拼接)
 *
 *  注意：常量与端点最终以你自己的抓包为准（技术方案 §8）。
 */
object CloudSigner {

    const val GREE_KEY = "d15cb842b7fd704ebcf8276f34cbd771"
    const val APP_ID = "5686063144437916735"
    private const val SEP = "_"

    fun api(): JsonObject {
        val t = com.gree.remote.util.TimeUtils.utcNow()
        val r = com.gree.remote.util.TimeUtils.millisNow()
        val vc = md5Hex(APP_ID + SEP + GREE_KEY + SEP + t + SEP + r)
        return buildJsonObject {
            put("appId", APP_ID)
            put("r", r)
            put("t", t)
            put("vc", vc)
        }
    }

    /** datVc = md5(GREE_KEY + "_" + 各字段按给定顺序用 "_" 拼接)。 */
    fun datVc(vararg datas: String): String {
        val sb = StringBuilder(GREE_KEY)
        for (d in datas) sb.append(SEP).append(d)
        return md5Hex(sb.toString())
    }

    /** 登录密码三段 MD5：psw = md5(md5(md5(pwd) + pwd) + t)。 */
    fun psw(pwd: String, t: String): String {
        val inner = md5Hex(pwd) + pwd
        val middle = md5Hex(inner)
        return md5Hex(middle + t)
    }
}

/** 云端登录凭据。 */
data class CloudCredentials(
    val uid: String,
    val token: String,
    val homeId: String = "",
)