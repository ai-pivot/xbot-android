package dev.xbot.android.ui.components

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString

/**
 * Chat-style text actions.
 *
 * Long-press opens a small menu with two choices:
 *  - 复制全部: copy the whole message (previous behavior)
 *  - 选择文本: switch this block into native text-selection mode, so the user
 *    can drag handles and use the system Copy action for a fragment.
 *
 * Selection mode is local to the block and exits via the "完成选择" chip.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CopyOrSelectText(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var selecting by remember(text) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    if (selecting) {
        Column(modifier = modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { selecting = false }) {
                    Text("完成选择", style = MaterialTheme.typography.labelSmall)
                }
            }
            SelectionContainer {
                content()
            }
        }
        return
    }

    Box(
        modifier = modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                menu = true
            },
        ),
    ) {
        content()
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("复制全部") },
                onClick = {
                    menu = false
                    clipboard.setText(AnnotatedString(text))
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                },
            )
            DropdownMenuItem(
                text = { Text("选择文本") },
                onClick = {
                    menu = false
                    selecting = true
                },
            )
        }
    }
}
