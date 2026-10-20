package dev.xbot.android.ui.components

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString

/**
 * Chat-style copy affordance: long-press on a message copies its plain text and
 * gives haptic + toast feedback. Taps are left to child text (link annotations
 * inside Markdown still receive their own click).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.copyOnLongPress(
    text: String,
    toast: String = "已复制",
): Modifier {
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    return if (text.isBlank()) {
        this
    } else {
        this.combinedClickable(
            onClick = {},
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                clipboard.setText(AnnotatedString(text))
                Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
            },
        )
    }
}
