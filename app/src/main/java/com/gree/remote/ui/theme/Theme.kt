package com.gree.remote.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * 是否使用用户自定义 HCT 配色（Shirone 风格）。由 Theme 提供给整棵树，
 * 主题入口据此决定用 ThemeEngine 生成还是跟随系统 Material You。
 */
val LocalDisplayPrefs = staticCompositionLocalOf { DisplayPrefs() }

/** 当前是否暗色主题，供非 Compose 主题上下文（如色卡预览）读取。 */
@Composable
fun isDarkTheme(): Boolean = isSystemInDarkTheme()

/**
 * 主题入口：
 * - displayCustom=true：用用户选的色相/样式/spec 经 HCT 引擎生成 ColorScheme（Shirone 同源）；
 * - displayCustom=false 且 API 31+：跟随系统 Material You 动态取色；
 * - 其余：回落到 ThemeEngine 默认色相（250 / tonalSpot / 2021）。
 *
 * 形状/字阶/动效 token 不随配色变化。减少运动偏好通过 LocalDisplayPrefs 下发，
 * 各动画点读取它决定是否弹跳。
 */
@Composable
fun GreeRemoteTheme(
    displayPrefs: DisplayPrefs = DisplayPrefs(),
    displayCustom: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useSystemDynamic = !displayCustom && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        useSystemDynamic ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> ThemeEngine.colorScheme(
            hue = displayPrefs.hue,
            isDark = darkTheme,
            style = displayPrefs.colorStyle,
            spec2025 = displayPrefs.spec2025,
        )
    }

    CompositionLocalProvider(LocalDisplayPrefs provides displayPrefs) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
