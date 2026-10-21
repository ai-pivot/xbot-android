package dev.xbot.android.chat

import dev.xbot.android.data.AskUserQuestion
import dev.xbot.android.data.QueueItem
import dev.xbot.android.data.TodoItem
import kotlin.math.roundToInt

data class ToolUi(
    val key: String,
    val name: String,
    val label: String,
    val status: String,
    val args: String,
    val detail: String,
    val summary: String,
    val elapsedMs: Long,
    val iteration: Int,
) {
    val title: String get() = label.ifBlank { name }
    val running: Boolean get() = status == "running" || status == "generating"
    val failed: Boolean get() = status == "failed" || status == "error"
}

data class IterationUi(
    val index: Int,
    val content: String = "",
    val reasoning: String = "",
    val tools: List<ToolUi> = emptyList(),
    val done: Boolean = false,
)

data class TurnUi(
    val turnId: Long,
    val userText: String = "",
    val userHistoryId: Long = 0L,
    val iterations: List<IterationUi> = emptyList(),
    val finalText: String = "",
    val error: String? = null,
    val cancelled: Boolean = false,
)

data class AskUserUi(
    val requestId: String,
    val questions: List<AskUserQuestion>,
)

/**
 * 上下文预算：服务端 token 用量在 UI 侧的投影。
 *
 * [percent] 已归一化到 0..1；[exact] 为 true 表示来自服务端 `usage_percent`，
 * false 表示用 `prompt_tokens / max_context_tokens` 自己估的（回合进行中的实时值）。
 */
data class ContextBudgetUi(
    val promptTokens: Long = 0L,
    val maxContextTokens: Long = 0L,
    val percent: Float = 0f,
    val model: String = "",
    val exact: Boolean = false,
) {
    val usedPercent: Int get() = (percent * 100f).roundToInt().coerceIn(0, 100)
}

data class ChatUiState(
    val chatId: String = "",
    val channel: String = "web",
    val title: String = "",
    /** 该会话的工作目录（服务端 work_dir / session status 的 cwd）。 */
    val cwd: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val connected: Boolean = false,
    val busy: Boolean = false,
    val turns: List<TurnUi> = emptyList(),
    val liveTurnId: Long = 0L,
    val liveIteration: Int = 0,
    val askUser: AskUserUi? = null,
    val queue: List<QueueItem> = emptyList(),
    val todos: List<TodoItem> = emptyList(),
    val hasMore: Boolean = false,
    val oldestId: Long = 0L,
    val loadingMore: Boolean = false,
    val loadMoreError: String? = null,
    val needsReload: Boolean = false,
    val pendingUserText: String? = null,
    /** null = 服务端还没有真实 token 数据（新会话 / 未发起过 LLM 调用）。 */
    val contextBudget: ContextBudgetUi? = null,
) {
    val isEmpty: Boolean get() = turns.isEmpty() && pendingUserText == null
}
