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
import androidx.compose.runtime.rememberUpdatedState
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
import dev.xbot.android.data.RewindResponse
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private data class PendingAttachment(
    val uri: Uri,
    val name: String,
    val mime: String,
    val size: Long,
)

private data class PrependAnchor(
    val turnId: Long,
    val offset: Int,
    val requestedOldestId: Long,
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
    var didInitialBackfill by remember(chatId) { mutableStateOf(false) }

    // One gesture = at most one older-history request. `consumed` resets when a
    // new scroll gesture starts; it is independent from the short-list backfill.
    var loadMoreGestureConsumed by remember(chatId) { mutableStateOf(false) }
    var pendingAnchor by remember(chatId) { mutableStateOf<PrependAnchor?>(null) }
    val latestState by rememberUpdatedState(state)

    fun requestLoadMore() {
        val s = latestState
        if (!s.hasMore || s.loading || s.loadingMore || s.oldestId <= 0L) return
        loadMoreGestureConsumed = true
        val offsetRow = if (s.hasMore) 1 else 0
        val turnIndex = (listState.firstVisibleItemIndex - offsetRow).coerceAtLeast(0)
        val anchorTurn = s.turns.getOrNull(turnIndex)
        pendingAnchor = anchorTurn?.let {
            PrependAnchor(
                turnId = it.turnId,
                offset = listState.firstVisibleItemScrollOffset,
                requestedOldestId = s.oldestId,
            )
        }
        vm.loadMore()
    }

    LaunchedEffect(listState, chatId) {
        var wasScrolling = false
        snapshotFlow { listState.isScrollInProgress to listState.firstVisibleItemIndex }
            .collect { (scrolling, index) ->
                if (scrolling && !wasScrolling) {
                    // A new drag/fling starts: allow one request for it.
                    loadMoreGestureConsumed = false
                }
                wasScrolling = scrolling
                if (!loadMoreGestureConsumed && index <= 2) {
                    requestLoadMore()
                }
            }
    }

    // Short first page: wait until the LazyColumn has actually been laid out,
    // then only backfill when the content cannot scroll in EITHER direction.
    // `!canScrollForward` alone is also true at the bottom of a long list, which
    // used to consume the gesture slot and make later upward scrolls do nothing.
    LaunchedEffect(state.loading, state.hasMore, state.oldestId) {
        if (state.loading || !state.hasMore || state.loadingMore || didInitialBackfill) {
            return@LaunchedEffect
        }
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .first { it > 0 }
        if (!listState.canScrollBackward && !listState.canScrollForward) {
            didInitialBackfill = true
            requestLoadMore()
        }
    }

    LaunchedEffect(state.loadingMore, state.loadMoreError, state.oldestId) {
        val anchor = pendingAnchor ?: return@LaunchedEffect
        if (state.loadingMore) return@LaunchedEffect
        if (state.loadMoreError != null) {
            pendingAnchor = null
            return@LaunchedEffect
        }
        if (state.oldestId == anchor.requestedOldestId) {
            if (!state.hasMore) pendingAnchor = null
            return@LaunchedEffect
        }
        val index = state.turns.indexOfFirst { it.turnId == anchor.turnId }
        if (index >= 0) {
            val offsetRow = if (state.hasMore) 1 else 0
            listState.scrollToItem(index + offsetRow, anchor.offset)
        }
        pendingAnchor = null
    }

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
    val currentModelEntry = availableModels.firstOrNull {
        it.subId == sessionModel?.subscriptionId && it.model == sessionModel?.model
    }

    val canScrollBackward by remember {
        derivedStateOf { listState.canScrollBackward }
    }
    val canScrollForward by remember {
        derivedStateOf { listState.canScrollForward }
    }

    // Changes only when the *tail* of the conversation grows: streamed text,
    // reasoning, tool status/output, a new iteration/turn, or the optimistic
    // pending message. Prepending an older page must NOT change it, otherwise
    // "load more" yanks the list back to the bottom (see ChatScroll.kt).
    val tail = state.tailFingerprint(showThinking)

    // Entering a session must land directly at the newest message, without
    // animating through the whole history.
    LaunchedEffect(state.loading, itemCount) {
        if (!state.loading && itemCount > 0 && !didInitialScroll) {
            listState.scrollToBottom(itemCount)
            didInitialScroll = true
            follow = true
        }
    }

    // Follow the tail only while the user has not taken over the list. Keyed on
    // `tail` (never on `itemCount`): prepending an older page grows the list but
    // not the tail, and must not scroll anything.
    LaunchedEffect(follow, tail, state.loading) {
        if (!shouldPinToBottom(follow, state.loading, itemCount, listState.isScrollInProgress)) {
            return@LaunchedEffect
        }
        // Let an in-flight drag/fling settle first, otherwise a programmatic
        // scroll fights the gesture and the position jumps.
        snapshotFlow { listState.isScrollInProgress }.first { !it }
        listState.scrollToBottom(itemCount)
    }

    // A real drag means the user is taking over; stop following immediately,
    // even when the list cannot scroll forward yet (that is exactly the state at
    // the bottom, where every upward drag starts).
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged) {
        if (isDragged) follow = followAfterUserScroll()
    }

    // Resume following only once the list has settled at the very bottom
    // (by drag/fling or the bottom button) — never mid-list.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canForward) ->
                follow = followAfterSettle(
                    follow = follow,
                    atBottom = !scrolling && !canForward,
                )
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
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                state.title.ifBlank { title.ifBlank { chatId } },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // 副标题：状态点 + 模型名（点击切换模型）。整行单行布局，
                            // 模型名可省略，避免像过去那样把标题挤到只剩一个字。
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { showModelPicker = true }
                                    .padding(vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
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
                                    maxLines = 1,
                                    softWrap = false,
                                )
                                Text(
                                    " · ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                                Text(
                                    currentModelEntry?.title
                                        ?: sessionModel?.model.orEmpty().ifBlank { "选择模型" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    actions = {
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
                contextBudget = state.contextBudget,
                cwd = state.cwd,
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
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    when {
                                        state.loadingMore -> CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                        )

                                        state.loadMoreError != null -> TextButton(
                                            onClick = { requestLoadMore() },
                                        ) {
                                            Text("加载失败，点击重试")
                                        }

                                        else -> if (!listState.canScrollForward) {
                                            TextButton(
                                                onClick = { requestLoadMore() },
                                            ) { Text("加载更早消息") }
                                        } else {
                                            Text(
                                                "上滑加载更早消息",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
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
                            // 明确要离开底部：不然流式输出会立刻把人拽回去。
                            follow = false
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
                        // 回退是「删掉」历史，必须整体替换，合并会把已回退的 turn 留在屏幕上。
                        vm.reload(replaceTurns = true)
                        snackbar.showSnackbar(rewindMessage(response))
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

/**
 * 回退结果文案。历史与文件快照是两件独立的事：
 * `history_rewound` 为 false 表示历史没截断（回退实际没生效），
 * `files_rewound` 为 false 但 `checkpoint_error` 非空表示历史回退了、文件没恢复。
 * 这两件事都不能用一句"已回退"盖掉。
 */
private fun rewindMessage(response: RewindResponse): String {
    if (!response.historyRewound) {
        val reason = response.checkpointError.ifBlank { "服务端未截断历史" }
        return "回退未生效：$reason"
    }
    val draftNote = if (response.draft.isNotBlank()) "，原消息已放回输入框" else ""
    return when {
        response.filesRewound -> "已回退，文件改动已一并恢复$draftNote"
        response.checkpointError.isNotBlank() ->
            "已回退$draftNote（文件未回退：${response.checkpointError}）"
        else -> "已回退$draftNote"
    }
}
