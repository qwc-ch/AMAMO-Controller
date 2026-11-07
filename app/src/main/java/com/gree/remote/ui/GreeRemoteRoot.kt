package com.gree.remote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gree.remote.AppContainer
import com.gree.remote.ui.home.HomeScreen
import com.gree.remote.ui.home.HomeViewModel
import com.gree.remote.ui.settings.SettingsScreen
import com.gree.remote.ui.settings.SettingsViewModel

private enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("首页", Icons.Filled.Home),
    SETTINGS("设置", Icons.Filled.Settings),
}

/**
 * 应用根：底部导航（首页 / 设置），无登录页——启动直达首页。
 * 两个 tab 的 ViewModel 均为 Activity 作用域，切换不丢状态。
 */
@Composable
fun GreeRemoteRoot(container: AppContainer) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    AppTab.entries.forEachIndexed { index, t ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when (tab) {
                    0 -> {
                        val homeVm: HomeViewModel = viewModel { HomeViewModel(container) }
                        HomeScreen(homeVm, container)
                    }
                    else -> {
                        val settingsVm: SettingsViewModel = viewModel { SettingsViewModel(container) }
                        SettingsScreen(settingsVm)
                    }
                }
            }
        }
    }
}
