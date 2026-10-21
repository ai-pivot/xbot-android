package dev.xbot.android.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.xbot.android.XbotApp
import dev.xbot.android.data.UnauthorizedException
import dev.xbot.android.data.WsMessage
import dev.xbot.android.data.XbotSseClient
import dev.xbot.android.data.SseState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as XbotApp).container
    private val api = container.api
    private val prefs = container.prefs

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var sse: XbotSseClient? = null
    private var eventsJob: Job? = null
    private var sseStateJob: Job? = null
    private var loadJob: Job? = null

    private var chatId = ""
    private var channel = "web"
    private var historyLimit = 40
    private var reloading = false

    fun open(chatId: String, channel: String, title: String) {
        if (this.chatId == chatId && this.channel == channel) return
        closeStream()
        this.chatId = chatId
        this.channel = channel
        historyLimit = 40
        _state.value = ChatReducer.empty(chatId, channel, title)
        prefs.activeChatId = chatId
        prefs.activeChannel = channel
        startStream()
        reload()
    }

    fun reload() {
        if (chatId.isBlank() || reloading) return
        reloading = true
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val response = withAuthRetry {
                    api.history(chatId, channel, historyLimit, 0)
                }
                _state.update { current ->
                    if (current.turns.isNotEmpty() && current.oldestId > 0L) {
                        ChatReducer.mergeLatestHistory(current, response)
                    } else {
                        ChatReducer.withHistory(current, response)
                    }
                }
                restorePendingAskUser()
            } catch (e: UnauthorizedException) {
                _state.update { it.copy(loading = false, error = "登录已失效，请重新登录") }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
            } finally {
                reloading = false
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (!current.hasMore ||
            current.oldestId <= 0L ||
            current.loading ||
            current.loadingMore
        ) {
            return
        }
        _state.update { it.copy(loadingMore = true, loadMoreError = null) }
        viewModelScope.launch {
            runCatching {
                withAuthRetry {
                    api.history(chatId, channel, historyLimit, current.oldestId)
                }
            }.onSuccess { response ->
                _state.update { ChatReducer.prependHistory(it, response) }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        loadingMore = false,
                        loadMoreError = e.message ?: "加载更早的消息失败",
                    )
                }
            }
        }
    }

    fun retryLoadMore() = loadMore()

    fun send(
        text: String,
        uploadKeys: List<String> = emptyList(),
        fileNames: List<String> = emptyList(),
        fileSizes: List<Long> = emptyList(),
        fileMimes: List<String> = emptyList(),
    ) {
        val content = text.trim()
        if ((content.isEmpty() && uploadKeys.isEmpty()) || chatId.isBlank()) return
        _state.update { it.copy(pendingUserText = content.ifBlank { "（附件）" }, error = null) }
        viewModelScope.launch {
            try {
                val response = withAuthRetry {
                    api.sendMessage(
                        content = content,
                        chatId = chatId,
                        channel = channel,
                        uploadKeys = uploadKeys,
                        fileNames = fileNames,
                        fileSizes = fileSizes,
                        fileMimes = fileMimes,
                    )
                }
                _state.update {
                    ChatReducer.sendAccepted(
                        it,
                        content.ifBlank { "（附件）" },
                        response.turnId,
                        response.queued,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(pendingUserText = null, error = e.message ?: "发送失败")
                }
            }
        }
    }

    fun cancel() {
        if (chatId.isBlank()) return
        viewModelScope.launch {
            runCatching { api.cancel(chatId, channel) }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun respondAskUser(answers: Map<String, String>, cancelled: Boolean) {
        val prompt = _state.value.askUser ?: return
        _state.update { ChatReducer.dismissAskUser(it) }
        viewModelScope.launch {
            runCatching {
                api.respondAskUser(chatId, answers, cancelled, channel)
            }.onFailure { e ->
                _state.update { it.copy(error = e.message, askUser = prompt) }
            }
        }
    }

    fun dismissAskUser() {
        _state.update { ChatReducer.dismissAskUser(it) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /** Drop the open chat, its SSE connection, and all rendered state (logout). */
    fun reset() {
        closeStream()
        loadJob?.cancel()
        loadJob = null
        chatId = ""
        channel = "web"
        _state.value = ChatUiState()
    }

    fun acknowledgeReload() {
        _state.update { it.copy(needsReload = false) }
    }

    private fun startStream() {
        val client = XbotSseClient(api, prefs, viewModelScope)
        sse = client
        eventsJob = viewModelScope.launch {
            client.events.collect { message ->
                _state.update { ChatReducer.applyEvent(it, message) }
                if (_state.value.needsReload) {
                    acknowledgeReload()
                    reload()
                }
            }
        }
        sseStateJob = viewModelScope.launch {
            client.state.collect { streamState ->
                when (streamState) {
                    is SseState.Connected -> _state.update { it.copy(connected = true) }
                    is SseState.Connecting -> _state.update { it.copy(connected = false) }
                    is SseState.Disconnected -> _state.update { it.copy(connected = false) }
                    is SseState.Error -> _state.update {
                        it.copy(
                            connected = false,
                            error = if (streamState.unauthorized) "登录已失效，请重新登录" else it.error,
                        )
                    }
                }
            }
        }
        client.start(chatId, channel)
    }

    private fun restorePendingAskUser() {
        viewModelScope.launch {
            val pending = runCatching {
                withAuthRetry { api.getPendingAskUser(chatId, channel) }
            }.getOrNull() ?: return@launch
            _state.update {
                ChatReducer.applyAskUser(it, WsMessage(type = "ask_user", progress = pending))
            }
        }
    }

    private suspend fun <T> withAuthRetry(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: UnauthorizedException) {
            if (api.trySilentLogin()) block() else throw e
        }
    }

    private fun closeStream() {
        eventsJob?.cancel()
        sseStateJob?.cancel()
        eventsJob = null
        sseStateJob = null
        sse?.stop()
        sse = null
    }

    override fun onCleared() {
        closeStream()
        loadJob?.cancel()
        super.onCleared()
    }
}
