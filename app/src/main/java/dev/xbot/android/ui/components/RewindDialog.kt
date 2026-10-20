package dev.xbot.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.xbot.android.chat.TurnUi

/**
 * Rewind target picker. The rewind API needs a session_messages history id, so
 * only turns that came from persisted history are offered (live turns have 0).
 */
@Composable
fun RewindDialog(
    turns: List<TurnUi>,
    onDismiss: () -> Unit,
    onConfirm: (historyId: Long) -> Unit,
) {
    val candidates = remember(turns) {
        turns.filter { it.userHistoryId > 0L && it.userText.isNotBlank() }
    }
    var selected by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("回退到某条消息") },
        text = {
            if (candidates.isEmpty()) {
                Text("没有可回退的历史消息。")
            } else {
                Column {
                    Text(
                        "将删除该消息之后的全部消息与迭代，并把这条消息放回输入框。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .padding(top = 8.dp),
                    ) {
                        items(candidates, key = { it.turnId }) { turn ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selected = turn.userHistoryId }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selected == turn.userHistoryId,
                                    onClick = { selected = turn.userHistoryId },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        turn.userText.replace('\n', ' '),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "Turn ${turn.turnId}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let(onConfirm) },
                enabled = selected != null,
            ) { Text("回退") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
