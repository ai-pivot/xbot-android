package dev.xbot.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.xbot.android.BuildConfig
import dev.xbot.android.XbotApp
import dev.xbot.android.chat.ChatViewModel
import dev.xbot.android.service.startNotificationService
import dev.xbot.android.service.stopNotificationService
import dev.xbot.android.ui.components.ModelPickerSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as XbotApp
    val prefs = app.container.prefs
    val api = app.container.api
    val scope = rememberCoroutineScope()
    val chatViewModel: ChatViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()
    val settings by settingsVm.state.collectAsStateWithLifecycle()
    var showDefaultModelPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { settingsVm.load() }

    var notifications by remember { mutableStateOf(prefs.notificationsEnabled) }
    var showLogout by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun enableNotifications() {
        if (prefs.activeChatId.isBlank()) {
            message = "请先打开一个会话，再开启通知。"
            notifications = false
            prefs.notificationsEnabled = false
            return
        }
        prefs.notificationsEnabled = true
        notifications = true
        runCatching { startNotificationService(context) }
            .onFailure { message = it.message ?: "无法启动通知服务" }
    }

    fun disableNotifications() {
        prefs.notificationsEnabled = false
        notifications = false
        runCatching { stopNotificationService(context) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            enableNotifications()
        } else {
            notifications = false
            prefs.notificationsEnabled = false
            message = "通知权限被拒绝，无法显示完成提醒。"
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        if (!enabled) {
            disableNotifications()
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enableNotifications()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("设置", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (settings.loading || settings.saving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            settings.error?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            SettingsCard(title = "服务器") {
                InfoRow("地址", prefs.serverUrl)
                InfoRow("账号", prefs.secrets.username().ifBlank { "未登录" })
                InfoRow("当前会话", prefs.activeChatId.ifBlank { "未选择" })
                InfoRow("通道", prefs.activeChannel)
            }

            SettingsCard(title = "模型与推理") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "默认模型",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            settings.defaultModel.ifBlank { "未设置" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = { showDefaultModelPicker = true }) {
                        Text("选择")
                    }
                }

                SettingChoiceRow(
                    label = "思考模式",
                    options = listOf(
                        "auto" to "自动",
                        "think" to "开启",
                        "think-max" to "最大",
                        "disabled" to "关闭",
                    ),
                    current = settings.thinkingMode,
                    onSelect = settingsVm::setThinkingMode,
                )

                SettingChoiceRow(
                    label = "上下文模式",
                    options = listOf(
                        "phase1" to "自动压缩",
                        "none" to "不压缩",
                    ),
                    current = settings.contextMode,
                    onSelect = settingsVm::setContextMode,
                )

                NumberSettingRow(
                    label = "最大输出 tokens",
                    value = settings.maxOutputTokens,
                    onValueChange = settingsVm::updateMaxOutputText,
                    onApply = settingsVm::applyMaxOutput,
                )
                NumberSettingRow(
                    label = "最大上下文 tokens",
                    value = settings.maxContextTokens,
                    onValueChange = settingsVm::updateMaxContextText,
                    onApply = settingsVm::applyMaxContext,
                )
                NumberSettingRow(
                    label = "压缩阈值（0–1）",
                    value = settings.compressionThreshold,
                    onValueChange = settingsVm::updateCompressionText,
                    onApply = settingsVm::applyCompression,
                    decimal = true,
                )
                NumberSettingRow(
                    label = "LLM 并发",
                    value = settings.llmConcurrency,
                    onValueChange = settingsVm::updateConcurrencyText,
                    onApply = settingsVm::applyConcurrency,
                )
            }

            SettingsCard(title = "工具") {
                if (settings.tools.isEmpty()) {
                    Text(
                        "没有可配置的内置工具。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    settings.tools.forEach { tool ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(tool.name, style = MaterialTheme.typography.bodyMedium)
                                if (tool.description.isNotBlank()) {
                                    Text(
                                        tool.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Switch(
                                checked = tool.enabled,
                                onCheckedChange = { settingsVm.toggleTool(tool, it) },
                            )
                        }
                    }
                }
            }

            SettingsCard(title = "通知") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("后台会话通知", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "通过前台服务维持一条 SSE 连接，在会话完成或 Agent 提问时通知。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = notifications, onCheckedChange = ::toggleNotifications)
                }
                Text(
                    "说明：xbot 服务端没有系统级推送通道，实时通知依赖这条常驻连接。" +
                        "系统省电策略或厂商后台限制仍可能中断它。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                ) { Text("打开电池优化设置") }
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                ) { Text("应用权限设置") }
            }

            SettingsCard(title = "关于") {
                InfoRow("客户端版本", BuildConfig.VERSION_NAME)
                Text(
                    "纯原生 Android 客户端，通过现有 xbot REST + RPC + SSE 接口接入，不修改服务端。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { showLogout = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("退出登录", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDefaultModelPicker) {
        ModelPickerSheet(
            title = "选择默认模型",
            models = settings.models,
            currentSubId = settings.defaultSubId,
            currentModel = settings.defaultModel,
            loading = settings.loading,
            onDismiss = { showDefaultModelPicker = false },
            onSelect = { entry ->
                showDefaultModelPicker = false
                settingsVm.selectDefaultModel(entry)
            },
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("提示") },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { message = null }) { Text("好") }
            },
        )
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("退出登录") },
            text = { Text("退出后需要重新输入服务器地址和账号密码。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogout = false
                        scope.launch {
                            runCatching { api.logout() }
                            prefs.notificationsEnabled = false
                            prefs.clearSession()
                            runCatching { stopNotificationService(context) }
                            chatViewModel.reset()
                            onLogout()
                        }
                    },
                ) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.65f),
        )
    }
}

@Composable
private fun SettingChoiceRow(
    label: String,
    options: List<Pair<String, String>>,
    current: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, title) ->
                FilterChip(
                    selected = current == value,
                    onClick = { onSelect(value) },
                    label = { Text(title) },
                )
            }
        }
    }
}

@Composable
private fun NumberSettingRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onApply: () -> Unit,
    decimal: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onApply) { Text("保存") }
    }
}
