package com.gree.remote.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 局域网 scan 扫描响应中的设备信息（对应报文的 dat 字段）。
 * 通过 ver/hid 可预判加密代际。
 */
@Serializable
data class ScannedDevice(
    @SerialName("t")
    val type: String = "dev",
    @SerialName("i")
    val i: Int = 0,
    @SerialName("cid")
    val cid: String = "",
    @SerialName("mac")
    val mac: String = "",
    @SerialName("mid")
    val mid: String = "",
    @SerialName("hid")
    val hid: String = "",
    @SerialName("tcid")
    val tcid: String = "",
    @SerialName("name")
    val name: String = "",
    @SerialName("brand")
    val brand: String = "",
    @SerialName("bc")
    val bc: String = "",
    @SerialName("pv")
    val pv: Int = 0,
    @SerialName("sub")
    val sub: Boolean = false,
    @SerialName("ver")
    val ver: String = "",
    @SerialName("uid")
    val uid: Int = 0,
)

/**
 * 归一化后的空调设备，供 UI 层与 Repository 使用。
 */
@Serializable
data class AcDevice(
    val id: String,
    val name: String,
    val mac: String,
    val modelId: String = "",
    /** 局域网模式：已协商出的加密代际；云端模式：可能为 null */
    val cipherVersion: CipherVersion? = null,
    val online: Boolean = true,
    /** 云端家庭 id（云端模式用） */
    val homeId: String = "",
    /** 云端 token（云端模式用） */
    val token: String = "",
)

/** 加密代际：V1 = AES-ECB，V2 = AES-GCM */
enum class CipherVersion {
    V1,
    V2,
}