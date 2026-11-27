package com.gree.remote.ui.theme

/**
 * 用户可自由选择的显示偏好（对齐 Shirone DisplaySettings）。
 * 全部存 DataStore，重启保持；默认值即 Shirone 风格默认（色相 250 / tonalSpot / 2021）。
 */
data class DisplayPrefs(
    /** 主题色相 0–360 */
    val hue: Float = 250f,
    /** 色彩样式 key，见 ThemeEngine.STYLES */
    val colorStyle: String = "tonalSpot",
    /** 颜色规格：false=2021, true=2025 */
    val spec2025: Boolean = false,
    /** 页面背景：none=纯色, banner=首页顶部渐变 banner */
    val wallpaperMode: String = "none",
    /** 布局：list=单列, grid=网格 */
    val layoutMode: String = "list",
    /** 背景纹理预设 */
    val texturePreset: String = "none",
    /** 背景纹理浓度 0f–1f */
    val textureOpacity: Float = 0.5f,
    /** 减少运动 */
    val reduceMotion: Boolean = false,
) {
    companion object {
        const val HUE_DEFAULT = 250f
        const val STYLE_DEFAULT = "tonalSpot"
        const val SPEC_DEFAULT_2025 = false
        const val WALLPAPER_DEFAULT = "none"
        const val LAYOUT_DEFAULT = "list"
        const val TEXTURE_DEFAULT = "none"
        const val TEXTURE_OPACITY_DEFAULT = 0.5f
        const val REDUCE_MOTION_DEFAULT = false

        val TEXTURE_PRESETS = listOf("none", "starlight", "cyberDots", "topography", "geometric", "sakura")
        val TEXTURE_LABELS = listOf("无", "星光", "赛博点阵", "等高线", "几何", "樱花")
    }
}
