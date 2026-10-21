package dev.xbot.android.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.xbot.android.data.UserChat
import dev.xbot.android.update.UpdateViewModel
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    onOpenChat: (UserChat) -> Unit,
    onSettings: () -> Unit,
    onLogin: () -> Unit,
    vm: SessionsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val updateVm: UpdateViewModel = viewModel()
    val updateState by updateVm.state.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<UserChat?>(null) }
    var deleteTarget by remember { mutableStateOf<UserChat?>(null) }
    var forkTarget by remember { mutableStateOf<UserChat?>(null) }
    var createLabel by remember { mutableStateOf("") }
    var renameLabel by remember { mutableStateOf("") }
    var forkLabel by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        updateVm.autoCheck()
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            vm.start()
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                vm.stop()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("xbot", fontWeight = FontWeight.Bold)
                        Text(
                            state.serverUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (state.refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    IconButton(onClick = { vm.refresh(showLoading = true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新建会话")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (updateState.available || updateState.downloading || updateState.readyPath != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "发现新版本 ${updateState.manifest?.versionName.orEmpty()}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
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
                            )
                        }
                        updateState.error?.let { error ->
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        when {
                            updateState.readyPath != null -> TextButton(
                                onClick = updateVm::install,
                            ) { Text("安装更新") }

                            !updateState.downloading && !updateState.checking -> TextButton(
                                onClick = updateVm::download,
                            ) { Text("下载并安装") }
                        }
                        if (updateState.needInstallPermission) {
                            TextButton(onClick = updateVm::openInstallPermissionSettings) {
                                Text("去授权安装未知应用")
                            }
                        }
                    }
                }
            }

            if (!state.authenticated) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("登录已失效", fontWeight = FontWeight.SemiBold)
                        Text(
                            "xbot 的登录会话保存在服务端内存中，服务重启后需要重新登录。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onLogin) { Text("重新登录") }
                    }
                }
            }

            state.error?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            error,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = vm::clearError) { Text("知道了") }
                    }
                }
            }

            when {
                state.loading && state.sessions.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                    }
                }

                state.sessions.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("还没有会话", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "点右下角 + 创建第一个会话",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 4.dp,
                            bottom = 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.sessions, key = { "${it.channel}:${it.chatId}" }) { chat ->
                            SessionRow(
                                chat = chat,
                                onClick = { onOpenChat(chat) },
                                onRename = {
                                    renameTarget = chat
                                    renameLabel = chat.label
                                },
                                onFork = {
                                    forkTarget = chat
                                    forkLabel = "${chat.label.ifBlank { chat.chatId }} fork"
                                },
                                onDelete = { deleteTarget = chat },
                            )
                            chat.children.forEach { child ->
                                SessionRow(
                                    chat = child,
                                    child = true,
                                    onClick = { onOpenChat(child) },
                                    onRename = {
                                        renameTarget = child
                                        renameLabel = child.label
                                    },
                                    onFork = {
                                        forkTarget = child
                                        forkLabel = "${child.label.ifBlank { child.chatId }} fork"
                                    },
                                    onDelete = { deleteTarget = child },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建会话") },
            text = {
                OutlinedTextField(
                    value = createLabel,
                    onValueChange = { createLabel = it },
                    label = { Text("会话名称（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCreate = false
                        val label = createLabel
                        createLabel = ""
                        vm.createChat(label) { chatId ->
                            val created = UserChat(
                                chatId = chatId,
                                channel = "web",
                                label = label.ifBlank { chatId },
                            )
                            onOpenChat(created)
                        }
                    },
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("取消") }
            },
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = renameLabel,
                    onValueChange = { renameLabel = it },
                    label = { Text("名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val label = renameLabel
                        renameTarget = null
                        if (label.isNotBlank()) vm.renameChat(target, label)
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            },
        )
    }

    forkTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { forkTarget = null },
            title = { Text("Fork 会话") },
            text = {
                Column {
                    Text(
                        "将复制该会话的上下文到一个新会话。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = forkLabel,
                        onValueChange = { forkLabel = it },
                        label = { Text("新会话名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val label = forkLabel.trim()
                        forkTarget = null
                        if (label.isNotBlank()) {
                            vm.forkChat(target, label) { newId ->
                                onOpenChat(
                                    UserChat(
                                        chatId = newId,
                                        channel = target.channel.ifBlank { "web" },
                                        label = label,
                                    ),
                                )
                            }
                        }
                    },
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { forkTarget = null }) { Text("取消") }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除会话") },
            text = { Text("确定删除「${target.label.ifBlank { target.chatId }}」？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        vm.deleteChat(target)
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SessionRow(
    chat: UserChat,
    child: Boolean = false,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onFork: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (child) 24.dp else 0.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            chat.running -> Color(0xFF22C55E)
                            chat.status == "error" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.outline
                        },
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        chat.label.ifBlank { chat.chatId },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (chat.channel.isNotBlank() && chat.channel != "web") {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            chat.channel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (chat.preview.isNotBlank()) {
                    Text(
                        chat.preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    relativeTime(chat.lastActive),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = {
                            menu = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Fork 会话") },
                        onClick = {
                            menu = false
                            onFork()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

private fun relativeTime(raw: String): String {
    if (raw.isBlank()) return ""
    return runCatching {
        val instant = Instant.parse(raw)
        val diff = System.currentTimeMillis() - instant.toEpochMilli()
        when {
            diff < 60_000 -> "刚刚"
            diff < 3_600_000 -> "${diff / 60_000} 分钟前"
            diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
            diff < 7 * 86_400_000 -> "${diff / 86_400_000} 天前"
            else -> raw.take(10)
        }
    }.getOrDefault(raw.take(16).replace('T', ' '))
}
