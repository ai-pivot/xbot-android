package dev.xbot.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    busy: Boolean,
    attachmentName: String?,
    onPickAttachment: () -> Unit,
    onClearAttachment: () -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    sending: Boolean = false,
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            if (attachmentName != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text(attachmentName, maxLines = 1) },
                        trailingIcon = {
                            IconButton(
                                onClick = onClearAttachment,
                                modifier = Modifier.size(22.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "移除附件",
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onPickAttachment) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = "添加附件")
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    minLines = 1,
                    maxLines = 6,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f),
                )
                if (busy) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = "停止生成",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                IconButton(
                    onClick = onSend,
                    enabled = text.isNotBlank() && !sending,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = if (text.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                }
            }
        }
    }
}
