package com.gree.remote.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gree.remote.ui.components.SectionLabel
import com.gree.remote.ui.theme.DisplayPrefs
import com.gree.remote.ui.theme.ThemeEngine
import com.gree.remote.ui.theme.isDarkTheme

/**
 * 显示设置面板（对齐 Shirone DisplaySettings 的全部选项）：
 * 色相滑块 / 色彩样式九宫格 / 颜色规格 / 页面背景 / 布局 / 背景纹理 / 减少运动。
 * 点击即生效，所有值由 ViewModel 落 DataStore，主题入口会重组整套 ColorScheme。
 */
@Composable
fun DisplaySettingsPanel(
    prefs: DisplayPrefs,
    custom: Boolean,
    dirty: Boolean,
    onCustomChange: (Boolean) -> Unit,
    onHueChange: (Float) -> Unit,
    onStyleChange: (String) -> Unit,
    onSpecChange: (Boolean) -> Unit,
    onWallpaperChange: (String) -> Unit,
    onLayoutChange: (String) -> Unit,
    onTextureChange: (String) -> Unit,
    onTextureOpacityChange: (Float) -> Unit,
    onReduceMotionChange: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {

        // 顶部：自定义配色开关 + 重置
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("自定义主题配色", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (custom) "使用下方色相/样式/规格生成配色" else "跟随系统壁纸（Material You）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = custom, onCheckedChange = onCustomChange)
        }
        TextButton(
            onClick = onReset,
            enabled = dirty,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("重置为默认") }

        // 1. 主题配色：色相滑块
        SettingBlock(title = "主题配色") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${prefs.hue.toInt()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
                val primary = ThemeEngine.colorScheme(
                    hue = prefs.hue,
                    isDark = isDarkTheme(),
                    style = prefs.colorStyle,
                    spec2025 = prefs.spec2025,
                ).primary
                Box(
                    Modifier
                        .size(28.dp)
                        .background(primary, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
            }
            Slider(
                value = prefs.hue,
                onValueChange = onHueChange,
                valueRange = 0f..360f,
                steps = 71, // 每 5 度一档
            )
        }

        // 2. 色彩样式：3×3 九宫格
        SettingBlock(title = "色彩样式") {
            val dark = isDarkTheme()
            val columns = ThemeEngine.STYLES.chunked(3)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                columns.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { style ->
                            val index = ThemeEngine.STYLES.indexOf(style)
                            val colors = ThemeEngine.previewColors(prefs.hue, dark, style)
                            StyleCell(
                                label = ThemeEngine.STYLE_LABELS[index],
                                primary = colors.first,
                                secondary = colors.second,
                                tertiary = colors.third,
                                selected = prefs.colorStyle == style,
                                onClick = { onStyleChange(style) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        // 3. 颜色规格
        SettingBlock(title = "颜色规格") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val labels = listOf("2021", "2025")
                labels.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = if (index == 0) !prefs.spec2025 else prefs.spec2025,
                        onClick = { onSpecChange(index == 1) },
                        shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        }

        // 4. 页面背景
        SettingBlock(title = "页面背景") {
            SegmentedChoice(
                options = listOf("none" to "纯色", "banner" to "渐变 Banner"),
                selected = prefs.wallpaperMode,
                onSelect = onWallpaperChange,
            )
        }

        // 5. 布局
        SettingBlock(title = "布局") {
            SegmentedChoice(
                options = listOf("list" to "单列", "grid" to "网格"),
                selected = prefs.layoutMode,
                onSelect = onLayoutChange,
            )
        }

        // 6. 背景纹理（预设 + 浓度）
        SettingBlock(title = "背景纹理") {
            val presets = DisplayPrefs.TEXTURE_PRESETS
            val columns = presets.chunked(3)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                columns.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            val index = presets.indexOf(preset)
                            TextureCell(
                                label = DisplayPrefs.TEXTURE_LABELS[index],
                                selected = prefs.texturePreset == preset,
                                onClick = { onTextureChange(preset) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("浓度", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    "${(prefs.textureOpacity * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = prefs.textureOpacity,
                onValueChange = onTextureOpacityChange,
                valueRange = 0f..1f,
            )
        }

        // 7. 减少运动
        SettingBlock(title = "减少运动") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("关闭温度弹跳动效", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = prefs.reduceMotion, onCheckedChange = onReduceMotionChange)
            }
        }
    }
}

@Composable
private fun SettingBlock(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(title)
        content()
    }
}

@Composable
private fun SegmentedChoice(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}

@Composable
private fun StyleCell(
    label: String,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .heightIn(min = 72.dp)
            .background(container, RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(12.dp),
            )
            .clickable(role = Role.RadioButton) { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(primary, secondary, tertiary).forEach { c ->
                Box(
                    Modifier
                        .size(12.dp)
                        .background(c, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TextureCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .background(container, RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(12.dp),
            )
            .clickable(role = Role.RadioButton) { onClick() }
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor, textAlign = TextAlign.Center)
    }
}
