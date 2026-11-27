package com.gree.remote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gree.remote.AppContainer
import com.gree.remote.ui.components.AdaptiveAppScaffold
import com.gree.remote.ui.components.AppDestinations
import com.gree.remote.ui.home.HomeScreen
import com.gree.remote.ui.home.HomeViewModel
import com.gree.remote.ui.settings.SettingsScreen
import com.gree.remote.ui.settings.SettingsViewModel

/**
 * 应用根：自适应导航（窄屏底部栏 / 宽屏侧边 rail），无登录页——启动直达首页。
 * 两个 tab 的 ViewModel 均为 Activity 作用域，切换不丢状态。
 */
@Composable
fun GreeRemoteRoot(container: AppContainer, windowSizeClass: WindowSizeClass) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    AdaptiveAppScaffold(
        windowSizeClass = windowSizeClass,
        selectedTab = tab,
        onTabSelected = { tab = it },
        destinations = AppDestinations,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
