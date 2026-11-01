package com.gree.remote.data.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 格力云端（国内 grih.gree.com）HTTP 接口。
 * 端点与常量来自公开逆向实现（xuqiu/hass、luc10/gree-api-client），
 * 最终以你自己抓包为准（技术方案 §8）。
 */
interface GreeCloudApi {

    @POST("App/UserLoginV2")
    suspend fun login(@Body body: JsonObject): CloudEnvelope<LoginData>

    @POST("App/GetDevsOfUserHomes")
    suspend fun getDevs(@Body body: JsonObject): CloudEnvelope<DevsData>

    @POST("App/GetScenes")
    suspend fun getScenes(@Body body: JsonObject): CloudEnvelope<ScenesData>

    @POST("App/StartOrCancelScene")
    suspend fun startScene(@Body body: JsonObject): CloudEnvelope<JsonElement?>

    /**
     * ⚠️ 项目唯一硬风险：遥控控制命令端点。可能不是这个路径/报文。
     * 此处是“最可能形态”的占位，必须用 §8 抓包定案后再改。
     */
    @POST("App/DevControl")
    suspend fun devControl(@Body body: JsonObject): CloudEnvelope<JsonElement?>
}

/** 统一响应信封。r=200 成功；r=402 token 失效需重登。 */
@Serializable
data class CloudEnvelope<T>(
    @SerialName("r") val code: Int = -1,
    @SerialName("m") val msg: String = "",
    @SerialName("dat") val dat: T? = null,
)

@Serializable
data class LoginData(
    @SerialName("uid") val uid: String = "",
    @SerialName("token") val token: String = "",
    @SerialName("homeId") val homeId: String = "",
)

@Serializable
data class DevsData(
    @SerialName("homeId") val homeId: String = "",
    @SerialName("devList") val devList: List<CloudDevice> = emptyList(),
)

@Serializable
data class CloudDevice(
    @SerialName("mac") val mac: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("type") val type: String = "",
    @SerialName("online") val online: Int = 0,
    @SerialName("model") val model: String = "",
)

@Serializable
data class ScenesData(
    @SerialName("sceneList") val sceneList: List<CloudScene> = emptyList(),
)

@Serializable
data class CloudScene(
    @SerialName("sceneId") val sceneId: String = "",
    @SerialName("sceneName") val sceneName: String = "",
)