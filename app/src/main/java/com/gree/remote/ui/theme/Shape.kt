package com.gree.remote.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 表达力形状阶梯（稳定线 5 档 + 组件级全圆 pill 由组件直接使用）。
 * MD3E 思路：容器大圆角 + 按钮 pill 混用，制造形状张力。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** MD3E 标志性全圆（pill）形状：主按钮 / FAB / chips 用。 */
val PillShape = RoundedCornerShape(50)
