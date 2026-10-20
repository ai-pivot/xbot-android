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
    @SerialName("history_rewound") val historyRewound: Int = 0,
    @SerialName("files_rewound") val filesRewound: Int = 0,
    @SerialName("checkpoint_error") val checkpointError: String = "",
)
