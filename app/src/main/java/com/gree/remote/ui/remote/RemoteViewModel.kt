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
     * 构造“遥控器式全量报文”的字段列表（当前状态原样回写，不含只读的室温）。
     * 真机实测（2026-11）：格力子母导风板机型（如臻焕风 KFR-35GW/35572）
     * 单字段改 SwUpDn 只动子板、母板不动；必须全量下发（模拟遥控器）母板才联动。
     * 因此摆风/开机都走这条路径。
     */
    private fun fullStateOpts(st: AcState, overrides: Map<String, Int> = emptyMap()): List<Pair<String, Int>> {
        val base = listOf(
            "Pow" to st.power,
            "Mod" to st.mode,
            "SetTem" to kotlin.math.floor(st.decodedSetTemp()).toInt(),
            "WdSpd" to st.windSpeed,
            "Air" to st.air,
            "Blo" to st.blowDry,
            "Health" to st.health,
            "SwhSlp" to st.sleep,
            "Lig" to st.light,
            "SwingLfRig" to st.swingLeftRight,
            "SwUpDn" to st.swingUpDown,
            "Quiet" to st.quiet,
            "Tur" to st.turbo,
            "StHt" to st.heatAux,
            "TemUn" to st.tempUnit,
            "HeatCoolType" to st.heatCoolType,
            "TemRec" to st.temRec,
            "SvSt" to st.saveEnergy,
        )
        return base.map { (k, v) -> k to (overrides[k] ?: v) }
    }

    /**
     * 遥控器式开机：全量状态 + Pow=1 一条报文下发。
     * 真机实测（2026-11）：单发 Pow=1 设备不刷新摆风等字段，导风板会停在异常位置
     * （遥控器开机是全量下发，板子正常往下）。
     */
    fun powerOn() {
        val s = session ?: return
        if (_state.value.busy) return
        val opts = fullStateOpts(_state.value.state, mapOf("Pow" to 1))
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                val newState = container.repository.lanCmdMulti(s, opts)
                _state.value = _state.value.copy(state = newState, busy = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = e.message ?: "开机失败")
            }
        }
    }

    /**
     * 上下风向：全量下发（仅改 SwUpDn），子母导风板机型母板才会联动（真机实测）。
     */
    fun setSwingUpDown(value: Int) {
        val s = session ?: return
        if (_state.value.busy) return
        if (_state.value.state.power != 1) return  // 关机不下发（与遥控器一致）
        val opts = fullStateOpts(_state.value.state, mapOf("SwUpDn" to value))
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                val newState = container.repository.lanCmdMulti(s, opts)
                _state.value = _state.value.copy(state = newState, busy = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = e.message ?: "风向设置失败")
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
