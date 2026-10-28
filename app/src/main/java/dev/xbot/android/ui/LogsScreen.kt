package dev.xbot.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xbot.android.XbotApp
import dev.xbot.android.logs.AppLog
import dev.xbot.android.logs.LogBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 端侧日志页：实时看最近 [AppLog.DEFAULT_CAPACITY] 条、复制、打包成 zip、直接上传到服务器。
 *
 * 排查任何问题都从这里取信息，不要再往具体页面里塞一次性诊断。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val api = (context.applicationContext as XbotApp).container.api
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // revision 变化即重读快照（AppLog 每写一条都会自增）。
    val revision by AppLog.revision.collectAsStateWithLifecycle()
    val lines = remember(revision) { AppLog.snapshotLines() }

    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("日志", style = MaterialTheme.typography.titleMedium) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(AppLog.snapshot()))
                        status = "已复制 ${lines.size} 行日志"
                    },
                ) { Text("复制") }

                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) { LogBundle.packageZip(context) }
                            }.onSuccess {
                                status = "已打包 ${it.file.name}（${it.entries} 项）\n${it.file.absolutePath}"
                            }.onFailure {
                                status = "打包失败：${it.message}"
                            }
                            busy = false
                        }
                    },
                ) { Text("打包") }

                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        status = "正在上传…"
                        scope.launch {
                            runCatching { LogBundle.upload(context, api) }
                                .onSuccess {
                                    status = "已上传（${it.size / 1024}KB）\nkey: ${it.uploadKey}\n${it.url}"
                                }
                                .onFailure {
                                    status = "上传失败：${it.message}"
                                }
                            busy = false
                        }
                    },
                ) { Text("上传") }

                TextButton(
                    onClick = {
                        AppLog.clear()
                        status = "已清空"
                    },
                ) { Text("清空") }
            }

            status?.let { text ->
                SelectionContainer {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }

            HorizontalDivider()

            if (lines.isEmpty()) {
                Text(
                    "还没有日志",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    items(lines.size) { index ->
                        Text(
                            lines[index],
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
