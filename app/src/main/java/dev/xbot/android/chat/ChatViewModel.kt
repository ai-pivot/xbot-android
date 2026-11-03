package dev.xbot.android.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.xbot.android.XbotApp
import dev.xbot.android.logs.AppLog
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
    private var budgetJob: Job? = null

    private var chatId = ""
    private var channel = "web"
    private var historyLimit = 40
    private var reloading = false

    fun open(chatId: String, channel: String, title: String) {
        if (this.chatId == chatId && this.channel == channel) return
        AppLog.i("chat", "open chat=$chatId channel=$channel")
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

    /**
     * Reload the newest page of history.
     *
     * [replaceTurns] must be true whenever the server may have **removed** turns
     * (rewind, or any truncation): merging keeps turns that no longer exist
     * server-side, which is exactly why a rewound message used to stay on screen
     * until the chat was reopened. Normal reloads keep the merge so pages loaded
     * by scrolling up are not thrown away.
     */
    fun reload(replaceTurns: Boolean = false) {
        if (chatId.isBlank() || reloading) return
        reloading = true
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                loadHistoryOnce(replaceTurns)
            } finally {
                reloading = false
            }
        }
    }

    private suspend fun loadHistoryOnce(replaceTurns: Boolean) {
        try {
            val response = withAuthRetry {
                api.history(chatId, channel, historyLimit, 0)
            }
            _state.update { current ->
                if (!replaceTurns && current.turns.isNotEmpty() && current.oldestId > 0L) {
                    ChatReducer.mergeLatestHistory(current, response)
                } else {
                    ChatReducer.withHistory(current, response)
                }
            }
            restorePendingAskUser()
            refreshContextBudget()
        } catch (e: UnauthorizedException) {
            _state.update { it.copy(loading = false, error = "登录已失效，请重新登录") }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
        }
    }

    /**
     * 回退到某条用户消息（[historyId] 是它的 `userHistoryId`）。
     *
     * 顺序是关键：先 `rewind`（服务端截断历史）→ 再等历史**整体重载**落地 → 最后才决定发不发。
     * 如果先发再重载，新消息会挂在旧列表上，SSE 回声还会多出一条；如果只重载不等待，
     * 发送时 `turns` 还是旧的，界面上看起来就像"消息发丢了"。
     *
     * [resend] = true：用 [text] 重新发送；
     * [resend] = false：**什么都不发**，只把 [text] 回填到输入框（`ChatUiState.draft`，UI 消费后清空）。
     */
    fun rewindTo(historyId: Long, text: String, resend: Boolean) {
        if (chatId.isBlank() || historyId <= 0L) return
        val content = text.trim()
        viewModelScope.launch {
            _state.update {
                it.copy(
                    rewinding = true,
                    error = null,
                    draft = null,
                    pendingUserText = if (resend && content.isNotEmpty()) content else it.pendingUserText,
                )
            }
            try {
                withAuthRetry { api.rewindHistory(chatId, channel, historyId) }
                loadHistoryOnce(replaceTurns = true)
                _state.update {
                    it.copy(
                        rewinding = false,
                        draft = if (resend) null else content.ifBlank { null },
                    )
                }
                if (resend && content.isNotEmpty()) send(content)
            } catch (e: UnauthorizedException) {
                _state.update {
                    it.copy(rewinding = false, pendingUserText = null, error = "登录已失效，请重新登录")
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(rewinding = false, pendingUserText = null, error = e.message ?: "回退失败")
                }
            }
        }
    }

    /** UI 把 [ChatUiState.draft] 回填输入框后调用，避免重复消费。 */
    fun consumeDraft() {
        _state.update { if (it.draft == null) it else it.copy(draft = null) }
    }

    /**
     * 拉一次 `/api/session/status` 刷新上下文预算。
     * 失败静默：指示器只是辅助信息，不该因为它弹错误条。
     */
    fun refreshContextBudget() {
        if (chatId.isBlank()) return
        budgetJob?.cancel()
        budgetJob = viewModelScope.launch {
            runCatching {
                withAuthRetry { api.sessionStatus(chatId, channel) }
            }.onSuccess { status ->
                _state.update { ChatReducer.withSessionStatus(it, status) }
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
        _state.update { it.copy(needsReload = false, reloadReplacesHistory = false) }
    }

    private fun startStream() {
        val client = XbotSseClient(api, prefs, viewModelScope)
        sse = client
        eventsJob = viewModelScope.launch {
            // 合批：一条事件进来先处理（leading），随后 60ms 内到达的攒成一批一起 apply，
            // 避免每来一条 progress 就重组一次（流式时的重组/重排风暴）。
            client.events.batchByWindow().collect { batch ->
                val wasBusy = _state.value.busy
                _state.update { state ->
                    batch.fold(state) { acc, message -> ChatReducer.applyEvent(acc, message) }
                }
                // 回合结束（busy 由 true 转 false）时服务端才拿到最终 token 数，
                // 这时刷新一次上下文预算，指示器才不会停在回合中途的估值上。
                if (wasBusy && !_state.value.busy) refreshContextBudget()
                if (_state.value.needsReload) {
                    val replace = _state.value.reloadReplacesHistory
                    acknowledgeReload()
                    reload(replaceTurns = replace)
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
        budgetJob?.cancel()
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
