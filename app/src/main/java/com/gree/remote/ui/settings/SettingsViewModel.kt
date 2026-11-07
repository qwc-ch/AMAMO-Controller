package com.gree.remote.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gree.remote.AppContainer
import com.gree.remote.data.model.AcDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    // 云端账号
    val cloudLoggedIn: Boolean = false,
    val cloudLoading: Boolean = false,
    val cloudDevices: List<AcDevice> = emptyList(),
    val cloudError: String? = null,
    // 登录弹窗
    val showLogin: Boolean = false,
    val loginBusy: Boolean = false,
    val loginError: String? = null,
    // 通用
    val dynamicColor: Boolean = true,
)

/**
 * 设置页 ViewModel：通用偏好（动态取色）+ 云端账号（登录/登出/设备列表）。
 */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(cloudLoggedIn = container.hasSavedUser()),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.dynamicColor.collect { enabled ->
                _state.update { it.copy(dynamicColor = enabled) }
            }
        }
    }

    /** 动态取色开关（Material You，API 31+ 生效）。 */
    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { container.setDynamicColor(enabled) }
    }

    fun loadCloudDevices() {
        if (!_state.value.cloudLoggedIn) {
            _state.update { it.copy(showLogin = true) }
            return
        }
        if (_state.value.cloudLoading) return
        viewModelScope.launch {
            _state.update { it.copy(cloudLoading = true, cloudError = null) }
            try {
                val devs = container.repository.cloudDevices()
                _state.update { it.copy(cloudLoading = false, cloudDevices = devs) }
            } catch (e: Exception) {
                _state.update { it.copy(cloudLoading = false, cloudError = e.message ?: "获取云端设备失败") }
            }
        }
    }

    fun login(user: String, password: String) {
        if (user.isBlank() || password.isBlank()) {
            _state.update { it.copy(loginError = "请输入账号和密码") }
            return
        }
        if (_state.value.loginBusy) return
        viewModelScope.launch {
            _state.update { it.copy(loginBusy = true, loginError = null) }
            try {
                container.cloudClient.login(user, password)
                container.saveCredentials(user, password)
                _state.update { it.copy(loginBusy = false, cloudLoggedIn = true, showLogin = false) }
                loadCloudDevices()
            } catch (e: Exception) {
                _state.update { it.copy(loginBusy = false, loginError = e.message ?: "登录失败") }
            }
        }
    }

    fun logout() {
        container.clearCredentials()
        _state.update { it.copy(cloudLoggedIn = false, cloudDevices = emptyList(), cloudError = null) }
    }

    fun showLogin(show: Boolean) {
        _state.update { it.copy(showLogin = show, loginError = null) }
    }
}
