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
    val error: String? = null,
    val busy: Boolean = false,          // 命令下发中（禁用控件防 UDP 乱序）
    val state: AcState = AcState(),
)

/**
 * 单台设备遥控 ViewModel（首页每设备一个分页各持一个实例，按 mac 作 key）。
 * 进入分页即连接（bind -> status）并开始轮询；切走分页后 [setActive](false) 停轮询省电。
 */
class RemoteViewModel(
    private val container: AppContainer,
    private val targetMac: String,
    private val targetIp: String? = null,
    private val firmwareVer: String? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(RemoteUiState())
    val state: StateFlow<RemoteUiState> = _state.asStateFlow()

    private var session: LanSession? = null
    private var pollJob: Job? = null
    private var active = false

    /** 分页可见性：true 恢复轮询，false 暂停。 */
    fun setActive(v: Boolean) {
        active = v
        if (v && session != null) startPolling() else pollJob?.cancel()
        // 首次激活且未连接过时发起连接
        if (v && session == null && !_state.value.connecting && !_state.value.connected) {
            connect()
        }
    }

    init {
        // 创建即尝试连接（分页组合会紧接着调 setActive(true)）
        connect()
    }

    fun connect() {
        if (_state.value.connecting || session != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(connecting = true, error = null)
            try {
                val s = container.repository.connectLan(
                    manualIp = targetIp,
                    targetMac = targetMac,
                    firmwareVer = firmwareVer,
                )
                session = s
                _state.value = _state.value.copy(connecting = false, connected = true, state = s.state)
                if (active) startPolling()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    connecting = false,
                    connected = false,
                    error = e.message ?: "局域网连接失败，请确认手机与空调在同一 WiFi",
                )
            }
        }
    }

    /** 重置会话并重连（换网络/失败恢复）。 */
    fun reconnect() {
        session = null
        pollJob?.cancel()
        _state.value = _state.value.copy(connected = false)
        connect()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (active) {
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
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                val st = container.repository.lanCmd(s, opt, value)
                _state.value = _state.value.copy(state = st, busy = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = e.message ?: "命令下发失败")
            }
        }
    }

    /**
     * 设定温度（支持 0.5℃）：
     * - 设备已用 TemRec 位（支持半度）→ SetTem + TemRec 一条报文组合下发；
     * - 不支持 → 仅 SetTem（自动取整）。
     */
    fun setTemp(value: Double) {
        val s = session ?: return
        if (_state.value.busy) return
        val clamped = value.coerceIn(MIN_TEMP, MAX_TEMP)
        val st = _state.value.state
        val opts = mutableListOf("SetTem" to kotlin.math.floor(clamped).toInt())
        if (st.temRec == 1) {
            opts += "TemRec" to if (clamped % 1.0 != 0.0) 1 else 0
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                val newState = container.repository.lanCmdMulti(s, opts)
                _state.value = _state.value.copy(state = newState, busy = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = e.message ?: "温度设置失败")
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    companion object {
        const val MIN_TEMP = 16.0
        const val MAX_TEMP = 30.0
    }
}
