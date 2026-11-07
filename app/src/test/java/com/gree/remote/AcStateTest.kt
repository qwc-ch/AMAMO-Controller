package com.gree.remote

import com.gree.remote.data.model.AcState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * AcState 解码规则单测：
 * - TemSen 室温 = 实际摄氏 + 40（gree-remote 协议偏移），<=40 视为未上报；
 * - SetTem 即摄氏整数，TemRec=1 表示 +0.5℃。
 */
class AcStateTest {

    @Test
    fun `room temp subtracts the protocol offset of 40`() {
        // 扫描实测：设备上报 65 → 实际 25℃
        assertEquals(25, AcState(roomTemp = 65).decodedRoomTemp())
        assertEquals(20, AcState(roomTemp = 60).decodedRoomTemp())
        assertEquals(31, AcState(roomTemp = 71).decodedRoomTemp())
    }

    @Test
    fun `room temp raw 0 or below offset is treated as not reported`() {
        // 0 = 未上报；1..40 视为无效原始值（换算后 <=0℃ 不可信）
        assertNull(AcState(roomTemp = 0).decodedRoomTemp())
        assertNull(AcState(roomTemp = 40).decodedRoomTemp())
        assertNull(AcState(roomTemp = 10).decodedRoomTemp())
    }

    @Test
    fun `room temp raw 41 is 1 degree celsius`() {
        assertEquals(1, AcState(roomTemp = 41).decodedRoomTemp())
    }

    @Test
    fun `set temp is the raw celsius value when TemRec off`() {
        assertEquals(26.0, AcState(setTemp = 26).decodedSetTemp(), 0.001)
        assertEquals(16.0, AcState(setTemp = 16).decodedSetTemp(), 0.001)
        assertEquals(30.0, AcState(setTemp = 30).decodedSetTemp(), 0.001)
    }

    @Test
    fun `set temp adds half degree when TemRec on`() {
        assertEquals(26.5, AcState(setTemp = 26, temRec = 1).decodedSetTemp(), 0.001)
        assertEquals(16.5, AcState(setTemp = 16, temRec = 1).decodedSetTemp(), 0.001)
    }

    @Test
    fun `half degree support flag follows TemRec bit`() {
        assertEquals(false, AcState(temRec = 0).supportsHalfDegree())
        assertEquals(true, AcState(temRec = 1).supportsHalfDegree())
    }
}
