package com.gree.remote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 主题入口（Material 3）。
 * 主色跟随系统 Material You dynamic color；
 * 关闭动态取色时回退到标准明/暗配色。
 */
@Composable
fun GreeRemoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> if (darkTheme) darkColorScheme() else lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}