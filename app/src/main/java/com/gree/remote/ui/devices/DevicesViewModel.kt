package com.gree.remote.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gree.remote.AppContainer
import com.gree.remote.data.model.AcDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DevicesUiState(
    val loading: Boolean = false,
    val devices: List<AcDevice> = emptyList(),
    val error: String? = null,
)

class DevicesViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(DevicesUiState())
    val state: StateFlow<DevicesUiState> = _state.asStateFlow()

    fun loadCloudDevices() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val devs = container.repository.cloudDevices()
                _state.value = _state.value.copy(loading = false, devices = devs)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "获取设备失败",
                )
            }
        }
    }
}