package dev.xbot.android.chat

import dev.xbot.android.data.AskUserQuestion
import dev.xbot.android.data.QueueItem
import dev.xbot.android.data.TodoItem

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
    val iterations: List<IterationUi> = emptyList(),
    val finalText: String = "",
    val error: String? = null,
    val cancelled: Boolean = false,
)

data class AskUserUi(
    val requestId: String,
    val questions: List<AskUserQuestion>,
)

data class ChatUiState(
    val chatId: String = "",
    val channel: String = "web",
    val title: String = "",
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
    val needsReload: Boolean = false,
    val pendingUserText: String? = null,
) {
    val isEmpty: Boolean get() = turns.isEmpty() && pendingUserText == null
}
