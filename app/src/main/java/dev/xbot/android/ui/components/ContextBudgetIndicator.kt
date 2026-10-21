package dev.xbot.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
fun budgetColor(percent: Float): Color = when {
    percent >= DANGER_FRACTION -> MaterialTheme.colorScheme.error
    percent >= WARN_FRACTION -> WarnColor
    else -> MaterialTheme.colorScheme.primary
}

/**
 * 输入框右侧的上下文预算指示器：一个小圆环 + 百分比，点击看明细。
 * `budget == null`（服务端还没有真实 token 数据）时不占位。
 */
@Composable
fun ContextBudgetIndicator(
    budget: ContextBudgetUi?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (budget == null) return

    val fraction = budget.percent.coerceIn(0f, 1f)
    val color = budgetColor(fraction)
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 4.dp)
            .semantics {
                contentDescription = "上下文占用 ${budget.usedPercent}%，点按查看明细"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val strokeWidth = 2.dp.toPx()
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth),
            )
            if (fraction > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = "${budget.usedPercent}%",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 点指示器弹出的明细。 */
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

private fun formatTokens(value: Long): String = when {
    value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000L -> String.format(Locale.US, "%.1fk", value / 1_000.0)
    else -> value.toString()
}
