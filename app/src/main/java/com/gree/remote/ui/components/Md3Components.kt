package com.gree.remote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** 内容最大宽度：对齐 Shirone 的大屏约束（840–1040dp），避免文字/控件无限拉伸。 */
val ContentMaxWidth = 960.dp

/**
 * 自适应应用骨架：
 * - 窄屏（compact width 或 compact height）用底部 NavigationBar；
 * - 宽屏用左侧 NavigationRail；
 * - 两者共用同一组 destinations 与选中状态，窗口变化不丢状态。
 */
@Composable
fun AdaptiveAppScaffold(
    windowSizeClass: WindowSizeClass,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    destinations: List<AppDestination>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val useRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact &&
        windowSizeClass.heightSizeClass != WindowHeightSizeClass.Compact

    Row(modifier = modifier.fillMaxSize()) {
        if (useRail) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxHeight(),
            ) {
                Spacer(Modifier.height(24.dp))
                destinations.forEachIndexed { index, dest ->
                    NavigationRailItem(
                        selected = selectedTab == index,
                        onClick = { onTabSelected(index) },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AppBackdrop(modifier = Modifier.fillMaxSize())
                content()
            }
            if (!useRail) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    destinations.forEachIndexed { index, dest ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { onTabSelected(index) },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        }
    }
}

data class AppDestination(val label: String, val icon: ImageVector)

val AppDestinations = listOf(
    AppDestination("首页", Icons.Filled.Home),
    AppDestination("设置", Icons.Filled.Settings),
)

/**
 * 编辑式页面容器：为内容套上最大宽度与统一横向留白。
 * 紧凑屏 16dp，宽屏 24dp（Shirone 的 8dp 网格）。
 */
@Composable
fun EditorialPage(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = ContentMaxWidth)
                .padding(horizontal = 16.dp),
        ) {
            content()
        }
    }
}

/** 分区标题：用 titleSmall + onSurfaceVariant，保持编辑感与可扫描性。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * 编辑式卡片：light 用 Surface Container Lowest，dark 用 High（与 Shirone 一致）。
 * 不添加阴影 / 描边，深度完全由 tonal surface 表达。
 */
@Composable
fun EditorialCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // 统一使用 surfaceContainerLow：light 下略高于页面底、dark 下略高于 surface，
    // 与 Shirone 的 tonal surface 层级一致，且不依赖硬编码明暗判断。
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}

/** 状态徽标：pill + container 色，颜色之外还有文字，不依赖 color-only。 */
@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Neutral,
) {
    val (container, content) = when (tone) {
        StatusTone.Success -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        StatusTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        StatusTone.Neutral -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

enum class StatusTone { Neutral, Success, Error }

/** 带图标 + 标题 + 副标题的状态空态卡片。 */
@Composable
fun StateCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    EditorialCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) {
                Spacer(Modifier.height(20.dp))
                action()
            }
        }
    }
}
