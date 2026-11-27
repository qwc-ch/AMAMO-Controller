package com.gree.remote.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 形状语言（对齐 Shirone 的 MD3 形状契约）：
 * - 12dp 是核心控件形状（按钮 / 输入框 / 分段按钮）；
 * - 16dp 是核心容器形状（卡片 / widget）；
 * - 28dp 留给 dialog / modal sheet；
 * - pill（50）只用于 chips、状态徽标、主按钮等需要强调的控件。
 * 不在同一层级混用任意圆角，保证层级可读。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** 全圆（pill）：chips / 状态徽标 / 主按钮。 */
val PillShape = RoundedCornerShape(50)
