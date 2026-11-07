package com.gree.remote.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 品牌配色（seed #4D6BFE，HCT tonal 生成，与启动图标同源）。
 * 仅在动态取色不可用（API < 31 或关闭）时作为回退方案。
 * 明/暗两套 48 角色完整定义见 Theme.kt。
 */

// ---- 蓝紫 primary（品牌主色）----
val BrandBlue10 = Color(0xFF001159)
val BrandBlue20 = Color(0xFF00208E)
val BrandBlue30 = Color(0xFF0031C6)
val BrandBlue40 = Color(0xFF2B4DE1)
val BrandBlue80 = Color(0xFFBAC3FF)
val BrandBlue90 = Color(0xFFDEE1FF)

// ---- 中性蓝灰 secondary ----
val BrandSlate10 = Color(0xFF171A2C)
val BrandSlate20 = Color(0xFF2C2F42)
val BrandSlate30 = Color(0xFF434659)
val BrandSlate40 = Color(0xFF5A5D72)
val BrandSlate80 = Color(0xFFC3C5DD)
val BrandSlate90 = Color(0xFFDFE1F9)

// ---- 粉紫 tertiary（点缀强调）----
val Accent10 = Color(0xFF2D1228)
val Accent20 = Color(0xFF44263E)
val Accent30 = Color(0xFF5D3C55)
val Accent40 = Color(0xFF76546D)
val Accent80 = Color(0xFFE5BAD8)
val Accent90 = Color(0xFFFFD7F2)

// ---- Neutral（表面层）----
val Neutral6 = Color(0xFF131316)
val Neutral10 = Color(0xFF1B1B1F)
val Neutral20 = Color(0xFF303034)
val Neutral24 = Color(0xFF39393C)
val Neutral90 = Color(0xFFE4E1E6)
val Neutral95 = Color(0xFFF3F0F4)
val Neutral98 = Color(0xFFFBF8FD)
val Neutral99 = Color(0xFFFEFBFF)

val NeutralVariant30 = Color(0xFF46464F)
val NeutralVariant50 = Color(0xFF767680)
val NeutralVariant60 = Color(0xFF90909A)
val NeutralVariant80 = Color(0xFFC6C5D0)
val NeutralVariant90 = Color(0xFFE3E1EC)

// Surface container 层级（light）
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF6F2F7)
val SurfaceContainerLight = Color(0xFFF0EDF1)
val SurfaceContainerHighLight = Color(0xFFEAE7EC)
val SurfaceContainerHighestLight = Color(0xFFE4E1E6)

// Surface container 层级（dark）
val SurfaceContainerLowestDark = Color(0xFF0E0E11)
val SurfaceContainerLowDark = Color(0xFF1B1B1F)
val SurfaceContainerDark = Color(0xFF1F1F23)
val SurfaceContainerHighDark = Color(0xFF2A2A2D)
val SurfaceContainerHighestDark = Color(0xFF353438)

// ---- Error ----
val Error10 = Color(0xFF410002)
val Error20 = Color(0xFF690005)
val Error30 = Color(0xFF93000A)
val Error40 = Color(0xFFBA1A1A)
val Error80 = Color(0xFFFFB4AB)
val Error90 = Color(0xFFFFDAD6)
val Error100 = Color(0xFFFFFFFF)

val Scrim = Color(0xFF000000)

/** 兼容旧引用名（主题内 tertiary 别名）。 */
val BrandTeal10 = Accent10
val BrandTeal20 = Accent20
val BrandTeal30 = Accent30
val BrandTeal40 = Accent40
val BrandTeal80 = Accent80
val BrandTeal90 = Accent90
val BrandOnPrimary = Color(0xFFFFFFFF)
val BrandOnSecondary = Color(0xFFFFFFFF)
val BrandOnTertiary = Color(0xFFFFFFFF)
