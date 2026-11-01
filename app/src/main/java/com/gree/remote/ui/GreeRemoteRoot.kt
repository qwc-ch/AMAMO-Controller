package com.gree.remote.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gree.remote.AppContainer
import com.gree.remote.ui.devices.DevicesScreen
import com.gree.remote.ui.devices.DevicesViewModel
import com.gree.remote.ui.login.LoginScreen
import com.gree.remote.ui.login.LoginViewModel
import com.gree.remote.ui.remote.RemoteScreen
import com.gree.remote.ui.remote.RemoteViewModel

/** 应用导航宿主。 */
@Composable
fun GreeRemoteRoot(container: AppContainer) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "login") {
        composable("login") {
            val vm: LoginViewModel = viewModel { LoginViewModel(container) }
            LoginScreen(vm) {
                nav.navigate("devices") { popUpTo("login") { inclusive = true } }
            }
        }
        composable("devices") {
            val vm: DevicesViewModel = viewModel { DevicesViewModel(container) }
            DevicesScreen(vm) { mac ->
                nav.navigate("remote/$mac")
            }
        }
        composable("remote/{mac}") { entry ->
            val vm: RemoteViewModel = viewModel { RemoteViewModel(container) }
            RemoteScreen(vm)
        }
    }
}