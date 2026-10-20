package dev.xbot.android.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.xbot.android.XbotApp
import dev.xbot.android.data.XbotApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val register: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val bootstrap: Boolean = false,
    val hint: String? = null,
)

class LoginViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as XbotApp).container
    private val prefs = container.prefs
    private val api = container.api

    private val _state = MutableStateFlow(
        LoginUiState(
            serverUrl = prefs.serverUrl,
            username = prefs.secrets.username(),
        ),
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun setServerUrl(value: String) = _state.update { it.copy(serverUrl = value, error = null) }
    fun setUsername(value: String) = _state.update { it.copy(username = value, error = null) }
    fun setPassword(value: String) = _state.update { it.copy(password = value, error = null) }
    fun setRegister(value: Boolean) = _state.update {
        it.copy(register = value, error = null, hint = null)
    }

    fun probe() {
        val url = XbotApi.normalizeBaseUrl(_state.value.serverUrl)
        if (url.isBlank()) {
            _state.update { it.copy(error = "请填写服务器地址") }
            return
        }
        prefs.serverUrl = url
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, hint = null) }
            runCatching { api.authConfig() }
                .onSuccess { config ->
                    _state.update {
                        it.copy(
                            loading = false,
                            bootstrap = config.bootstrap,
                            register = config.bootstrap,
                            hint = if (config.bootstrap) {
                                "这是新服务器，请创建第一个账号（将成为管理员）"
                            } else {
                                null
                            },
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, error = e.message ?: "无法连接服务器")
                    }
                }
        }
    }

    fun submit() {
        val current = _state.value
        val url = XbotApi.normalizeBaseUrl(current.serverUrl)
        if (url.isBlank()) {
            _state.update { it.copy(error = "请填写服务器地址") }
            return
        }
        if (current.username.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(error = "请填写用户名和密码") }
            return
        }
        prefs.serverUrl = url
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                if (current.register) {
                    api.register(current.username, current.password)
                } else {
                    api.login(current.username, current.password)
                }
                _state.update { it.copy(loading = false, success = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = e.message ?: "登录失败")
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** Consume the one-shot success flag so returning to the login screen does not auto-navigate. */
    fun consumeSuccess() = _state.update { it.copy(success = false) }

    fun logout() {
        viewModelScope.launch {
            runCatching { api.logout() }
            prefs.notificationsEnabled = false
            _state.update {
                LoginUiState(
                    serverUrl = prefs.serverUrl,
                    username = prefs.secrets.username(),
                )
            }
        }
    }
}
