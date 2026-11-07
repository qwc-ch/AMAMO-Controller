package com.gree.remote.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * 表达力动效规格（稳定线无 MotionScheme，用 foundation spring 等效实现 MD3E 弹性手感）：
 * - [BouncySpatial]：入场 / 元素位移，带回弹（MD3E expressive spatial）；
 * - [SnappyEffects]：状态切换 / 颜色，快而不弹（MD3E fast effects）。
 * MD3E 正式 API 见 material3 1.5.0 线的 MotionScheme.expressive()。
 */
object ExpressiveMotion {

    /** 空间弹性：结果入场、温度数字强调等。 */
    val BouncySpatial = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy, // 0.5
        stiffness = Spring.StiffnessMediumLow,          // 400
    )

    /** 快速干脆：颜色/透明度/小位移。 */
    val SnappyEffects = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,     // 1.0
        stiffness = Spring.StiffnessMedium,             // 1500
    )
}
