package dev.xbot.android.data

import dev.xbot.android.logs.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import kotlin.math.min
import kotlin.random.Random

sealed interface SseState {
    data object Disconnected : SseState
    data object Connecting : SseState
    data object Connected : SseState
    data class Error(val message: String, val unauthorized: Boolean = false) : SseState
}

/**
 * One SSE connection per session, mirroring the web client's EventSource.
 *
 * - `Last-Event-ID` resumes from the server's ring buffer.
 * - 15s server heartbeat feeds a watchdog; silence > 45s forces a reconnect.
 * - Exponential backoff with jitter, reset on a successful open.
 */
class XbotSseClient(
    private val api: XbotApi,
    private val prefs: AppPrefs,
    private val scope: CoroutineScope,
) {
    private companion object {
        /** 流式内容类事件太多，不逐条写日志，只记结构性事件。 */
        val NOISY_EVENTS = setOf(
            "progress",
            "progress_structured",
            "sync_progress",
            "stream_content",
            "text",
        )
    }

    private val _events = MutableSharedFlow<WsMessage>(
        extraBufferCapacity = 512,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<WsMessage> = _events.asSharedFlow()

    private val _state = MutableStateFlow<SseState>(SseState.Disconnected)
    val state: StateFlow<SseState> = _state.asStateFlow()

    private var source: EventSource? = null
    private var reconnectJob: Job? = null
    private var watchdogJob: Job? = null
    private var shouldRun = false
    private var attempt = 0
    private var chatId = ""
    private var channel = "web"
    private var lastSeq = 0L
    private var lastActivityAt = 0L

    fun start(chatId: String, channel: String = "web") {
        stop()
        this.chatId = chatId
        this.channel = channel
        this.lastSeq = prefs.lastSeq(chatId)
        this.shouldRun = true
        this.attempt = 0
        _state.value = SseState.Connecting
        connect()
        startWatchdog()
    }

    fun stop() {
        shouldRun = false
        reconnectJob?.cancel()
        reconnectJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        source?.cancel()
        source = null
        _state.value = SseState.Disconnected
    }

    fun currentChatId(): String = chatId

    private fun connect() {
        if (!shouldRun || chatId.isBlank()) return
        _state.value = SseState.Connecting
        val url = runCatching {
            val raw = XbotApi.normalizeBaseUrl(prefs.serverUrl) +
                "/api/sse?chat_id=${enc(chatId)}&channel=${enc(channel)}"
            raw.toHttpUrlOrNull() ?: throw IllegalStateException("bad server url")
        }.getOrElse { e ->
            _state.value = SseState.Error(e.message ?: "服务器地址无效")
            return
        }

        val builder = Request.Builder().url(url).get().header("Accept", "text/event-stream")
        if (lastSeq > 0) builder.header("Last-Event-ID", lastSeq.toString())

        lastActivityAt = System.currentTimeMillis()
        val request = builder.build()
        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                if (eventSource !== source) return
                attempt = 0
                lastActivityAt = System.currentTimeMillis()
                _state.value = SseState.Connected
                AppLog.i("sse", "connected chat=$chatId channel=$channel")
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                if (eventSource !== source) return
                lastActivityAt = System.currentTimeMillis()
                if (!id.isNullOrBlank()) {
                    id.toLongOrNull()?.let { seq ->
                        if (seq > lastSeq) {
                            lastSeq = seq
                            prefs.setLastSeq(chatId, seq)
                        }
                    }
                }
                if (type == "heartbeat") return
                val message = runCatching {
                    XbotJson.decodeFromString<WsMessage>(data)
                }.getOrNull() ?: return
                // 流式内容每几毫秒一条，不逐条落日志；只记「结构性」事件。
                val resolved = message.copy(type = message.type.ifBlank { type.orEmpty() })
                if (resolved.type !in NOISY_EVENTS) {
                    AppLog.d("sse", "event ${resolved.type} ${data.length}B")
                }
                _events.tryEmit(resolved)
            }

            override fun onClosed(eventSource: EventSource) {
                if (eventSource !== source) return
                source = null
                if (shouldRun) scheduleReconnect("连接已关闭")
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?,
            ) {
                if (eventSource !== source) return
                source = null
                val code = response?.code ?: 0
                if (code == 401) {
                    shouldRun = false
                    _state.value = SseState.Error("登录已失效，请重新登录", unauthorized = true)
                    return
                }
                if (shouldRun) {
                    _state.value = SseState.Error(t?.message ?: "连接失败")
                    scheduleReconnect(t?.message ?: "连接失败")
                }
            }
        }

        source = EventSources.createFactory(api.sseClient).newEventSource(request, listener)
    }

    private fun scheduleReconnect(reason: String) {
        if (!shouldRun) return
        reconnectJob?.cancel()
        val waitMs = min(30_000L, 1_000L shl min(attempt, 5)) +
            Random.nextLong(0, 500)
        attempt += 1
        _state.value = SseState.Error(reason)
        AppLog.w("sse", "reconnect in ${waitMs}ms: $reason (attempt=$attempt)")
        reconnectJob = scope.launch {
            delay(waitMs)
            if (shouldRun) connect()
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (shouldRun) {
                delay(15_000)
                if (!shouldRun) return@launch
                val silentFor = System.currentTimeMillis() - lastActivityAt
                if (silentFor > 50_000L && _state.value is SseState.Connected) {
                    source?.cancel()
                    source = null
                    scheduleReconnect("连接超时")
                }
            }
        }
    }

    private fun enc(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
