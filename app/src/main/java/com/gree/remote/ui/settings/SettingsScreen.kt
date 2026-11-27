package com.gree.remote.ui.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gree.remote.ui.components.EditorialCard
import com.gree.remote.ui.components.EditorialPage
import com.gree.remote.ui.components.SectionLabel
import com.gree.remote.ui.components.StatusBadge
import com.gree.remote.ui.components.StatusTone
import com.gree.remote.ui.theme.PillShape

private val settingsTabs = listOf("通用", "云端账号", "关于")

/**
 * 设置页：内部三个子分页（TabRow）。
 * - 通用：动态取色（Material You）；
 * - 云端账号：登录/登出、云端设备列表；
 * - 关于：版本与参考实现。
 */
@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val s by vm.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    EditorialPage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()),
        ) {
            Spacer(Modifier.height(8.dp))
            TabRow(
                selectedTabIndex = tab,
                modifier = Modifier.fillMaxWidth(),
            ) {
                settingsTabs.forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(label) },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (tab) {
                    0 -> GeneralTab(s, vm)
                    1 -> CloudAccountTab(s, vm)
                    else -> AboutTab()
                }
            }
        }
    }

    if (s.showLogin) {
        LoginDialog(
            busy = s.loginBusy,
            error = s.loginError,
            onLogin = vm::login,
            onDismiss = { vm.showLogin(false) },
        )
    }
}

// ---- 通用 ----

@Composable
private fun GeneralTab(s: SettingsUiState, vm: SettingsViewModel) {
    SettingCard(title = "显示设置") {
        DisplaySettingsPanel(
            prefs = s.displayPrefs,
            custom = s.displayCustom,
            dirty = vm.isDisplayDirty(),
            onCustomChange = vm::setDisplayCustom,
            onHueChange = vm::setDisplayHue,
            onStyleChange = vm::setDisplayStyle,
            onSpecChange = vm::setDisplaySpec2025,
            onWallpaperChange = vm::setDisplayWallpaper,
            onLayoutChange = vm::setDisplayLayout,
            onTextureChange = vm::setDisplayTexture,
            onTextureOpacityChange = vm::setDisplayTextureOpacity,
            onReduceMotionChange = vm::setDisplayReduceMotion,
            onReset = vm::resetDisplayPrefs,
        )
    }
}

// ---- 云端账号 ----

@Composable
private fun CloudAccountTab(s: SettingsUiState, vm: SettingsViewModel) {
    SettingCard(title = "格力云账号") {
        if (!s.cloudLoggedIn) {
            Text(
                "未登录。局域网直连无需账号；登录仅用于同步云端设备列表。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = { vm.showLogin(true) },
                shape = PillShape,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("登录格力云") }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("已登录", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "账号信息已加密保存在本机",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge("已登录", tone = StatusTone.Success)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = vm::logout) { Text("登出") }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("云端设备", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (s.cloudLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = vm::loadCloudDevices) { Text("刷新") }
                }
            }
            s.cloudError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (s.cloudDevices.isEmpty() && s.cloudError == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "暂无设备，点“刷新”拉取。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            s.cloudDevices.forEach { dev ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(dev.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (dev.online) "在线 · ${dev.mac}" else "离线 · ${dev.mac}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusBadge(
                        if (dev.online) "在线" else "离线",
                        tone = if (dev.online) StatusTone.Success else StatusTone.Neutral,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "云端控制端点待定案，控制请用局域网直连。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- 关于 ----

@Composable
private fun AboutTab() {
    SettingCard(title = "AMAMO Controller") {
        Text("版本 0.1.0", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "格力空调局域网遥控（UDP 直连，scan → bind → status/cmd，双代际加密自动协商），" +
                "可选格力云同步设备列表。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        SectionLabel("参考实现与致谢")
        Spacer(Modifier.height(8.dp))
        listOf(
            "tomikaa87/gree-remote — 协议报文与 AES-ECB",
            "inwaar/gree-hvac-client — AES-GCM 适配",
            "xuqiu/hass、luc10/gree-api-client — 云端接口",
        ).forEach {
            Text("· $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 设置分组卡。 */
@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    EditorialCard {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/** 云端登录弹窗。 */
@Composable
private fun LoginDialog(
    busy: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var user by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("登录格力云", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("账号") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pwd,
                    onValueChange = { pwd = it },
                    label = { Text("密码") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Button(
                    onClick = { onLogin(user, pwd) },
                    shape = PillShape,
                ) { Text("登录") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        },
    )
}
