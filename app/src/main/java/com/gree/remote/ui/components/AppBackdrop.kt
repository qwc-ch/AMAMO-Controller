package com.gree.remote.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.gree.remote.ui.theme.DisplayPrefs
import com.gree.remote.ui.theme.LocalDisplayPrefs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 全屏背景层（对齐 Shirone 的 wallpaper + texture）：
 * - wallpaperMode=banner：顶部一层淡淡的 primary→transparent 渐变；
 * - texturePreset：用 Canvas 绘制 6 种轻量纹理，浓度由 opacity 控制。
 * 全部使用主题色 token（无硬编码色值），放在内容之下，不影响文字对比。
 */
@Composable
fun AppBackdrop(modifier: Modifier = Modifier) {
    val prefs = LocalDisplayPrefs.current
    val tint = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier.fillMaxSize()) {
        if (prefs.wallpaperMode == "banner") {
            drawBannerTint(tint)
        }
        if (prefs.texturePreset != "none" && prefs.textureOpacity > 0f) {
            drawTexture(prefs.texturePreset, prefs.textureOpacity, onSurface)
        }
    }
}

private fun DrawScope.drawBannerTint(tint: Color) {
    val h = size.height * 0.28f
    val steps = 24
    for (i in 0 until steps) {
        val y = h * i / steps
        val alpha = (1f - i / steps.toFloat()) * 0.10f
        drawLine(
            color = tint.copy(alpha = alpha),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = h / steps + 1f,
        )
    }
}

private fun DrawScope.drawTexture(preset: String, opacity: Float, color: Color) {
    val alpha = opacity * 0.12f // 纹理始终很淡，保证可读性
    val c = color.copy(alpha = alpha)
    when (preset) {
        "starlight" -> {
            val step = 64f
            var x = step / 2
            while (x < size.width) {
                var y = step / 2
                while (y < size.height) {
                    drawCircle(c, radius = 1.5f, center = Offset(x, y))
                    y += step
                }
                x += step
            }
        }
        "cyberDots" -> {
            val step = 32f
            var x = 0f
            while (x < size.width) {
                var y = 0f
                while (y < size.height) {
                    drawCircle(c, radius = 1f, center = Offset(x, y))
                    y += step
                }
                x += step
            }
        }
        "topography" -> {
            for (row in 0..8) {
                val baseY = size.height * row / 8f
                val path = Path().apply {
                    moveTo(0f, baseY)
                    for (x in 0..size.width.toInt() step 40) {
                        lineTo(x.toFloat(), baseY + sin(x / 80f + row) * 16f)
                    }
                }
                drawPath(path, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
            }
        }
        "geometric" -> {
            val step = 120f
            var x = 0f
            while (x < size.width) {
                var y = 0f
                while (y < size.height) {
                    drawLine(c, Offset(x, y), Offset(x + step, y + step), strokeWidth = 1.dp.toPx())
                    drawLine(c, Offset(x + step, y), Offset(x, y + step), strokeWidth = 1.dp.toPx())
                    y += step
                }
                x += step
            }
        }
        "sakura" -> {
            val step = 96f
            var x = step / 2
            while (x < size.width) {
                var y = step / 2
                while (y < size.height) {
                    for (p in 0 until 5) {
                        val angle = p * (Math.PI * 2 / 5)
                        val cx = x + cos(angle).toFloat() * 8f
                        val cy = y + sin(angle).toFloat() * 8f
                        drawCircle(c, radius = 5f, center = Offset(cx, cy))
                    }
                    y += step
                }
                x += step
            }
        }
    }
}
