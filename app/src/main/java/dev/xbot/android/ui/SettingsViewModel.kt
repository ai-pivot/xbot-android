package dev.xbot.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.xbot.android.XbotApp
import dev.xbot.android.data.DefaultModel
import dev.xbot.android.data.ModelEntry
import dev.xbot.android.data.Subscription
import dev.xbot.android.data.ToolSetting
import dev.xbot.android.data.UnauthorizedException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val models: List<ModelEntry> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val defaultSubId: String = "",
    val defaultModel: String = "",
    val thinkingMode: String = "auto",
    val contextMode: String = "phase1",
    val maxOutputTokens: String = "",
    val maxContextTokens: String = "",
    val compressionThreshold: String = "",
    val llmConcurrency: String = "",
    val tools: List<ToolSetting> = emptyList(),
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as XbotApp).container
    private val api = container.api

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val models = withAuthRetry { api.listModelEntries() }
                val subscriptions = withAuthRetry { api.listSubscriptions() }
                val default = withAuthRetry { api.getDefaultModel() }
                val thinking = withAuthRetry { api.getThinkingMode() }
                val context = withAuthRetry { api.getContextMode() }
                val maxOutput = withAuthRetry { api.getMaxOutputTokens() }
                val maxContext = withAuthRetry { api.getMaxContextTokens() }
                val compression = withAuthRetry { api.getCompressionThreshold() }
                val concurrency = withAuthRetry { api.getLLMConcurrency() }
                val tools = withAuthRetry { api.getToolSettings() }

                _state.value = SettingsUiState(
                    loading = false,
                    saving = false,
                    error = null,
                    models = models,
                    subscriptions = subscriptions,
                    defaultSubId = default.subId,
                    defaultModel = default.model,
                    thinkingMode = normalizeThinking(thinking),
                    contextMode = context.ifBlank { "phase1" },
                    maxOutputTokens = maxOutput.takeIf { it > 0 }?.toString().orEmpty(),
                    maxContextTokens = maxContext.takeIf { it > 0 }?.toString().orEmpty(),
                    compressionThreshold = compression,
                    llmConcurrency = concurrency.takeIf { it > 0 }?.toString().orEmpty(),
                    tools = tools,
                )
            } catch (e: UnauthorizedException) {
                _state.update { it.copy(loading = false, error = "登录已失效，请重新登录") }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载设置失败") }
            }
        }
    }

    fun selectDefaultModel(entry: ModelEntry) {
        save {
            api.setDefaultModel(entry.subId, entry.model)
            _state.update {
                it.copy(defaultSubId = entry.subId, defaultModel = entry.model)
            }
        }
    }

    fun setThinkingMode(mode: String) {
        save {
            val dbMode = when (mode) {
                "auto" -> ""
                "think" -> "enabled"
                else -> mode
            }
            api.setThinkingMode(dbMode)
            _state.update { it.copy(thinkingMode = normalizeThinking(dbMode)) }
        }
    }

    fun setContextMode(mode: String) {
        save {
            api.setContextMode(mode)
            _state.update { it.copy(contextMode = mode) }
        }
    }

    fun updateMaxOutputText(value: String) {
        _state.update { it.copy(maxOutputTokens = value.filter(Char::isDigit)) }
    }

    fun applyMaxOutput() {
        val value = _state.value.maxOutputTokens.toIntOrNull() ?: return
        save {
            api.setMaxOutputTokens(value)
        }
    }

    fun updateMaxContextText(value: String) {
        _state.update { it.copy(maxContextTokens = value.filter(Char::isDigit)) }
    }

    fun applyMaxContext() {
        val value = _state.value.maxContextTokens.toIntOrNull() ?: return
        save {
            api.setMaxContextTokens(value)
        }
    }

    fun updateCompressionText(value: String) {
        _state.update { it.copy(compressionThreshold = value) }
    }

    fun applyCompression() {
        val value = _state.value.compressionThreshold.toDoubleOrNull() ?: return
        save {
            api.setCompressionThreshold(value)
        }
    }

    fun updateConcurrencyText(value: String) {
        _state.update { it.copy(llmConcurrency = value.filter(Char::isDigit)) }
    }

    fun applyConcurrency() {
        val value = _state.value.llmConcurrency.toIntOrNull() ?: return
        save {
            api.setLLMConcurrency(value)
        }
    }

    fun toggleTool(tool: ToolSetting, enabled: Boolean) {
        save {
            api.setToolEnabled(tool.name, enabled)
            _state.update { current ->
                current.copy(
                    tools = current.tools.map {
                        if (it.name == tool.name) it.copy(enabled = enabled) else it
                    },
                )
            }
        }
    }

    fun toggleSubscription(sub: Subscription, enabled: Boolean) {
        save {
            api.setSubscriptionEnabled(sub.id, enabled)
            _state.update { current ->
                current.copy(
                    subscriptions = current.subscriptions.map {
                        if (it.id == sub.id) it.copy(enabled = enabled) else it
                    },
                )
            }
        }
    }

    fun toggleModel(entry: ModelEntry, enabled: Boolean) {
        save {
            api.setModelEnabled(entry.subId, entry.model, enabled)
            _state.update { current ->
                current.copy(
                    models = current.models.map {
                        if (it.subId == entry.subId && it.model == entry.model) {
                            it.copy(status = if (enabled) "normal" else "disabled")
                        } else {
                            it
                        }
                    },
                )
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                withAuthRetry { block() }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "保存失败") }
            } finally {
                _state.update { it.copy(saving = false) }
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

    private fun normalizeThinking(value: String): String = when (value) {
        "", "auto" -> "auto"
        "think", "enabled" -> "think"
        "think-max" -> "think-max"
        "disabled" -> "disabled"
        else -> value
    }
}
