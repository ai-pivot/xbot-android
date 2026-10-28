package dev.xbot.android.ui

import dev.xbot.android.chat.TurnUi

/**
 * 一轮对话里「过程」部分的摘要（思考块数 / 工具数 / 是否有过程旁白）。
 *
 * 手机上一轮里 3 个思考 + 6 个工具各占一行要 350dp 以上，半屏都是过程。
 * 所以过程整体折叠成一行，只有最终答复常驻显示（见 `ChatScreen.TurnBlock`）。
 */
internal data class TurnProcess(
    val reasoningCount: Int,
    val toolCount: Int,
    val hasNarration: Boolean,
) {
    val isEmpty: Boolean get() = reasoningCount == 0 && toolCount == 0 && !hasNarration
}

/**
 * 最终答复：`final_text` 优先，没有就用最后一个 iteration 的正文。
 *
 * 它**不参与折叠** —— 折叠的是过程（思考、工具、过程旁白），答复永远显示。
 */
internal fun TurnUi.finalAnswerText(): String =
    finalText.ifBlank { iterations.lastOrNull()?.content.orEmpty() }

/**
 * 除最终答复之外的过程内容。
 *
 * 过程旁白（中间 iteration 的正文）也归进过程里：它本来就是「执行过程中的解说」，
 * 展开后照常可见，折叠时不该占屏。
 */
internal fun TurnUi.process(): TurnProcess {
    val answer = finalAnswerText()
    var reasoning = 0
    var tools = 0
    var narration = false
    iterations.forEach { iteration ->
        if (iteration.reasoning.isNotBlank()) reasoning += 1
        tools += iteration.tools.size
        if (iteration.content.isNotBlank() && iteration.content != answer) narration = true
    }
    return TurnProcess(reasoningCount = reasoning, toolCount = tools, hasNarration = narration)
}

/**
 * 折叠条上的文案。
 *
 * 运行中优先显示「正在做什么」，跑完才是统计；统计里 0 的部分不显示。
 */
internal fun processLabel(
    process: TurnProcess,
    running: Boolean,
    currentTool: String? = null,
): String {
    if (running && !currentTool.isNullOrBlank()) return "过程 · 正在 $currentTool"
    val parts = buildList {
        if (process.reasoningCount > 0) add("思考 ${process.reasoningCount}")
        if (process.toolCount > 0) add("工具 ${process.toolCount}")
    }
    val detail = if (parts.isEmpty()) "过程" else parts.joinToString(" · ")
    return if (running) "$detail · 进行中" else detail
}
