package dev.xbot.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.xbot.android.chat.ContextBudgetUi
import java.util.Locale

/** 上下文占用的告警线：>=60% 变黄，>=85% 变红。 */
private const val WARN_FRACTION = 0.6f
private const val DANGER_FRACTION = 0.85f

private val WarnColor = Color(0xFFF59E0B)

@Composable
private fun budgetColor(percent: Float): Color = when {
    percent >= DANGER_FRACTION -> MaterialTheme.colorScheme.error
    percent >= WARN_FRACTION -> WarnColor
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * 输入框上方的上下文占用文字，形如 `85K/200K`（已用输入 / 上下文上限）。
 * `budget == null`（服务端还没有真实 token 数据）时不占位 —— 连那一行高度也不留。
 */
@Composable
fun ContextBudgetLabel(
    budget: ContextBudgetUi?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (budget == null) return

    val used = formatTokens(budget.promptTokens)
    val cap = if (budget.maxContextTokens > 0L) formatTokens(budget.maxContextTokens) else "—"
    Text(
        text = "$used/$cap",
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        color = budgetColor(budget.percent.coerceIn(0f, 1f)),
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .semantics { contentDescription = "上下文占用 $used / $cap，点按查看明细" },
    )
}

/** 点文字弹出的明细。 */
@Composable
fun ContextBudgetDialog(budget: ContextBudgetUi, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
        title = { Text("上下文预算") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BudgetRow("已用输入", formatTokens(budget.promptTokens))
                BudgetRow(
                    "上下文上限",
                    if (budget.maxContextTokens > 0L) {
                        formatTokens(budget.maxContextTokens)
                    } else {
                        "未知"
                    },
                )
                BudgetRow(
                    "当前占用",
                    if (budget.exact) {
                        "${budget.usedPercent}%"
                    } else {
                        "${budget.usedPercent}%（回合进行中估算）"
                    },
                )
                if (budget.model.isNotBlank()) {
                    BudgetRow("模型", budget.model)
                }
                Text(
                    "接近上限时服务端会压缩历史，回复可能变慢或丢失早期细节。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun BudgetRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 12345 -> `12.3k`，1250000 -> `1.3M`。 */
private fun formatTokens(value: Long): String = when {
    value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000L -> String.format(Locale.US, "%.1fK", value / 1_000.0)
    else -> value.toString()
}
