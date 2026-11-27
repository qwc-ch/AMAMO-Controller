package com.gree.remote.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gree.remote.AppContainer
import com.gree.remote.ui.components.EditorialPage
import com.gree.remote.ui.components.StateCard
import com.gree.remote.ui.remote.RemotePanel
import com.gree.remote.ui.remote.RemoteViewModel
import com.gree.remote.ui.theme.PillShape

/**
 * 首页（应用启动页，登录页已移除）：
 * - 顶部：标题 + 局域网扫描；
 * - 每台发现的设备一个分页（TabRow），分页内嵌遥控面板；
 * - 设置（含云端账号）在底部导航的"设置"页。
 */
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    container: AppContainer,
) {
    val s by vm.state.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { vm.scanLan() }
    LaunchedEffect(s.devices.size) {
        if (selectedTab >= s.devices.size) selectedTab = 0
    }

    EditorialPage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()),
        ) {
            Spacer(Modifier.height(20.dp))

            // ---- Hero 标题 + 扫描 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AMAMO", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "格力空调 · 局域网直连",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = vm::scanLan,
                    enabled = !s.scanning,
                    shape = PillShape,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(if (s.devices.isEmpty()) "扫描" else "重新扫描")
                }
            }

            Spacer(Modifier.height(20.dp))

            // 扫描状态行
            when {
                s.scanning -> {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                s.error != null && s.devices.isEmpty() -> {
                    Text(
                        s.error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (s.devices.isEmpty()) {
                if (!s.scanning) {
                    EmptyDevices(onScan = vm::scanLan)
                }
            } else {
                // ---- 设备分页 ----
                TabRow(selectedTabIndex = selectedTab.coerceAtMost(s.devices.size - 1)) {
                    s.devices.forEachIndexed { index, dev ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(dev.name, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                            },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ---- 当前设备的遥控面板（每设备独立 ViewModel） ----
                val dev = s.devices.getOrNull(selectedTab.coerceAtMost(s.devices.size - 1))
                if (dev != null) {
                    key(dev.mac) {
                        val panelVm: RemoteViewModel = viewModel(
                            key = "remote_${dev.mac}",
                        ) {
                            RemoteViewModel(
                                container = container,
                                targetMac = dev.mac,
                                targetIp = dev.ip,
                                firmwareVer = dev.firmware,
                            )
                        }
                        DisposableEffect(dev.mac) {
                            panelVm.setActive(true)
                            onDispose { panelVm.setActive(false) }
                        }
                        RemotePanel(panelVm, dev.name, dev.ip)
                    }
                }
            }
        }
    }
}

/** 无设备空态：引导扫描（编辑式状态卡）。 */
@Composable
private fun EmptyDevices(onScan: () -> Unit) {
    StateCard(
        icon = Icons.Filled.Refresh,
        title = "还没发现空调",
        description = "确保手机与空调连接同一个 WiFi，\n然后点击下方按钮扫描局域网。",
        modifier = Modifier.padding(top = 32.dp),
    ) {
        Button(
            onClick = onScan,
            shape = PillShape,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text("扫描局域网")
        }
    }
}
