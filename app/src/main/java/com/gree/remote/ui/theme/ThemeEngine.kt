package com.gree.remote.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFidelity
import com.materialkolor.scheme.SchemeFruitSalad
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant

/**
 * 主题引擎：与 Shirone 同源的 HCT 动态配色。
 *
 * 用户自由选择：色相（0–360）、9 种色彩样式、颜色规格（2021/2025）。
 * 色相经固定 chroma/tone 生成 seed，再由 Material Color Utilities 的
 * 9 种 Scheme 变体生成完整 HCT 色阶，最后映射为 Compose 的 M3 ColorScheme。
 *
 * 2021/2025 两规格的差别：MCU 1.7.1 的 Scheme 类按 2021 规范生成调色板；
 * 2025 规格对 container/on-container 的 tone 做了调整，这里在取色阶段
 * 用不同的 tone 覆盖（见 [role] 的 spec 分支），保持纯 HCT、无硬编码色值。
 */
object ThemeEngine {

    /** 9 种色彩样式，与 Shirone 的 MC_STYLES 顺序一致。 */
    val STYLES = listOf(
        "tonalSpot", "vibrant", "content", "expressive", "rainbow",
        "fruitSalad", "monochrome", "neutral", "fidelity",
    )

    val STYLE_LABELS = listOf(
        "柔和", "鲜艳", "内容", "表现", "彩虹", "果沙拉", "单色", "中性", "保真",
    )

    private const val SEED_CHROMA = 60.0
    private const val SEED_TONE = 50.0

    /** 由色相生成 seed（固定中等彩度与明度），与 Shirone seedFromHue 一致。 */
    fun seedFromHue(hue: Float): Int =
        Hct.from(hue.toDouble(), SEED_CHROMA, SEED_TONE).toInt()

    /** seed 对应的 ARGB，用于九宫格预览。 */
    fun seedColor(hue: Float): Color = Color(seedFromHue(hue))

    /** 构造 HCT scheme（2021 规范；2025 的微调在 [role] 阶段处理）。 */
    private fun scheme(hue: Float, isDark: Boolean, style: String): DynamicScheme {
        val source = Hct.fromInt(seedFromHue(hue))
        val contrast = 0.0
        return when (style) {
            "vibrant" -> SchemeVibrant(source, isDark, contrast)
            "content" -> SchemeContent(source, isDark, contrast)
            "expressive" -> SchemeExpressive(source, isDark, contrast)
            "rainbow" -> SchemeRainbow(source, isDark, contrast)
            "fruitSalad" -> SchemeFruitSalad(source, isDark, contrast)
            "monochrome" -> SchemeMonochrome(source, isDark, contrast)
            "neutral" -> SchemeNeutral(source, isDark, contrast)
            "fidelity" -> SchemeFidelity(source, isDark, contrast)
            else -> SchemeTonalSpot(source, isDark, contrast)
        }
    }

    /** 从 scheme 的主/次/三级调色板取 tone，得到预览色。 */
    fun previewColors(hue: Float, isDark: Boolean, style: String): Triple<Color, Color, Color> {
        val s = scheme(hue, isDark, style)
        return Triple(
            Color(s.primaryPalette.tone(if (isDark) 80 else 40)),
            Color(s.secondaryPalette.tone(if (isDark) 80 else 40)),
            Color(s.tertiaryPalette.tone(if (isDark) 80 else 40)),
        )
    }

    /**
     * 生成完整 ColorScheme。使用 MaterialDynamicColors 的官方角色映射。
     * spec=2025 时对容器色做一次轻微 tone 偏移，贴近 M3E 2025 规范；
     * spec=2021 则原样使用 MCU Scheme 的 2021 输出。
     */
    fun colorScheme(hue: Float, isDark: Boolean, style: String, spec2025: Boolean): ColorScheme {
        val s = scheme(hue, isDark, style)
        val mdc = MaterialDynamicColors()

        fun role(argbOf: (MaterialDynamicColors) -> com.materialkolor.dynamiccolor.DynamicColor): Color =
            Color(argbOf(mdc).getArgb(s))

        // 2025 规范：容器色 tone 略降/升，增强层次；on-color 保持高对比。
        fun container(palette: com.materialkolor.palettes.TonalPalette, tone2021: Int, tone2025: Int): Color =
            Color(palette.tone(if (spec2025) tone2025 else tone2021))

        return if (isDark) {
            darkColorScheme(
                primary = role { it.primary() },
                onPrimary = role { it.onPrimary() },
                primaryContainer = container(s.primaryPalette, 30, 28),
                onPrimaryContainer = role { it.onPrimaryContainer() },
                inversePrimary = role { it.inversePrimary() },
                secondary = role { it.secondary() },
                onSecondary = role { it.onSecondary() },
                secondaryContainer = container(s.secondaryPalette, 30, 28),
                onSecondaryContainer = role { it.onSecondaryContainer() },
                tertiary = role { it.tertiary() },
                onTertiary = role { it.onTertiary() },
                tertiaryContainer = container(s.tertiaryPalette, 30, 28),
                onTertiaryContainer = role { it.onTertiaryContainer() },
                background = role { it.background() },
                onBackground = role { it.onBackground() },
                surface = role { it.surface() },
                onSurface = role { it.onSurface() },
                surfaceVariant = role { it.surfaceVariant() },
                onSurfaceVariant = role { it.onSurfaceVariant() },
                outline = role { it.outline() },
                outlineVariant = role { it.outlineVariant() },
                scrim = role { it.scrim() },
                inverseSurface = role { it.inverseSurface() },
                inverseOnSurface = role { it.inverseOnSurface() },
                error = role { it.error() },
                onError = role { it.onError() },
                errorContainer = role { it.errorContainer() },
                onErrorContainer = role { it.onErrorContainer() },
                surfaceBright = role { it.surfaceBright() },
                surfaceDim = role { it.surfaceDim() },
                surfaceContainerLowest = role { it.surfaceContainerLowest() },
                surfaceContainerLow = role { it.surfaceContainerLow() },
                surfaceContainer = role { it.surfaceContainer() },
                surfaceContainerHigh = role { it.surfaceContainerHigh() },
                surfaceContainerHighest = role { it.surfaceContainerHighest() },
            )
        } else {
            lightColorScheme(
                primary = role { it.primary() },
                onPrimary = role { it.onPrimary() },
                primaryContainer = container(s.primaryPalette, 90, 92),
                onPrimaryContainer = role { it.onPrimaryContainer() },
                inversePrimary = role { it.inversePrimary() },
                secondary = role { it.secondary() },
                onSecondary = role { it.onSecondary() },
                secondaryContainer = container(s.secondaryPalette, 90, 92),
                onSecondaryContainer = role { it.onSecondaryContainer() },
                tertiary = role { it.tertiary() },
                onTertiary = role { it.onTertiary() },
                tertiaryContainer = container(s.tertiaryPalette, 90, 92),
                onTertiaryContainer = role { it.onTertiaryContainer() },
                background = role { it.background() },
                onBackground = role { it.onBackground() },
                surface = role { it.surface() },
                onSurface = role { it.onSurface() },
                surfaceVariant = role { it.surfaceVariant() },
                onSurfaceVariant = role { it.onSurfaceVariant() },
                outline = role { it.outline() },
                outlineVariant = role { it.outlineVariant() },
                scrim = role { it.scrim() },
                inverseSurface = role { it.inverseSurface() },
                inverseOnSurface = role { it.inverseOnSurface() },
                error = role { it.error() },
                onError = role { it.onError() },
                errorContainer = role { it.errorContainer() },
                onErrorContainer = role { it.onErrorContainer() },
                surfaceBright = role { it.surfaceBright() },
                surfaceDim = role { it.surfaceDim() },
                surfaceContainerLowest = role { it.surfaceContainerLowest() },
                surfaceContainerLow = role { it.surfaceContainerLow() },
                surfaceContainer = role { it.surfaceContainer() },
                surfaceContainerHigh = role { it.surfaceContainerHigh() },
                surfaceContainerHighest = role { it.surfaceContainerHighest() },
            )
        }
    }
}
