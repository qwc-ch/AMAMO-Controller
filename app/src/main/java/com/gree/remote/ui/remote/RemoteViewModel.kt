package com.gree.remote.ui.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gree.remote.AppContainer
import com.gree.remote.data.model.AcState
import com.gree.remote.data.repo.LanSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RemoteUiState(
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val lanIp: String = "",
    val error: String? = null,
    val state: AcState = AcState(),
)

/**
 * 遥控页 ViewModel。进入即尝试局域网直连；支持手动填 IP。
 * 局域网模式完成 scan -> bind -> status，并以命令回包更新状态。
 */
class RemoteViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(RemoteUiState())
    val state: StateFlow<RemoteUiState> = _state.asStateFlow()

    private var session: LanSession? = null
    private var pollJob: Job? = null

    init {
        connect()
    }

    fun onLanIpChange(v: String) {
        _state.value = _state.value.copy(lanIp = v)
    }

    fun connect(manualIp: String? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(connecting = true, error = null)
            try {
                val s = container.repository.connectLan(manualIp ?: _state.value.lanIp.takeIf { it.isNotBlank() })
                session = s
                _state.value = _state.value.copy(connecting = false, connected = true, state = s.state)
                startPolling()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    connecting = false,
                    connected = false,
                    error = e.message ?: "局域网连接失败，请检查 WiFi 或在云端模式重试",
                )
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(4000)
                val s = session ?: break
                try {
                    val st = container.repository.refreshLanState(s)
                    _state.value = _state.value.copy(state = st)
                } catch (_: Exception) {
                    // 轮询失败忽略，下一次再试
                }
            }
        }
    }

    /** 下发命令。opt = 字段名，value = 值。 */
    fun send(opt: String, value: Int) {
        val s = session ?: return
        viewModelScope.launch {
            try {
                val st = container.repository.lanCmd(s, opt, value)
                _state.value = _state.value.copy(state = st)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "命令下发失败")
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        container.repository.close()
        super.onCleared()
    }
}