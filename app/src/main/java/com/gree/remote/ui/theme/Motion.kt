package com.gree.remote.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * 动效语言（对齐 Shirone 的"克制表达"）：
 * - 只在少数 hero 时刻使用带回弹的空间弹簧（温度数字）；
 * - 颜色 / 透明度 / 小位移用不回弹的 effects 弹簧；
 * - 具体动画时长由 Compose 的 MotionDurationScale 控制，系统关闭动画时立即完成。
 */
object ExpressiveMotion {

    /** 空间弹性：温度数字强调等少数 hero 时刻。 */
    val BouncySpatial = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 快速干脆：颜色 / 透明度 / 小位移，不回弹。 */
    val SnappyEffects = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}
