package dev.xbot.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.xbot.android.chat.AskUserUi

/**
 * AskUser prompt. Answers are keyed by 1-based question index, matching the
 * web client (`answers["1"] = ...`). Multi-select values are joined with "、" —
 * the same separator the web AskUserPanel uses.
 */
@Composable
fun AskUserDialog(
    prompt: AskUserUi,
    onRespond: (answers: Map<String, String>, cancelled: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val selections = remember(prompt.requestId) {
        androidx.compose.runtime.mutableStateMapOf<Int, MutableSet<String>>()
    }
    val others = remember(prompt.requestId) {
        androidx.compose.runtime.mutableStateMapOf<Int, String>()
    }

    fun choose(questionIndex: Int, option: String, multi: Boolean) {
        val set = selections.getOrPut(questionIndex) { mutableSetOf() }
        if (multi) {
            if (!set.add(option)) set.remove(option)
        } else {
            set.clear()
            set.add(option)
        }
        selections[questionIndex] = set
        // Choosing an option clears an "other" answer for single-select.
        if (!multi) others.remove(questionIndex)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agent 需要你的回答", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                prompt.questions.forEachIndexed { index, question ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            question.question,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (question.options.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                question.options.forEach { option ->
                                    val selected = selections[index]?.contains(option) == true
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            choose(index, option, question.multiSelect)
                                        },
                                        label = { Text(option) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                        if (question.allowOther || question.options.isEmpty()) {
                            OutlinedTextField(
                                value = others[index].orEmpty(),
                                onValueChange = { others[index] = it },
                                label = { Text("其他 / 直接回答") },
                                minLines = 1,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val answers = buildMap {
                        prompt.questions.forEachIndexed { index, question ->
                            val other = others[index]?.trim().orEmpty()
                            val picked = selections[index]?.joinToString("、").orEmpty()
                            val value = if (other.isNotBlank()) other else picked
                            if (value.isNotBlank()) put((index + 1).toString(), value)
                        }
                    }
                    onRespond(answers, false)
                },
            ) { Text("提交") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onRespond(emptyMap(), true) }) {
                    Text("取消本轮")
                }
                Spacer(Modifier.padding(horizontal = 4.dp))
                TextButton(onClick = onDismiss) { Text("稍后") }
            }
        },
    )
}
