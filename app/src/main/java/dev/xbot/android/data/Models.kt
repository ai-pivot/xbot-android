package dev.xbot.android.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WsMessage(
    val type: String = "",
    val id: String = "",
    val seq: Long = 0L,
    val content: String = "",
    @SerialName("original_content") val originalContent: String? = null,
    val ts: Long = 0L,
    val progress: ProgressEvent? = null,
    val channel: String = "",
    @SerialName("chat_id") val chatId: String = "",
    @SerialName("route_channel") val routeChannel: String? = null,
    @SerialName("route_chat_id") val routeChatId: String? = null,
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("sender_name") val senderName: String? = null,
    val cancelled: Boolean = false,
    @SerialName("turn_id") val turnId: Long = 0L,
    val interrupted: Boolean = false,
    @SerialName("request_id") val requestId: String = "",
    val reason: String = "",
    val session: SessionEvent? = null,
    @SerialName("queue_state") val queueState: QueueState? = null,
    val metadata: Map<String, String> = emptyMap(),
    val result: JsonElement? = null,
    val error: String? = null,
)

@Serializable
data class ProgressEvent(
    val iteration: Int = 0,
    val content: String = "",
    val reasoning: String = "",
    @SerialName("tool_calls") val toolCalls: List<ToolCallSnapshot> = emptyList(),
    @SerialName("elapsed_wall") val elapsedWall: Long = 0L,
    @SerialName("chat_id") val chatId: String = "",
    val seq: Long = 0L,
    val phase: String = "",
    @SerialName("active_tools") val activeTools: List<ToolProgress> = emptyList(),
    @SerialName("completed_tools") val completedTools: List<ToolProgress> = emptyList(),
    val todos: List<TodoItem> = emptyList(),
    val goal: GoalInfo? = null,
    @SerialName("token_usage") val tokenUsage: TokenUsage? = null,
    val questions: List<AskUserQuestion> = emptyList(),
    @SerialName("request_id") val requestId: String = "",
    @SerialName("stream_content") val streamContent: String = "",
    @SerialName("stream_delta") val streamDelta: String = "",
    @SerialName("reasoning_stream_content") val reasoningStreamContent: String = "",
    @SerialName("reasoning_stream_delta") val reasoningStreamDelta: String = "",
    @SerialName("streaming_tools") val streamingTools: List<ToolProgress> = emptyList(),
    @SerialName("iteration_history") val iterationHistory: List<ProgressEvent> = emptyList(),
    @SerialName("history_compacted") val historyCompacted: Boolean = false,
    val cwd: String = "",
    @SerialName("resync_required") val resyncRequired: Boolean = false,
    @SerialName("turn_id") val turnId: Long = 0L,
    @SerialName("turn_start") val turnStart: TurnStartInfo? = null,
    @SerialName("iteration_regions_before") val iterationRegionsBefore: Int = 0,
    @SerialName("tools_folded") val toolsFolded: Boolean = false,
)

@Serializable
data class ToolCallSnapshot(
    val id: String = "",
    val name: String = "",
    val args: String = "",
    val status: String = "",
    val elapsed: Long = 0L,
)

@Serializable
data class ToolProgress(
    val name: String = "",
    val label: String = "",
    val status: String = "",
    @SerialName("elapsed_ms") val elapsedMs: Long = 0L,
    val iteration: Int = 0,
    val summary: String = "",
    val detail: String = "",
    val args: String = "",
    @SerialName("tool_hints") val toolHints: String = "",
    @SerialName("started_at") val startedAt: String = "",
    @SerialName("ui_mode") val uiMode: String = "",
    @SerialName("ui_libs") val uiLibs: List<String> = emptyList(),
    @SerialName("ui_surface") val uiSurface: UISurface? = null,
    @SerialName("gen_chars") val genChars: Int = 0,
    @SerialName("call_id") val callId: String = "",
)

@Serializable
data class UISurface(
    val kind: String = "",
    val title: String = "",
    val collapsible: Boolean = false,
    val fullscreen: Boolean = false,
    @SerialName("default_open") val defaultOpen: Boolean = false,
)

@Serializable
data class TodoItem(
    val text: String = "",
    val status: String = "pending",
)

@Serializable
data class GoalInfo(
    val objective: String = "",
    val status: String = "",
    val summary: String = "",
)

@Serializable
data class TokenUsage(
    @SerialName("prompt_tokens") val promptTokens: Long = 0L,
    @SerialName("completion_tokens") val completionTokens: Long = 0L,
    @SerialName("total_tokens") val totalTokens: Long = 0L,
    @SerialName("cache_hit_tokens") val cacheHitTokens: Long = 0L,
    @SerialName("max_output_tokens") val maxOutputTokens: Long = 0L,
    @SerialName("context_usage_percent") val contextUsagePercent: Double = 0.0,
)

@Serializable
data class AskUserQuestion(
    val question: String = "",
    val options: List<String> = emptyList(),
    @SerialName("multi_select") val multiSelect: Boolean = false,
    @SerialName("allow_other") val allowOther: Boolean = false,
)

@Serializable
data class TurnStartInfo(
    val trigger: String = "",
    val content: String = "",
    @SerialName("request_id") val requestId: String = "",
    @SerialName("sender_name") val senderName: String = "",
)

@Serializable
data class SessionEvent(
    val channel: String = "",
    @SerialName("chat_id") val chatId: String = "",
    val action: String = "",
    val label: String = "",
    val role: String = "",
    val instance: String = "",
    @SerialName("session_key") val sessionKey: String = "",
    @SerialName("parent_id") val parentId: String = "",
    @SerialName("target_history_id") val targetHistoryId: Long = 0L,
    val senderId: String? = null,
    val removed: Boolean = false,
)

@Serializable
data class QueueState(
    val channel: String = "",
    @SerialName("chat_id") val chatId: String = "",
    val items: List<QueueItem> = emptyList(),
)

@Serializable
data class QueueItem(
    @SerialName("msg_id") val msgId: String = "",
    @SerialName("turn_id") val turnId: Long = 0L,
    val content: String = "",
    val preview: String = "",
    val source: String = "",
    @SerialName("enqueued_at") val enqueuedAt: Long = 0L,
)

@Serializable
data class HistoryMessage(
    val id: Long = 0L,
    @SerialName("history_id") val historyId: Long = 0L,
    val role: String = "",
    val content: String = "",
    @SerialName("reasoning_content") val reasoningContent: String = "",
    @SerialName("tool_call_id") val toolCallId: String = "",
    @SerialName("tool_name") val toolName: String = "",
    @SerialName("tool_arguments") val toolArguments: String = "",
    val timestamp: String = "",
    @SerialName("turn_id") val turnId: Long = 0L,
    val iterations: List<HistoryIteration> = emptyList(),
    @SerialName("iterations_truncated") val iterationsTruncated: Int = 0,
    @SerialName("regions_before") val regionsBefore: Int = 0,
    @SerialName("record_type") val recordType: String = "",
    @SerialName("display_only") val displayOnly: Boolean = false,
    val standalone: Boolean = false,
    @SerialName("anchor_turn_id") val anchorTurnId: Long = 0L,
    @SerialName("turn_user") val turnUser: HistoryMessage? = null,
)

@Serializable
data class HistoryIteration(
    val iteration: Int = 0,
    val content: String = "",
    val reasoning: String = "",
    val tools: List<ToolProgress> = emptyList(),
    @SerialName("elapsed_wall") val elapsedWall: Long = 0L,
    val tokens: Long = 0L,
    @SerialName("ttft_ms") val ttftMs: Long = 0L,
    @SerialName("tokens_per_sec") val tokensPerSec: Long = 0L,
    @SerialName("total_ms") val totalMs: Long = 0L,
    @SerialName("tools_folded") val toolsFolded: Boolean = false,
)

@Serializable
data class HistoryResponse(
    val ok: Boolean = false,
    val messages: List<HistoryMessage> = emptyList(),
    val processing: Boolean = false,
    @SerialName("active_progress") val activeProgress: ProgressEvent? = null,
    @SerialName("last_seq") val lastSeq: Long = 0L,
    @SerialName("chat_id") val chatId: String = "",
    val channel: String = "",
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("oldest_id") val oldestId: Long = 0L,
)

@Serializable
data class UserChat(
    @SerialName("chat_id") val chatId: String = "",
    val channel: String = "",
    val label: String = "",
    @SerialName("work_dir") val workDir: String = "",
    @SerialName("last_active") val lastActive: String = "",
    val preview: String = "",
    @SerialName("is_current") val isCurrent: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
    val type: String = "",
    @SerialName("full_key") val fullKey: String = "",
    val role: String = "",
    val instance: String = "",
    @SerialName("parent_chat_id") val parentChatId: String = "",
    @SerialName("parent_channel") val parentChannel: String = "",
    val historical: Boolean = false,
    val running: Boolean = false,
    val status: String = "",
    val children: List<UserChat> = emptyList(),
)

@Serializable
data class SessionTreeResponse(
    val ok: Boolean = false,
    val sessions: List<UserChat> = emptyList(),
    @SerialName("orphan_subagents") val orphanSubAgents: List<UserChat> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("next_offset") val nextOffset: Int = 0,
)

@Serializable
data class ChatsResponse(
    val ok: Boolean = false,
    val chats: List<UserChat> = emptyList(),
    val sessions: List<UserChat> = emptyList(),
    @SerialName("orphan_subagents") val orphanSubAgents: List<UserChat> = emptyList(),
)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class AuthResponse(
    val ok: Boolean = false,
    val message: String = "",
    @SerialName("user_id") val userId: Int = 0,
)

@Serializable
data class AuthConfig(
    @SerialName("invite_only") val inviteOnly: Boolean = false,
    val bootstrap: Boolean = false,
)

@Serializable
data class SendMessageRequest(
    val content: String,
    @SerialName("chat_id") val chatId: String? = null,
    val channel: String? = null,
    @SerialName("upload_keys") val uploadKeys: List<String> = emptyList(),
    @SerialName("file_names") val fileNames: List<String> = emptyList(),
    @SerialName("file_sizes") val fileSizes: List<Long> = emptyList(),
    @SerialName("file_mimes") val fileMimes: List<String> = emptyList(),
    val id: String? = null,
    val interrupt: Boolean = false,
    val type: String = "message",
)

@Serializable
data class SendMessageResponse(
    @SerialName("chat_id") val chatId: String = "",
    val channel: String = "",
    @SerialName("message_id") val messageId: Long = 0L,
    val timestamp: Long = 0L,
    val queued: Boolean = false,
    @SerialName("turn_id") val turnId: Long = 0L,
    val interrupted: Boolean = false,
    val command: Boolean = false,
)

@Serializable
data class UploadResponse(
    @SerialName("upload_key") val uploadKey: String = "",
    val name: String = "",
    val size: Long = 0L,
    val mime: String = "",
)

@Serializable
data class SessionStatusResponse(
    @SerialName("token_usage") val tokenUsage: TokenUsage? = null,
    val cwd: String = "",
    val todos: List<TodoItem> = emptyList(),
)

@Serializable
data class RpcRequest(val method: String, val params: JsonElement? = null)

@Serializable
data class AskUserRequest(
    val channel: String? = null,
    @SerialName("chat_id") val chatId: String? = null,
    val answers: Map<String, String> = emptyMap(),
    val cancelled: Boolean = false,
)

@Serializable
data class CancelRequest(
    val channel: String? = null,
    @SerialName("chat_id") val chatId: String? = null,
)
