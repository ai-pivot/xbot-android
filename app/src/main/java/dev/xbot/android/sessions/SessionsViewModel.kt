package dev.xbot.android.sessions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.xbot.android.XbotApp
import dev.xbot.android.data.UnauthorizedException
import dev.xbot.android.data.UserChat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SessionsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val sessions: List<UserChat> = emptyList(),
    val error: String? = null,
    val authenticated: Boolean = true,
    val serverUrl: String = "",
)

class SessionsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as XbotApp).container
    private val api = container.api

    private val _state = MutableStateFlow(
        SessionsUiState(serverUrl = api.baseUrl()),
    )
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    fun start() {
        if (pollJob != null) return
        refresh(showLoading = true)
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_MS)
                refresh(showLoading = false)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    fun refresh(showLoading: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = if (showLoading) true else it.loading,
                    refreshing = !showLoading,
                    serverUrl = api.baseUrl(),
                )
            }
            try {
                val tree = withAuthRetry { api.sessionTree() }
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        sessions = tree.sessions,
                        error = null,
                        authenticated = true,
                    )
                }
            } catch (e: UnauthorizedException) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        authenticated = false,
                        error = "登录已失效，请重新登录",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = e.message ?: "无法连接服务器",
                    )
                }
            }
        }
    }

    fun createChat(label: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { withAuthRetry { api.createChat(label) } }
                .onSuccess { chatId ->
                    refresh(showLoading = false)
                    onCreated(chatId)
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message ?: "创建会话失败") }
                }
        }
    }

    fun deleteChat(chat: UserChat) {
        viewModelScope.launch {
            runCatching {
                withAuthRetry {
                    api.deleteChat(chat.chatId, chat.channel.ifBlank { "web" })
                }
            }.onSuccess {
                _state.update { s ->
                    s.copy(sessions = s.sessions.filterNot { it.chatId == chat.chatId })
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "删除会话失败") }
            }
        }
    }

    fun renameChat(chat: UserChat, label: String) {
        viewModelScope.launch {
            runCatching {
                withAuthRetry {
                    api.renameChat(chat.chatId, label, chat.channel.ifBlank { "web" })
                }
            }.onSuccess { refresh() }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message ?: "重命名失败") }
                }
        }
    }

    fun forkChat(chat: UserChat, label: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            runCatching {
                withAuthRetry {
                    api.forkChat(
                        sourceChatId = chat.chatId,
                        sourceChannel = chat.channel.ifBlank { "web" },
                        label = label,
                    )
                }
            }.onSuccess { response ->
                refresh()
                onCreated(response.chatId)
            }.onFailure { e ->
                _state.update {
                    it.copy(refreshing = false, error = e.message ?: "Fork 失败")
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun <T> withAuthRetry(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: UnauthorizedException) {
            if (api.trySilentLogin()) block() else throw e
        }
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private companion object {
        const val POLL_MS = 8_000L
    }
}
