package com.gree.remote.data.cloud

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 格力云端客户端：登录、家庭/设备列表、402 自动重登。
 * 新协议（2025-08 起）：请求体由 CloudSigner 签名，CloudCryptoInterceptor 加解密。
 */
class GreeCloudClient(
    private val api: GreeCloudApi,
    /** 402 时自动重登一次。由上层提供（账号密码取自安全存储）。 */
    private val refreshLogin: suspend () -> CloudCredentials,
) {

    private var credentials: CloudCredentials? = null

    /** 已登录凭据。 */
    fun currentCredentials(): CloudCredentials? = credentials

    /** 登录。成功后缓存 uid/token。 */
    suspend fun login(user: String, password: String): CloudCredentials {
        val time = CloudSigner.nowPair()
        val t = time.first
        val psw = CloudSigner.psw(password, t)
        val fields = buildJsonObject {
            put("user", user)
            put("t", t)
            put("psw", psw)
        }
        val body = CloudSigner.requestBody(fields, listOf("user", "psw", "t"), time)
        val resp = api.login(body)
        checkResponse(resp)
        val uid = resp.uidValue ?: error("login response missing uid")
        val cred = CloudCredentials(uidValue = uid, token = resp.token)
        credentials = cred
        return cred
    }

    /**
     * 拉取家庭 + 设备列表。
     * 新接口两步：/App/GetHomes → home[]，再 /App/GetDevsInRoomsOfHomeV2 → rooms[].devs[]。
     */
    suspend fun getDevices(): DevsData {
        val cred = requireCredentials()
        val homes = queryHomes(cred)
        val home = homes.homes.firstOrNull() ?: error("云端账号下没有家庭")
        return queryDevices(cred, home.idValue ?: error("家庭缺少 homeId"))
    }

    private suspend fun queryHomes(cred: CloudCredentials): HomeData {
        val fields = buildJsonObject {
            put("token", cred.token)
            put("uid", cred.uidValue)
        }
        val body = CloudSigner.requestBody(fields, listOf("token", "uid"))
        val resp = api.getHomes(body)
        if (resp.code == 402) {
            credentials = refreshLogin()
            return queryHomes(credentials!!)
        }
        checkResponse(resp)
        return resp
    }

    private suspend fun queryDevices(cred: CloudCredentials, homeId: JsonElement): DevsData {
        val fields = buildJsonObject {
            put("token", cred.token)
            put("homeId", homeId)
            put("uid", cred.uidValue)
        }
        // datVc 字段顺序：token, uid, homeId（与 luc10/gree-api-client 一致）
        val body = CloudSigner.requestBody(fields, listOf("token", "uid", "homeId"))
        val resp = api.getDevs(body)
        if (resp.code == 402) {
            credentials = refreshLogin()
            return queryDevices(credentials!!, homeId)
        }
        checkResponse(resp)
        return resp
    }

    private fun requireCredentials(): CloudCredentials =
        credentials ?: error("not logged in")

    private fun checkResponse(resp: CloudResponse) {
        if (resp.code != 200) {
            error("cloud error code=${resp.code} msg=${resp.msg}")
        }
    }
}

/** 云端登录凭据。uid 用 JsonElement 保真（可能是数字或字符串）。 */
data class CloudCredentials(
    val uidValue: JsonElement,
    val token: String,
    val homeId: String = "",
) {
    val uid: String get() = uidValue.jsonPrimitive.content
}
