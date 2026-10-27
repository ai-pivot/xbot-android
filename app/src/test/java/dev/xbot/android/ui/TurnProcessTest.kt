package dev.xbot.android.ui

import dev.xbot.android.chat.IterationUi
import dev.xbot.android.chat.ToolUi
import dev.xbot.android.chat.TurnUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnProcessTest {

    private fun tool(name: String, running: Boolean = false) = ToolUi(
        key = name,
        name = name,
        label = name,
        status = if (running) "running" else "done",
        args = "",
        detail = "",
        summary = "",
        elapsedMs = 1000L,
        iteration = 1,
    )

    @Test
    fun `final answer prefers final text`() {
        val turn = TurnUi(
            turnId = 1,
            iterations = listOf(IterationUi(index = 1, content = "中间旁白")),
            finalText = "最终答复",
        )

        assertEquals("最终答复", turn.finalAnswerText())
    }

    @Test
    fun `final answer falls back to the last iteration content`() {
        val turn = TurnUi(
            turnId = 1,
            iterations = listOf(
                IterationUi(index = 1, content = "旁白"),
                IterationUi(index = 2, content = "最后一段"),
            ),
        )

        assertEquals("最后一段", turn.finalAnswerText())
    }

    @Test
    fun `simple answer has no process to fold`() {
        val turn = TurnUi(
            turnId = 1,
            iterations = listOf(IterationUi(index = 1, content = "直接回答")),
            finalText = "直接回答",
        )

        val process = turn.process()

        assertTrue(process.isEmpty)
        assertFalse(process.hasNarration)
    }

    @Test
    fun `process counts reasoning blocks and tools`() {
        val turn = TurnUi(
            turnId = 1,
            iterations = listOf(
                IterationUi(index = 1, reasoning = "想一想", tools = listOf(tool("Shell"), tool("Shell"))),
                IterationUi(index = 2, reasoning = "再想", tools = listOf(tool("TodoWrite"))),
            ),
            finalText = "最终答复",
        )

        val process = turn.process()

        assertEquals(2, process.reasoningCount)
        assertEquals(3, process.toolCount)
        assertFalse(process.isEmpty)
    }

    @Test
    fun `intermediate narration counts as process but the answer does not`() {
        val turn = TurnUi(
            turnId = 1,
            iterations = listOf(
                IterationUi(index = 1, content = "先跑一下工具"),
                IterationUi(index = 2, content = "最终答复"),
            ),
            finalText = "最终答复",
        )

        val process = turn.process()

        assertTrue(process.hasNarration)
        assertEquals(0, process.reasoningCount)
        assertFalse(process.isEmpty)
    }

    @Test
    fun `label prefers the running tool while the turn is live`() {
        val process = TurnProcess(reasoningCount = 1, toolCount = 2, hasNarration = false)

        assertEquals("过程 · 正在 Shell", processLabel(process, running = true, currentTool = "Shell"))
        assertEquals("思考 1 · 工具 2 · 进行中", processLabel(process, running = true, currentTool = null))
        assertEquals("思考 1 · 工具 2", processLabel(process, running = false, currentTool = "Shell"))
    }

    @Test
    fun `label degrades gracefully when there is nothing to count`() {
        assertEquals("过程", processLabel(TurnProcess(0, 0, true), running = false))
        assertEquals("思考 3", processLabel(TurnProcess(3, 0, false), running = false))
        assertEquals("工具 4", processLabel(TurnProcess(0, 4, false), running = false))
    }
}
