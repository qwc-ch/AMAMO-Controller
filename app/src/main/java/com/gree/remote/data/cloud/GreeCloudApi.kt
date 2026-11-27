package com.gree.remote.data.cloud

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 格力云端（国内 grih.gree.com）HTTP 接口，2025-08 起现用协议。
 *
 * 请求体为明文 JSON 字符串，由 [CloudCryptoInterceptor] 统一 AES 加密；
 * 响应 enRes 由拦截器解密成明文 JSON 后再交给 kotlinx-serialization。
 *
 * 已验证端点（HTTP 探针，2026-11-27）：
 *  - App/UserLoginV2                登录 → uid/token
 *  - App/GetHomes                    家庭列表 → home[]
 *  - App/GetDevsInRoomsOfHomeV2      设备列表 → rooms[].devs[]
 */
interface GreeCloudApi {

    @POST("App/UserLoginV2")
    suspend fun login(@Body body: JsonObject): LoginData

    @POST("App/GetHomes")
    suspend fun getHomes(@Body body: JsonObject): HomeData

    @POST("App/GetDevsInRoomsOfHomeV2")
    suspend fun getDevs(@Body body: JsonObject): DevsData

    /** 场景端点保留（未实测，报文形态同前）。 */
    @POST("App/GetScenes")
    suspend fun getScenes(@Body body: JsonObject): CloudEnvelope

    @POST("App/StartOrCancelScene")
    suspend fun startScene(@Body body: JsonObject): CloudEnvelope
}
