package dev.xbot.android.chat

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * SSE 事件合批窗口。
 *
 * 模型输出是阵发的，服务端逐条推 `stream_content` / `progress_structured`，客户端如果
 * 每来一条就 `_state.update` 一次，就会变成几十 Hz 的重组 + 重排（这也是贴底滚动一直在
 * 和内容"打架"的根源之一）。这里按「leading + 窗口 trailing」合批：第一条立刻处理，
 * 之后把 [SSE_BATCH_WINDOW_MS] 内到达的事件攒成一批一次性 apply。
 *
 * 参考 Paseo 移动端的 `agent-stream-coalescer`（每 agent 每 60ms 最多一条）与
 * `session-stream-reducers`（每帧只提交一次）。
 */
internal const val SSE_BATCH_WINDOW_MS = 60L

/** 单批上限：持续高压时也别让一批无限长（否则一次 apply 会卡住一帧以上）。 */
internal const val SSE_BATCH_MAX = 64

/**
 * 把事件流按窗口合批。
 *
 * - **leading**：窗口内第一条立即发出，不做延迟（否则每回合第一个字都要多等一个窗口）；
 * - **trailing**：随后最多再攒 [windowMs] 毫秒，或攒够 [maxBatch] 条就发出一批；
 * - 上游结束时把剩下的全部发完再结束。
 */
internal fun <T> Flow<T>.batchByWindow(
    windowMs: Long = SSE_BATCH_WINDOW_MS,
    maxBatch: Int = SSE_BATCH_MAX,
): Flow<List<T>> = channelFlow {
    val inbox = Channel<T>(Channel.UNLIMITED)
    launch {
        collect { inbox.send(it) }
        inbox.close()
    }
    while (true) {
        val first = inbox.receiveCatching().getOrNull() ?: break
        // leading：窗口里的第一条立刻发出去，别让每个回合的第一个字多等一个窗口。
        send(listOf(first))

        // trailing：随后最多再攒一个窗口（或攒够一批）一起发。
        val batch = ArrayList<T>(minOf(maxBatch, 16))
        var upstreamClosed = false
        withTimeoutOrNull(windowMs) {
            while (batch.size < maxBatch) {
                val next = inbox.receiveCatching().getOrNull()
                if (next == null) {
                    upstreamClosed = true
                    return@withTimeoutOrNull
                }
                batch += next
            }
        }
        if (batch.isNotEmpty()) send(batch)
        if (upstreamClosed) break
    }
}
