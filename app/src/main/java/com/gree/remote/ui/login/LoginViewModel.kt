package com.gree.remote.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gree.remote.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val user: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
)

class LoginViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onUserChange(v: String) {
        _state.value = _state.value.copy(user = v)
    }

    fun onPasswordChange(v: String) {
        _state.value = _state.value.copy(password = v)
    }

    /** 仅保存云端凭据。局域网模式无需账号。 */
    fun saveCredentials() {
        container.saveCredentials(_state.value.user, _state.value.password)
        _state.value = _state.value.copy(loggedIn = true)
    }

    fun login() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                container.cloudClient.login(
                    _state.value.user, _state.value.password
                )
                container.saveCredentials(_state.value.user, _state.value.password)
                _state.value = _state.value.copy(loading = false, loggedIn = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "登录失败",
                )
            }
        }
    }
}