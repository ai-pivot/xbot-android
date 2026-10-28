package dev.xbot.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.xbot.android.ui.components.MarkdownText

/**
 * 流式文本的「配速显示」（paced reveal）。
 *
 * 借鉴 Paseo 移动端的做法（`agent-stream/text-reveal.ts`）：模型输出到达时是**阵发**的，
 * 按到达直接渲染就会一阵一阵地蹦字；把合批窗口调小只会让块更小更频繁，治标不治本。
 * 正确做法是——**到达只更新目标长度，显示速率由积压量反推**：积压越多追得越快，
 * 积压少时慢慢补齐，观感连续。
 *
 * 三条不变量：
 * 1. 状态里存**全文**，只有渲染的切片被配速（复制、选中、滚动几何都跟屏幕一致）；
 * 2. **首次出现整段渲染**，只有「增长」才配速（历史消息、重新挂载不需要特判）；
 * 3. **结束即 snap 到全文**，绝不把未显示的字留在尾巴上。
 */
internal const val TEXT_REVEAL_HORIZON_MS = 150L

/** 一帧最多推进一次（高刷屏也不超过 60Hz）。 */
internal const val TEXT_REVEAL_FRAME_INTERVAL_MS = 1000L / 60

/** 单帧耗时上限：被后台/卡顿拉长时不要让一次推进吃掉一大段。 */
private const val MAX_ELAPSED_MS = 250L

private const val ZWJ = 0x200D

/**
 * 这一帧该显示多少个字符。
 *
 * 与积压量成正比，所以模型跑在前面时追得快、跟不上时慢慢补；至少推进 1 个字，
 * 保证尾巴总能追平。
 */
internal fun computeRevealStep(
    backlog: Int,
    elapsedMs: Long,
    horizonMs: Long = TEXT_REVEAL_HORIZON_MS,
): Int {
    if (backlog <= 0) return 0
    if (horizonMs <= 0) return backlog
    val elapsed = elapsedMs.coerceIn(0L, MAX_ELAPSED_MS)
    if (elapsed <= 0) return 0
    if (elapsed >= horizonMs) return backlog
    val step = ((backlog.toLong() * elapsed + horizonMs - 1) / horizonMs).toInt()
    return step.coerceIn(1, backlog)
}

/**
 * 把切点拉回到**安全的字素边界**。
 *
 * 在任意下标 `substring` 会把代理对（emoji）或组合字符（音标、肤色修饰、ZWJ 序列）
 * 劈开，流式时表现为半个字形闪一下。这里只移动「渲染边界」，调用方的推进计数保持单调，
 * 因此长序列最多晚一两帧显示，不会卡住。
 */
internal fun clampToSafeRevealBoundary(text: String, index: Int): Int {
    if (index <= 0) return 0
    if (index >= text.length) return text.length
    var cut = index
    // 判断「切在这里会不会把一簇字素劈开」：看切点**之后**那个字符。
    // 它是附加成分（音标、变体选择符、肤色）、是 ZWJ、或者切点前一个是 ZWJ，
    // 都说明切点落在簇中间 —— 后退一个 code point 再判断。
    while (cut > 0 && cut < text.length) {
        val next = text.codePointAt(cut)
        val previous = text.codePointBefore(cut)
        val splitsSurrogatePair = Character.isLowSurrogate(text[cut])
        val insideCluster = isExtender(next) || next == ZWJ || previous == ZWJ
        if (!splitsSurrogatePair && !insideCluster) break
        cut -= Character.charCount(previous)
    }
    return cut
}

private fun isExtender(cp: Int): Boolean = when (Character.getType(cp)) {
    Character.NON_SPACING_MARK.toInt(),
    Character.ENCLOSING_MARK.toInt(),
    Character.COMBINING_SPACING_MARK.toInt(),
    -> true
    else -> cp in 0xFE00..0xFE0F || cp in 0x1F3FB..0x1F3FF
}

/**
 * 最后一个「稳定块边界」的下标：空行之后、且不在未闭合的 ``` 围栏里。
 *
 * 用它把正文切成 `head` + `tail`：head 的字符串只在某个块写完时才变，
 * `MarkdownText` 内部 `remember(text) { parseMarkdown(text) }` 就能命中缓存；
 * 每帧重新解析的只有很短的 tail —— 这样配速 60fps 也不会把 CPU 顶满。
 */
internal fun stableBlockBoundary(text: String): Int {
    var inFence = false
    var boundary = 0
    var lineStart = 0
    while (lineStart < text.length) {
        val newline = text.indexOf('\n', lineStart)
        val lineEnd = if (newline < 0) text.length else newline
        val line = text.substring(lineStart, lineEnd).trim()
        if (line.startsWith("```")) inFence = !inFence
        if (!inFence && line.isEmpty() && newline >= 0) boundary = newline + 1
        lineStart = lineEnd + 1
    }
    return boundary
}

/**
 * 配速渲染一段 Markdown。
 *
 * [streaming] 为 true 时按积压量逐帧推进；false（回合结束/历史消息）直接整段显示。
 */
@Composable
internal fun PacedMarkdown(
    text: String,
    streaming: Boolean,
    modifier: Modifier = Modifier,
) {
    // 首次出现整段显示；之后只对增长配速。
    var revealed by remember { mutableIntStateOf(text.length) }

    // 文本被替换/回退（rewind、重新加载）时不要留下更长的旧切片。
    LaunchedEffect(text) {
        if (text.length < revealed) revealed = text.length
    }

    LaunchedEffect(text, streaming) {
        if (!streaming) {
            revealed = text.length
            return@LaunchedEffect
        }
        var lastFrame = withFrameNanos { it }
        while (revealed < text.length) {
            withFrameNanos { now ->
                val elapsedMs = (now - lastFrame) / 1_000_000L
                lastFrame = now
                val step = computeRevealStep(text.length - revealed, elapsedMs)
                if (step > 0) revealed = (revealed + step).coerceAtMost(text.length)
            }
        }
    }

    val end = if (streaming) clampToSafeRevealBoundary(text, revealed) else text.length
    val head = minOf(stableBlockBoundary(text), end)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (head > 0) MarkdownText(text.substring(0, head))
        if (end > head) MarkdownText(text.substring(head, end))
    }
}
