package com.gree.remote.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gree.remote.AppContainer
import com.gree.remote.data.repo.LanDiscoveredDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val scanning: Boolean = false,
    val devices: List<LanDiscoveredDevice> = emptyList(),
    val error: String? = null,
)

/**
 * 首页 ViewModel：局域网扫描。
 * 扫描只发现不 bind；每台设备一个分页，进入分页后才建立会话（bind + status）。
 */
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** 局域网扫描：发现同一 WiFi 下的格力空调。 */
    fun scanLan() {
        if (_state.value.scanning) return
        viewModelScope.launch {
            _state.update { it.copy(scanning = true, error = null) }
            try {
                val devs = container.repository.scanLan()
                _state.update {
                    it.copy(
                        scanning = false,
                        devices = devs,
                        error = if (devs.isEmpty()) "未发现设备：请确认手机与空调在同一 WiFi" else null,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(scanning = false, error = e.message ?: "扫描失败") }
            }
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }
}
