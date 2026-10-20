package dev.xbot.android.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.xbot.android.XbotApp
import dev.xbot.android.chat.ChatViewModel
import dev.xbot.android.chat.IterationUi
import dev.xbot.android.chat.TurnUi
import dev.xbot.android.data.ModelEntry
import dev.xbot.android.data.SessionModel
import dev.xbot.android.ui.components.AskUserDialog
import dev.xbot.android.ui.components.AutoLinkText
import dev.xbot.android.ui.components.Composer
import dev.xbot.android.ui.components.CopyOrSelectText
import dev.xbot.android.ui.components.MarkdownText
import dev.xbot.android.ui.components.ModelPickerSheet
import dev.xbot.android.ui.components.RewindDialog
import dev.xbot.android.ui.components.ToolCard
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
    val listState = remember(chatId) { LazyListState() }

    // Auto-follow state:
    //  - follow=true  → keep the newest content pinned to the bottom
    //  - follow=false → the user scrolled away; never fight their gesture
    var follow by remember(chatId) { mutableStateOf(true) }
    var didInitialScroll by remember(chatId) { mutableStateOf(false) }

    var input by rememberSaveable(chatId) { mutableStateOf("") }
    var attachment by remember(chatId) { mutableStateOf<PendingAttachment?>(null) }
    var sending by remember(chatId) { mutableStateOf(false) }

    var sessionModel by remember(chatId) { mutableStateOf<SessionModel?>(null) }
    var availableModels by remember(chatId) { mutableStateOf<List<ModelEntry>>(emptyList()) }
    var modelsLoading by remember(chatId) { mutableStateOf(false) }
    var showModelPicker by remember(chatId) { mutableStateOf(false) }
    var showRewind by remember(chatId) { mutableStateOf(false) }

    LaunchedEffect(chatId, channel) {
        vm.open(chatId, channel, title)
        if (app.container.prefs.notificationsEnabled) {
            runCatching { dev.xbot.android.service.startNotificationService(context) }
        }
        modelsLoading = true
        runCatching {
            val session = app.container.api.getSessionModel(chatId, channel)
            val resolved = if (session.model.isBlank()) {
                val fallback = app.container.api.getDefaultModel()
                SessionModel(fallback.subId, fallback.model)
            } else {
                session
            }
            val entries = app.container.api.listModelEntries()
            sessionModel = resolved
            availableModels = entries
        }
        modelsLoading = false
    }

    LaunchedEffect(showModelPicker) {
        if (showModelPicker && availableModels.isEmpty() && !modelsLoading) {
            modelsLoading = true
            availableModels = runCatching {
                app.container.api.listModelEntries()
            }.getOrDefault(emptyList())
            modelsLoading = false
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
    // The optional "load more" row is also a LazyColumn item.
    val itemCount = state.turns.size + extraRows + if (state.hasMore) 1 else 0

    val canScrollBackward by remember {
        derivedStateOf { listState.canScrollBackward }
    }
    val canScrollForward by remember {
        derivedStateOf { listState.canScrollForward }
    }

    // Changes whenever the visible tail grows: streamed text, reasoning, tool
    // status/output, a new iteration/turn, or the optimistic pending message.
    val followVersion = (state.turns.size.toLong() * 1_000_000L) +
        (lastIteration?.content?.length ?: 0) +
        (lastIteration?.reasoning?.length ?: 0) +
        (lastTurn?.finalText?.length ?: 0) +
        (lastIteration?.tools?.hashCode() ?: 0) +
        (state.pendingUserText?.length ?: 0)

    // Entering a session must land directly at the newest message, without
    // animating through the whole history.
    LaunchedEffect(state.loading, itemCount) {
        if (!state.loading && itemCount > 0 && !didInitialScroll) {
            listState.scrollToBottom(itemCount)
            didInitialScroll = true
            follow = true
        }
    }

    // Follow streaming output only while the user has not scrolled away.
    LaunchedEffect(followVersion, follow, itemCount) {
        if (follow && !state.loading && itemCount > 0) {
            listState.scrollToBottom(itemCount)
        }
    }

    // A real drag means the user is taking over; stop following immediately.
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged) {
        if (isDragged && listState.canScrollForward) {
            follow = false
        }
    }

    // If the user returns to the bottom (by drag/fling or the bottom button),
    // resume following.
    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollForward }.collect { canForward ->
            if (!canForward) follow = true
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
                        TextButton(onClick = { showModelPicker = true }) {
                            Text(
                                sessionModel?.model?.take(18).orEmpty().ifBlank { "模型" },
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 120.dp),
                            )
                        }
                        IconButton(
                            onClick = { showRewind = true },
                            enabled = !state.busy && state.turns.any { it.userHistoryId > 0L },
                        ) {
                            Icon(Icons.Filled.History, contentDescription = "回退")
                        }
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
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 10.dp,
                            end = 12.dp,
                            bottom = 64.dp,
                        ),
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

            // Floating scroll controls: jump to the oldest message instantly,
            // or return to the newest one. Only visible when the list can move
            // in that direction, so they never cover idle content.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (canScrollBackward) {
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch { listState.scrollToItem(0) }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = "跳到顶部",
                        )
                    }
                }
                if (canScrollForward) {
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch { listState.scrollToBottom(itemCount) }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "回到底部",
                        )
                    }
                }
            }
        }
    }

    if (showModelPicker) {
        ModelPickerSheet(
            title = "选择模型",
            models = availableModels,
            currentSubId = sessionModel?.subscriptionId.orEmpty(),
            currentModel = sessionModel?.model.orEmpty(),
            loading = modelsLoading,
            onDismiss = { showModelPicker = false },
            onSelect = { entry ->
                showModelPicker = false
                scope.launch {
                    runCatching {
                        app.container.api.selectModel(chatId, channel, entry.subId, entry.model)
                    }.onSuccess {
                        sessionModel = SessionModel(entry.subId, entry.model)
                    }.onFailure {
                        snackbar.showSnackbar(it.message ?: "切换模型失败")
                    }
                }
            },
        )
    }

    if (showRewind) {
        RewindDialog(
            turns = state.turns,
            onDismiss = { showRewind = false },
            onConfirm = { historyId ->
                showRewind = false
                scope.launch {
                    runCatching {
                        app.container.api.rewindHistory(chatId, channel, historyId)
                    }.onSuccess { response ->
                        if (response.draft.isNotBlank()) input = response.draft
                        vm.reload()
                        snackbar.showSnackbar("已回退，原消息已放回输入框")
                    }.onFailure {
                        snackbar.showSnackbar(it.message ?: "回退失败")
                    }
                }
            },
        )
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

/**
 * LazyList `scrollToItem(last)` aligns the TOP of the last item. A turn can be
 * taller than the viewport (many iterations), so that is not the bottom of the
 * conversation — both "top" and "bottom" appeared to jump to the top for long
 * turns. Scroll by a large delta afterwards; LazyList clamps at the real end.
 */
private suspend fun androidx.compose.foundation.lazy.LazyListState.scrollToBottom(itemCount: Int) {
    if (itemCount <= 0) return
    scrollToItem(itemCount - 1)
    scrollBy(100_000f)
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
            CopyOrSelectText(text = turn.finalText) {
                MarkdownText(
                    text = turn.finalText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                )
            }
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
            CopyOrSelectText(text = iteration.content) {
                MarkdownText(
                    text = iteration.content,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                )
            }
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
                CopyOrSelectText(text = reasoning) {
                    Text(
                        reasoning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.SansSerif,
                    )
                }
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
            CopyOrSelectText(text = text) {
                AutoLinkText(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    linkColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
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
