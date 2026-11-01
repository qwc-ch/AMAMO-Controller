package com.gree.remote.data.cloud

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 格力云端客户端：登录、取设备列表、下发命令，以及 402 自动重登。
 *
 * 签名由 CloudSigner 统一生成；端点形态以 §8 抓包为准。
 */
class GreeCloudClient(
    private val api: GreeCloudApi,
    /** 402 时自动重登一次。由上层提供（账号密码取自安全存储）。 */
    private val refreshLogin: suspend () -> CloudCredentials,
) {

    private var credentials: CloudCredentials? = null

    /** 已登录凭据。 */
    fun currentCredentials(): CloudCredentials? = credentials

    /**
     * 登录。成功后缓存 uid/token/homeId。
     */
    suspend fun login(user: String, password: String): CloudCredentials {
        val body = loginBody(user, password)
        val resp = api.login(body)
        checkCode(resp)
        val dat = resp.dat ?: error("login empty dat")
        val cred = CloudCredentials(uid = dat.uid, token = dat.token, homeId = dat.homeId)
        credentials = cred
        return cred
    }

    /**
     * 拉取家庭 + 设备列表。
     */
    suspend fun getDevices(): DevsData {
        return retryOn402 {
            val cred = requireCredentials()
            val body = buildJsonObject {
                put("api", CloudSigner.api())
                put("uid", cred.uid)
                put("token", cred.token)
                put("datVc", CloudSigner.datVc(cred.token, cred.uid))
            }
            api.getDevs(body)
        }.dat ?: DevsData()
    }

    /**
     * 下发控制命令。
     * ⚠️ 这是待抓包定案的端点；此处按“最可能形态”构造。
     */
    suspend fun devControl(mac: String, opt: String, value: Int) {
        retryOn402 {
            val cred = requireCredentials()
            val body = buildJsonObject {
                put("api", CloudSigner.api())
                put("uid", cred.uid)
                put("token", cred.token)
                put("homeId", cred.homeId)
                put("mac", mac)
                put("opt", opt)
                put("p", value)
                put("datVc", CloudSigner.datVc(cred.token, cred.uid, cred.homeId, mac, opt, value.toString()))
            }
            api.devControl(body)
        }
    }

    private suspend fun <T> retryOn402(block: suspend () -> CloudEnvelope<T>): CloudEnvelope<T> {
        var resp = block()
        if (resp.code == 402) {
            // token 失效：自动重登一次后重放原请求（单次，防死循环）
            credentials = refreshLogin()
            resp = block()
        }
        checkCode(resp)
        return resp
    }

    private fun requireCredentials(): CloudCredentials =
        credentials ?: error("not logged in")

    private fun checkCode(resp: CloudEnvelope<*>) {
        if (resp.code != 200) {
            error("cloud error code=${resp.code} msg=${resp.msg}")
        }
    }

    private fun loginBody(user: String, password: String): JsonObject {
        val api = CloudSigner.api()
        val t = api["t"]!!.toString().trim('"')
        val psw = CloudSigner.psw(password, t)
        return buildJsonObject {
            put("app", "格力+")
            put("appver", "201901178")
            put("devId", "ffffffff-f534-9766-ffff-ffffc2e834d9")
            put("devModel", "MuMu")
            put("api", api)
            put("user", user)
            put("t", t)
            put("psw", psw)
            put("datVc", CloudSigner.datVc(user, psw, t))
        }
    }
}