package dev.xbot.android.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One selectable (subscription, model) pair returned by list_all_model_entries. */
@Serializable
data class ModelEntry(
    @SerialName("sub_id") val subId: String = "",
    @SerialName("sub_name") val subName: String = "",
    val model: String = "",
    val status: String = "",
    val vision: Boolean = false,
) {
    val selectable: Boolean get() = status != "disabled"
    val title: String get() = if (subName.isBlank()) model else "$subName · $model"
    val subtitle: String
        get() = buildList {
            if (status == "offline") add("未拉取")
            if (status == "disabled") add("已禁用")
            if (vision) add("视觉")
        }.joinToString(" · ")
}

/** The model currently bound to one session (get_session_subscription). */
@Serializable
data class SessionModel(
    @SerialName("subscription_id") val subscriptionId: String = "",
    val model: String = "",
)

/** The user-level default model (get_default_model). */
@Serializable
data class DefaultModel(
    @SerialName("sub_id") val subId: String = "",
    val model: String = "",
)

@Serializable
data class ToolSetting(
    val name: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    @SerialName("server_name") val serverName: String = "",
)

@Serializable
data class ToolSettingsResponse(
    val tools: List<ToolSetting> = emptyList(),
)

data class ModelSettingsSnapshot(
    val models: List<ModelEntry> = emptyList(),
    val defaultSubId: String = "",
    val defaultModel: String = "",
    val thinkingMode: String = "",
    val contextMode: String = "",
    val maxOutputTokens: Int = 0,
    val maxContextTokens: Int = 0,
    val compressionThreshold: String = "",
    val llmConcurrency: Int = 0,
    val tools: List<ToolSetting> = emptyList(),
)
