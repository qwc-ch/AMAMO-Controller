package com.gree.remote.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gree.remote.AppContainer
import com.gree.remote.data.model.AcDevice
import com.gree.remote.ui.theme.DisplayPrefs
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
    val showLogin: Boolean = false,
    val loginBusy: Boolean = false,
    val loginError: String? = null,
    // 显示偏好（Shirone 风格全部选项）
    val displayPrefs: DisplayPrefs = DisplayPrefs(),
    val displayCustom: Boolean = false,
)

/**
 * 设置页 ViewModel：显示偏好（色相/样式/spec/背景/布局/纹理/动效）+ 云端账号。
 */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(cloudLoggedIn = container.hasSavedUser()),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.displayPrefs.collect { prefs ->
                _state.update { it.copy(displayPrefs = prefs) }
            }
        }
        viewModelScope.launch {
            container.displayCustom.collect { custom ->
                _state.update { it.copy(displayCustom = custom) }
            }
        }
    }

    // ---- 显示偏好 ----

    fun setDisplayCustom(enabled: Boolean) = viewModelScope.launch { container.setDisplayCustom(enabled) }
    fun setDisplayHue(hue: Float) = viewModelScope.launch { container.setDisplayHue(hue) }
    fun setDisplayStyle(style: String) = viewModelScope.launch { container.setDisplayStyle(style) }
    fun setDisplaySpec2025(enabled: Boolean) = viewModelScope.launch { container.setDisplaySpec2025(enabled) }
    fun setDisplayWallpaper(mode: String) = viewModelScope.launch { container.setDisplayWallpaper(mode) }
    fun setDisplayLayout(mode: String) = viewModelScope.launch { container.setDisplayLayout(mode) }
    fun setDisplayTexture(preset: String) = viewModelScope.launch { container.setDisplayTexture(preset) }
    fun setDisplayTextureOpacity(opacity: Float) = viewModelScope.launch { container.setDisplayTextureOpacity(opacity) }
    fun setDisplayReduceMotion(enabled: Boolean) = viewModelScope.launch { container.setDisplayReduceMotion(enabled) }
    fun resetDisplayPrefs() = viewModelScope.launch { container.resetDisplayPrefs() }

    /** 是否有偏离默认值的选择（控制重置按钮可见性）。 */
    fun isDisplayDirty(): Boolean {
        val s = _state.value
        return s.displayCustom ||
            s.displayPrefs != DisplayPrefs()
    }

    // ---- 云端账号 ----

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
