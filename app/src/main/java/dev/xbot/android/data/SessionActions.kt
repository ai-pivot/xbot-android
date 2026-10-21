package dev.xbot.android.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ForkResponse(
    @SerialName("chat_id") val chatId: String = "",
    val channel: String = "web",
)

@Serializable
data class RewindResponse(
    val ok: Boolean = false,
    @SerialName("target_history_id") val targetHistoryId: Long = 0L,
    val draft: String = "",
    // 服务端 protocol.HistoryRewindResult 里这两个字段都是 bool。
    // 曾误声明为 Int，导致回退成功、响应却在解析阶段抛
    // "Unexpected symbol 't' in numeric literal at path: $.data.files_rewound"。
    @SerialName("history_rewound") val historyRewound: Boolean = false,
    @SerialName("files_rewound") val filesRewound: Boolean = false,
    @SerialName("checkpoint_error") val checkpointError: String = "",
)
