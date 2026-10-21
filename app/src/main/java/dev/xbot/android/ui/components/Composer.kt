package dev.xbot.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.xbot.android.chat.ContextBudgetUi

/**
 * 底部输入区。
 *
 * 高度按「一行文字」压缩过：`OutlinedTextField` 自带 56dp 的最小高度（装饰盒内固定），
 * 换成了 `BasicTextField` + 自己的圆角背景，行高约 36dp，整条输入区比原来矮 20dp 左右。
 */
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
    contextBudget: ContextBudgetUi? = null,
) {
    var showBudget by remember { mutableStateOf(false) }

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
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            if (attachmentName != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                attachmentName,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = onClearAttachment,
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "移除附件",
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        },
                    )
                }
            }
            ContextBudgetLabel(
                budget = contextBudget,
                onClick = { showBudget = true },
                modifier = Modifier.align(Alignment.End),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                CompactIconButton(
                    icon = Icons.Outlined.AttachFile,
                    description = "添加附件",
                    onClick = onPickAttachment,
                )
                CompactInput(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                )
                if (busy) {
                    CompactIconButton(
                        icon = Icons.Filled.Stop,
                        description = "停止生成",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = onCancel,
                    )
                }
                CompactIconButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    description = "发送",
                    enabled = text.isNotBlank() && !sending,
                    tint = if (text.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    onClick = onSend,
                )
            }
        }
    }

    if (showBudget) {
        contextBudget?.let { budget ->
            ContextBudgetDialog(budget = budget, onDismiss = { showBudget = false })
        }
    }
}

/** 单行起、最多 6 行自增长；最小高度约 36dp（原 `OutlinedTextField` 是 56dp）。 */
@Composable
private fun CompactInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = "发消息…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CompactIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
    }
}
