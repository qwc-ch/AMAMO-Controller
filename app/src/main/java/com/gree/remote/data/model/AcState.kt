package com.gree.remote.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 空调状态快照。对应局域网 status 响应（dat 列）与云端下发字段。
 * 所有字段有默认值，解析时可容忍缺列。
 */
@Serializable
data class AcState(
    @SerialName("Pow") val power: Int = 0,
    @SerialName("Mod") val mode: Int = 0,             // 0 自动 1 制冷 2 除湿 3 送风 4 制热
    @SerialName("SetTem") val setTemp: Int = 26,      // 设定温度（十六进制，需解码）
    @SerialName("TemSen") val roomTemp: Int = 0,      // 室温（十六进制，部分固件上报）
    @SerialName("WdSpd") val windSpeed: Int = 0,      // 0 自动 ~ 5 高
    @SerialName("SwUpDn") val swingUpDown: Int = 0,   // 上下扫风
    @SerialName("SwingLfRig") val swingLeftRight: Int = 0, // 左右扫风
    @SerialName("Lig") val light: Int = 0,            // 指示灯
    @SerialName("Tur") val turbo: Int = 0,            // 强劲
    @SerialName("Quiet") val quiet: Int = 0,          // 静音
    @SerialName("Blo") val blowDry: Int = 0,          // 干燥防霉
    @SerialName("Health") val health: Int = 0,        // 负离子
    @SerialName("SvSt") val saveEnergy: Int = 0,      // 节能
    @SerialName("StHt") val heatAux: Int = 0,         // 电辅热
    @SerialName("TemUn") val tempUnit: Int = 0,       // 温度单位
    @SerialName("Air") val air: Int = 0,              // 新风
    @SerialName("SwhSlp") val sleep: Int = 0,         // 睡眠
    @SerialName("HeatCoolType") val heatCoolType: Int = 0,
    @SerialName("TemRec") val temRec: Int = 0,        // 余温回收
) {
    /** 设定温度（摄氏）：设备端 SetTem 的数值即摄氏温度（16..30），无需换算。 */
    fun decodedSetTemp(): Int = setTemp

    /** 室温（摄氏）：部分固件上报 TemSen 数值即摄氏温度；0 表示未上报。 */
    fun decodedRoomTemp(): Int = roomTemp
}