package com.gree.remote.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
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
        ) {
            when (tab) {
                0 -> GeneralTab(s, vm::setDynamicColor)
                1 -> CloudAccountTab(s, vm)
                else -> AboutTab()
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
private fun GeneralTab(s: SettingsUiState, onDynamicColor: (Boolean) -> Unit) {
    SettingCard(title = "外观") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("动态取色（Material You）", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        "跟随系统壁纸取色；关闭后使用品牌蓝紫配色"
                    else "需要 Android 12 及以上",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = s.dynamicColor,
                onCheckedChange = onDynamicColor,
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "深浅色跟随系统设置，暂不提供单独开关。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { vm.showLogin(true) }) { Text("登录格力云") }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("已登录", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "账号信息已加密保存在本机",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = vm::logout) { Text("登出") }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("云端设备", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (s.cloudLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = vm::loadCloudDevices) { Text("刷新") }
                }
            }
            s.cloudError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (s.cloudDevices.isEmpty() && s.cloudError == null) {
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
                        .padding(vertical = 6.dp),
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
                }
            }
            Spacer(Modifier.height(4.dp))
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
        Text("版本 0.1.0", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "格力空调局域网遥控（UDP 直连，scan → bind → status/cmd，双代际加密自动协商），" +
                "可选格力云同步设备列表。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text("参考实现与致谢：", style = MaterialTheme.typography.titleSmall)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
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
                TextButton(onClick = { onLogin(user, pwd) }) { Text("登录") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        },
    )
}
