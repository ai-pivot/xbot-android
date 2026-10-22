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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
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
import dev.xbot.android.push.PushRegistrar
import dev.xbot.android.update.UpdateViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenModels: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as XbotApp
    val prefs = app.container.prefs
    val api = app.container.api
    val scope = rememberCoroutineScope()
    val chatViewModel: ChatViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()
    val settings by settingsVm.state.collectAsStateWithLifecycle()
    val updateVm: UpdateViewModel = viewModel()
    val updateState by updateVm.state.collectAsStateWithLifecycle()
    var updateUrl by remember { mutableStateOf(prefs.updateManifestUrl) }
    var mediaBase by remember { mutableStateOf(prefs.mediaBaseUrl) }

    LaunchedEffect(Unit) { settingsVm.load() }

    var fcmToken by remember { mutableStateOf(prefs.fcmToken) }
    val clipboard = LocalClipboardManager.current
    var showLogout by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var notificationsAllowed by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsAllowed = granted
        if (!granted) message = "通知权限被拒绝，收不到后台推送。"
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
                OutlinedTextField(
                    value = mediaBase,
                    onValueChange = {
                        mediaBase = it
                        prefs.mediaBaseUrl = it
                    },
                    label = { Text("媒体/对象存储地址") },
                    placeholder = { Text("https://minio.cjiw.site") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "用于把 localhost/127.0.0.1 图片地址重写到公网对象存储；留空则不重写。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsCard(title = "模型管理") {
                val defaultEntry = settings.models.firstOrNull {
                    it.subId == settings.defaultSubId && it.model == settings.defaultModel
                }
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
                            defaultEntry?.title
                                ?: settings.defaultModel.ifBlank { "未设置" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${settings.subscriptions.size} 个订阅 · ${settings.models.size} 个模型",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onOpenModels) { Text("管理") }
                }
            }

            SettingsCard(
                title = "推理设置",
                collapsible = true,
                initiallyExpanded = false,
            ) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("内置工具", style = MaterialTheme.typography.bodyMedium)
                        val enabledCount = settings.tools.count { it.enabled }
                        Text(
                            if (settings.tools.isEmpty()) {
                                "暂无可配置工具"
                            } else {
                                "$enabledCount / ${settings.tools.size} 已启用"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onOpenTools) { Text("管理") }
                }
            }

            SettingsCard(title = "通知权限") {
                Text(
                    if (notificationsAllowed) {
                        "已允许 xbot 发送通知。"
                    } else {
                        "未允许通知，收不到后台推送。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!notificationsAllowed) {
                        TextButton(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                        ) { Text("开启通知权限") }
                    }
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    ) { Text("电池优化设置") }
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
            }

            // FCM data push: the server pushes ask_user / turn-complete events;
            // this device registers its token and renders the notification.
            SettingsCard(title = "后台推送（FCM）") {
                Text(
                    when {
                        fcmToken.isBlank() ->
                            "尚未获取到 FCM token。若手机没有 Google Play 服务，后台推送不可用。"
                        prefs.pushRegistered ->
                            "已注册到服务端，后台会通过 FCM 接收提问和完成通知。"
                        else ->
                            "已获取 token，但尚未注册到服务端；登录后会自动重试。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            fcmToken = prefs.fcmToken
                            PushRegistrar.onAppStart(app)
                        },
                    ) { Text("刷新/重新注册") }
                    TextButton(
                        enabled = fcmToken.isNotBlank(),
                        onClick = { clipboard.setText(AnnotatedString(fcmToken)) },
                    ) { Text("复制 token") }
                    TextButton(
                        enabled = fcmToken.isNotBlank(),
                        onClick = {
                            scope.launch {
                                val result = runCatching { PushRegistrar.sendTest(app) }
                                message = result.fold(
                                    onSuccess = { "已发送测试通知（$it 个目标）" },
                                    onFailure = { "发送失败：${it.message}" },
                                )
                            }
                        },
                    ) { Text("发送测试通知") }
                }
                if (fcmToken.isNotBlank()) {
                    Text(
                        fcmToken,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Text(
                    "收不到时检查：系统通知权限、电池优化白名单、以及服务端是否已启用 push。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsCard(title = "更新") {
                InfoRow(
                    "当前版本",
                    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                )
                OutlinedTextField(
                    value = updateUrl,
                    onValueChange = { updateUrl = it },
                    label = { Text("更新清单地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            updateVm.setManifestUrl(updateUrl)
                            updateVm.check(manual = true)
                        },
                        enabled = !updateState.checking && !updateState.downloading,
                    ) {
                        Text(if (updateState.checking) "检查中…" else "检查更新")
                    }
                    if (updateState.checking) {
                        LinearProgressIndicator(modifier = Modifier.weight(1f))
                    }
                }
                updateState.error?.let { error ->
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                updateState.note?.let { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val manifest = updateState.manifest
                if (manifest != null) {
                    if (updateState.available) {
                        Text(
                            "发现新版本 ${manifest.versionName} (${manifest.versionCode})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (manifest.changelog.isNotBlank()) {
                            Text(
                                manifest.changelog,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (updateState.downloading) {
                            LinearProgressIndicator(
                                progress = { updateState.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "下载中 ${(updateState.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        if (updateState.verifying) {
                            Text(
                                "正在校验安装包…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        when {
                            updateState.readyPath != null -> TextButton(
                                onClick = updateVm::install,
                            ) { Text("安装更新") }

                            !updateState.downloading && !updateState.verifying -> TextButton(
                                onClick = updateVm::download,
                            ) { Text("下载更新") }
                        }
                    } else if (updateState.upToDate) {
                        Text(
                            "已是最新版本",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (updateState.needInstallPermission) {
                    Text(
                        "需要允许安装未知应用才能完成更新。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = updateVm::openInstallPermissionSettings) {
                        Text("去授权")
                    }
                }
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
                            PushRegistrar.onLogout(app)
                            runCatching { api.logout() }
                            prefs.clearSession()
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
    collapsible: Boolean = false,
    initiallyExpanded: Boolean = true,
    content: @Composable () -> Unit,
) {
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
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
            val headerModifier = if (collapsible) {
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            } else {
                Modifier.fillMaxWidth()
            }
            Row(
                modifier = headerModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (collapsible) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            if (!collapsible || expanded) {
                content()
            }
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
