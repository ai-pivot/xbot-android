package dev.xbot.android.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.xbot.android.XbotApp
import dev.xbot.android.chat.ChatViewModel
import dev.xbot.android.chat.IterationUi
import dev.xbot.android.chat.TurnUi
import dev.xbot.android.ui.components.AskUserDialog
import dev.xbot.android.ui.components.AutoLinkText
import dev.xbot.android.ui.components.Composer
import dev.xbot.android.ui.components.MarkdownText
import dev.xbot.android.ui.components.ToolCard
import dev.xbot.android.ui.components.copyOnLongPress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private data class PendingAttachment(
    val uri: Uri,
    val name: String,
    val mime: String,
    val size: Long,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    channel: String,
    title: String,
    onBack: () -> Unit,
    vm: ChatViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as XbotApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var input by rememberSaveable(chatId) { mutableStateOf("") }
    var attachment by remember(chatId) { mutableStateOf<PendingAttachment?>(null) }
    var sending by remember(chatId) { mutableStateOf(false) }

    LaunchedEffect(chatId, channel) {
        vm.open(chatId, channel, title)
        if (app.container.prefs.notificationsEnabled) {
            runCatching { dev.xbot.android.service.startNotificationService(context) }
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        var name = "attachment"
        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
            }
        }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        attachment = PendingAttachment(uri, name, mime, size)
    }

    fun submit() {
        val text = input.trim()
        val file = attachment
        if (text.isEmpty() && file == null) return
        sending = true
        scope.launch {
            try {
                var uploadKeys = emptyList<String>()
                var names = emptyList<String>()
                var sizes = emptyList<Long>()
                var mimes = emptyList<String>()
                if (file != null) {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
                    } ?: throw IllegalStateException("无法读取附件")
                    val uploaded = app.container.api.uploadFile(bytes, file.name, file.mime)
                    uploadKeys = listOf(uploaded.uploadKey)
                    names = listOf(file.name)
                    sizes = listOf(file.size)
                    mimes = listOf(file.mime)
                }
                vm.send(
                    text = text,
                    uploadKeys = uploadKeys,
                    fileNames = names,
                    fileSizes = sizes,
                    fileMimes = mimes,
                )
                input = ""
                attachment = null
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "发送失败")
            } finally {
                sending = false
            }
        }
    }

    val lastTurn = state.turns.lastOrNull()
    val lastIteration = lastTurn?.iterations?.lastOrNull()
    val showThinking = state.busy && (
        lastIteration == null ||
            lastIteration.done ||
            (lastIteration.content.isBlank() && lastIteration.tools.isEmpty())
        )
    val extraRows = (if (state.pendingUserText != null) 1 else 0) +
        (if (showThinking) 1 else 0)
    val totalRows = state.turns.size + extraRows

    val stickToBottom by remember {
        derivedStateOf { !listState.canScrollForward }
    }
    val lastContentLength = lastIteration?.content?.length ?: 0
    LaunchedEffect(totalRows, lastContentLength, state.pendingUserText) {
        if (totalRows > 0 && stickToBottom) {
            listState.animateScrollToItem(totalRows - 1)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                state.title.ifBlank { title.ifBlank { chatId } },
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (state.connected) Color(0xFF22C55E) else Color(0xFFF59E0B),
                                        ),
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    when {
                                        state.busy -> "运行中"
                                        state.connected -> "已连接"
                                        else -> "重连中…"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = vm::reload) {
                            Icon(Icons.Filled.Refresh, contentDescription = "重新加载")
                        }
                    },
                )
                if (state.loading || !state.connected) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        bottomBar = {
            Composer(
                text = input,
                onTextChange = { input = it },
                busy = state.busy,
                attachmentName = attachment?.name,
                onPickAttachment = { picker.launch("*/*") },
                onClearAttachment = { attachment = null },
                onSend = { submit() },
                onCancel = vm::cancel,
                sending = sending,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading && state.turns.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.turns.isEmpty() && state.pendingUserText == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "x",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("开始对话", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "消息会通过现有 xbot 服务端处理，支持工具调用与流式输出。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (state.hasMore) {
                            item(key = "load_more") {
                                TextButton(
                                    onClick = vm::loadMore,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("加载更早的消息") }
                            }
                        }
                        items(state.turns, key = { it.turnId }) { turn ->
                            TurnBlock(turn)
                        }
                        state.pendingUserText?.let { pending ->
                            item(key = "pending_user") {
                                UserBubble(pending)
                            }
                        }
                        if (showThinking) {
                            item(key = "thinking") {
                                ThinkingRow()
                            }
                        }
                    }
                }
            }
        }
    }

    state.askUser?.let { prompt ->
        AskUserDialog(
            prompt = prompt,
            onRespond = { answers, cancelled ->
                vm.respondAskUser(answers, cancelled)
            },
            onDismiss = vm::dismissAskUser,
        )
    }
}

@Composable
private fun TurnBlock(turn: TurnUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (turn.userText.isNotBlank()) {
            UserBubble(turn.userText)
        }
        turn.iterations.forEach { iteration ->
            IterationBlock(iteration)
        }
        val lastContent = turn.iterations.lastOrNull()?.content.orEmpty()
        if (turn.finalText.isNotBlank() && turn.finalText != lastContent) {
            MarkdownText(
                text = turn.finalText,
                modifier = Modifier
                    .fillMaxWidth()
                    .copyOnLongPress(turn.finalText)
                    .padding(horizontal = 4.dp),
            )
        }
        if (turn.cancelled) {
            Text(
                "已取消",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun IterationBlock(iteration: IterationUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (iteration.reasoning.isNotBlank()) {
            ReasoningBlock(iteration.reasoning)
        }
        if (iteration.tools.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                iteration.tools.forEach { tool ->
                    ToolCard(tool)
                }
            }
        }
        if (iteration.content.isNotBlank()) {
            MarkdownText(
                text = iteration.content,
                modifier = Modifier
                    .fillMaxWidth()
                    .copyOnLongPress(iteration.content)
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun ReasoningBlock(reasoning: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "思考 ${reasoning.length} 字",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                Text(
                    reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.copyOnLongPress(reasoning),
                )
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            modifier = Modifier.fillMaxWidth(0.86f),
        ) {
            AutoLinkText(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                linkColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .copyOnLongPress(text)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun ThinkingRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
        )
        Text(
            "思考中…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
