package com.gree.remote.data.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.Base64

/**
 * 格力云新协议统一加密/解密拦截器：
 *  - 请求：API 层传明文 JSON（@Body String），这里 AES-ECB 加密 + Base64 后
 *    以 form-urlencoded body 重发，并加 Gaen1/Charset 头；
 *  - 响应：解密 {"enRes":"<base64>"} 为明文 JSON，交给 Retrofit 的序列化 converter；
 *    无 enRes（错误页）则原样透传。
 */
class CloudCryptoInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val body = original.body ?: throw IOException("cloud request has no body")
        val buffer = okio.Buffer()
        body.writeTo(buffer)
        val plain = buffer.readUtf8()
        buffer.close()

        val encrypted = Base64.getEncoder().encodeToString(
            CloudSigner.encrypt(plain.toByteArray(Charsets.UTF_8))
        )
        val req = original.newBuilder()
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Gaen1", CloudSigner.GAE1)
            .header("Charset", "utf-8")
            .method(original.method, encrypted.toByteArray(Charsets.UTF_8).toResponseBody(FORM_TYPE))
            .build()

        val response = chain.proceed(req)
        val raw = response.body?.string() ?: return response

        val decrypted = try {
            val obj = Json.parseToJsonElement(raw).jsonObject
            val enRes = obj["enRes"]?.toString()?.trim('"')
            if (enRes != null) {
                String(CloudSigner.decrypt(Base64.getDecoder().decode(enRes)), Charsets.UTF_8)
            } else raw
        } catch (e: Exception) {
            throw IOException("cloud response decrypt failed: ${e.message}", e)
        }
        return response.newBuilder().body(decrypted.toResponseBody(JSON_TYPE)).build()
    }

    companion object {
        private val FORM_TYPE = "application/x-www-form-urlencoded".toMediaType()
        private val JSON_TYPE = "application/json".toMediaType()
    }
}

/** 响应通用字段接口（各响应数据类均含 code/msg）。 */
interface CloudResponse {
    val code: Int
    val msg: String
}

/** 无业务数据的响应（场景端点）。 */
@Serializable
data class CloudEnvelope(
    @SerialName("r") override val code: Int = -1,
    @SerialName("msg") override val msg: String = "",
) : CloudResponse

@Serializable
data class LoginData(
    @SerialName("r") override val code: Int = -1,
    @SerialName("msg") override val msg: String = "",
    /** uid 可能是数字或字符串，用 JsonElement 保真；登录失败时缺省。 */
    @SerialName("uid") val uidValue: JsonElement? = null,
    @SerialName("token") val token: String = "",
) : CloudResponse

@Serializable
data class HomeData(
    @SerialName("r") override val code: Int = -1,
    @SerialName("msg") override val msg: String = "",
    @SerialName("home") val homes: List<HomeItem> = emptyList(),
) : CloudResponse

@Serializable
data class HomeItem(
    /** 云端 homeId 可能是数字或字符串，用 JsonElement 兼容。 */
    @SerialName("id") val idValue: JsonElement? = null,
) {
    val id: String get() = idValue?.jsonPrimitive?.content ?: ""
}


@Serializable
data class DevsData(
    @SerialName("r") override val code: Int = -1,
    @SerialName("msg") override val msg: String = "",
    @SerialName("rooms") val rooms: List<RoomItem> = emptyList(),
) : CloudResponse

@Serializable
data class RoomItem(
    @SerialName("devs") val devs: List<CloudDevice> = emptyList(),
)

@Serializable
data class CloudDevice(
    @SerialName("mac") val mac: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("type") val type: String = "",
    @SerialName("online") val online: Int = 0,
    @SerialName("model") val model: String = "",
    /** 新接口返回的设备专属 key（可用于局域网控制）。 */
    @SerialName("key") val key: String = "",
)
