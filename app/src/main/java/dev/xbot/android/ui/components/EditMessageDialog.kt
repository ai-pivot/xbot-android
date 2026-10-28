package dev.xbot.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

/**
 * 「编辑并重新发送」对话框。
 *
 * 交互顺序刻意反过来：**先让用户改完、点确定，才发起 rewind**。
 * 旧流程是先回退（等接口 + 重载历史）再把草稿塞回输入框，用户只能干等着 ——
 * 改到一半再点确定，感知上就没有那段空白。
 */
@Composable
fun EditMessageDialog(
    original: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onRewindOnly: (String) -> Unit,
) {
    var text by remember(original) { mutableStateOf(original) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // 打开即可输入，省掉一次点击。
        runCatching { focusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("编辑并重新发送") },
        text = {
            Column {
                Text(
                    "将从这条消息处回退：它和它之后的全部内容都会被删除。" +
                        "可以改完直接重发，也可以只回退、把文字放回输入框。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .focusRequester(focusRequester),
                    minLines = 3,
                    maxLines = 8,
                    enabled = !busy,
                    placeholder = { Text("要重新发送的内容") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = !busy && text.isNotBlank(),
            ) {
                Text(if (busy) "回退中…" else "确定并重新发送")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { onRewindOnly(text.trim()) },
                    enabled = !busy,
                ) {
                    Text("仅回退")
                }
                TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
            }
        },
    )
}
