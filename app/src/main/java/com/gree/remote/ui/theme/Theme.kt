package com.gree.remote.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 主题入口（Material 3 + MD3E 设计语言）。
 * - API 31+ 默认跟随系统 Material You 动态取色；
 * - 其余情况回退到品牌蓝紫 seed 的表达力明/暗配色（见 [ExpressiveLightColors] / [ExpressiveDarkColors]）。
 * - 形状（Shape.kt）/ 字阶（Type.kt）/ 弹性动效（Motion.kt）不随动态色变化，保证 MD3E 手感。
 */
@Composable
fun GreeRemoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> ExpressiveDarkColors
        else -> ExpressiveLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** 品牌回退明色方案（seed #4D6BFE，蓝紫）。 */
private val ExpressiveLightColors = lightColorScheme(
    primary = BrandBlue40,
    onPrimary = BrandOnPrimary,
    primaryContainer = BrandBlue90,
    onPrimaryContainer = BrandBlue10,
    inversePrimary = BrandBlue80,
    secondary = BrandSlate40,
    onSecondary = BrandOnSecondary,
    secondaryContainer = BrandSlate90,
    onSecondaryContainer = BrandSlate10,
    tertiary = BrandTeal40,
    onTertiary = BrandOnTertiary,
    tertiaryContainer = BrandTeal90,
    onTertiaryContainer = BrandTeal10,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant80,
    scrim = Scrim,
    inverseSurface = Neutral20,
    inverseOnSurface = Neutral95,
    error = Error40,
    onError = Error100,
    errorContainer = Error90,
    onErrorContainer = Error10,
    surfaceBright = Neutral98,
    surfaceDim = Neutral90,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
)

/** 品牌回退暗色方案（与明色同一 seed）。 */
private val ExpressiveDarkColors = darkColorScheme(
    primary = BrandBlue80,
    onPrimary = BrandBlue20,
    primaryContainer = BrandBlue30,
    onPrimaryContainer = BrandBlue90,
    inversePrimary = BrandBlue40,
    secondary = BrandSlate80,
    onSecondary = BrandSlate20,
    secondaryContainer = BrandSlate30,
    onSecondaryContainer = BrandSlate90,
    tertiary = BrandTeal80,
    onTertiary = BrandTeal20,
    tertiaryContainer = BrandTeal30,
    onTertiaryContainer = BrandTeal90,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,
    outline = NeutralVariant60,
    outlineVariant = NeutralVariant30,
    scrim = Scrim,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral20,
    error = Error80,
    onError = Error20,
    errorContainer = Error30,
    onErrorContainer = Error90,
    surfaceBright = Neutral24,
    surfaceDim = Neutral6,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
)
