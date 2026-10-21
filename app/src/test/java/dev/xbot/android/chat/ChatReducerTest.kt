package dev.xbot.android.chat

import dev.xbot.android.data.HistoryIteration
import dev.xbot.android.data.HistoryMessage
import dev.xbot.android.data.HistoryResponse
import dev.xbot.android.data.ProgressEvent
import dev.xbot.android.data.SessionEvent
import dev.xbot.android.data.TokenUsage
import dev.xbot.android.data.WsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReducerTest {

    @Test
    fun `history builds one turn per turn id`() {
        val state = ChatReducer.withHistory(
            ChatReducer.empty("chat_1", "web"),
            HistoryResponse(
                ok = true,
                messages = listOf(
                    HistoryMessage(id = 1, role = "user", content = "hello", turnId = 7),
                    HistoryMessage(
                        id = 2,
                        role = "assistant",
                        content = "done",
                        turnId = 7,
                        iterations = listOf(
                            HistoryIteration(iteration = 1, content = "step one"),
                            HistoryIteration(iteration = 2, content = "step two"),
                        ),
                    ),
                ),
                hasMore = true,
                oldestId = 1,
            ),
        )

        assertEquals(1, state.turns.size)
        val turn = state.turns.single()
        assertEquals(7L, turn.turnId)
        assertEquals("hello", turn.userText)
        assertEquals("done", turn.finalText)
        assertEquals(listOf(1, 2), turn.iterations.map { it.index })
        assertTrue(state.hasMore)
        assertFalse(state.loading)
    }

    @Test
    fun `stream delta appends and checkpoint replaces`() {
        var state = ChatReducer.empty("chat_1", "web")
        state = ChatReducer.applyProgress(
            state,
            ProgressEvent(
                turnId = 5,
                iteration = 1,
                streamDelta = "Hel",
                phase = "thinking",
            ),
        )
        state = ChatReducer.applyProgress(
            state,
            ProgressEvent(turnId = 5, iteration = 1, streamDelta = "lo", phase = "thinking"),
        )
        assertEquals("Hello", state.turns.single().iterations.single().content)

        state = ChatReducer.applyProgress(
            state,
            ProgressEvent(turnId = 5, iteration = 1, streamContent = "Hello world"),
        )
        assertEquals("Hello world", state.turns.single().iterations.single().content)
    }

    @Test
    fun `phase done keeps the last iteration and clears busy`() {
        var state = ChatReducer.empty("chat_1", "web")
        state = ChatReducer.applyProgress(
            state,
            ProgressEvent(
                turnId = 3,
                iteration = 2,
                content = "final answer",
                streamContent = "final answer",
                phase = "thinking",
            ),
        )
        assertTrue(state.busy)

        state = ChatReducer.applyProgress(
            state,
            ProgressEvent(turnId = 3, iteration = 2, phase = "done"),
        )

        assertFalse(state.busy)
        val iteration = state.turns.single().iterations.single()
        assertEquals("final answer", iteration.content)
        assertTrue(iteration.done)
        assertEquals(0L, state.liveTurnId)
    }

    @Test
    fun `text event commits the reply and never clears prior content`() {
        var state = ChatReducer.applyProgress(
            ChatReducer.empty("chat_1", "web"),
            ProgressEvent(
                turnId = 9,
                iteration = 1,
                streamContent = "streamed body",
                phase = "thinking",
            ),
        )
        state = ChatReducer.applyEvent(
            state,
            WsMessage(type = "text", turnId = 9, content = "final body"),
        )

        assertEquals("final body", state.turns.single().finalText)
        assertEquals("streamed body", state.turns.single().iterations.single().content)
        assertFalse(state.busy)
    }

    @Test
    fun `session idle clears busy without deleting the turn`() {
        var state = ChatReducer.applyProgress(
            ChatReducer.empty("chat_1", "web"),
            ProgressEvent(turnId = 2, iteration = 1, streamDelta = "x", phase = "thinking"),
        )
        state = ChatReducer.applyEvent(
            state,
            WsMessage(
                type = "session",
                session = SessionEvent(chatId = "chat_1", action = "idle"),
            ),
        )

        assertFalse(state.busy)
        assertEquals(1, state.turns.size)
    }

    @Test
    fun `ask user prompt is parsed from progress and resolved event clears it`() {
        var state = ChatReducer.applyEvent(
            ChatReducer.empty("chat_1", "web"),
            WsMessage(
                type = "ask_user",
                progress = ProgressEvent(
                    turnId = 4,
                    requestId = "req-1",
                    questions = listOf(
                        dev.xbot.android.data.AskUserQuestion(
                            question = "pick one",
                            options = listOf("a", "b"),
                        ),
                    ),
                ),
            ),
        )
        assertNotNull(state.askUser)
        assertEquals("req-1", state.askUser?.requestId)

        state = ChatReducer.applyEvent(
            state,
            WsMessage(type = "ask_user_resolved", requestId = "req-1", reason = "answered"),
        )
        assertNull(state.askUser)
    }

    @Test
    fun `send accepted creates an optimistic turn that events merge into`() {
        var state = ChatReducer.empty("chat_1", "web")
        state = ChatReducer.sendAccepted(state, "hello", turnId = 11, queued = false)
        assertEquals(1, state.turns.size)
        assertEquals("hello", state.turns.single().userText)
        assertTrue(state.busy)

        state = ChatReducer.applyEvent(
            state,
            WsMessage(type = "user_echo", turnId = 11, content = "hello"),
        )
        assertEquals(1, state.turns.size)
        assertEquals("hello", state.turns.single().userText)
        assertNull(state.pendingUserText)
    }

    @Test
    fun `iteration history delta merges by iteration number`() {
        var state = ChatReducer.empty("chat_1", "web")
        state = ChatReducer.applyProgress(
            state,
            ProgressEvent(
                turnId = 1,
                iteration = 2,
                iterationHistory = listOf(
                    ProgressEvent(iteration = 1, content = "one"),
                ),
                phase = "thinking",
            ),
        )
        state = ChatReducer.applyProgress(
            state,
            ProgressEvent(
                turnId = 1,
                iteration = 2,
                iterationHistory = listOf(
                    ProgressEvent(iteration = 1, content = "one"),
                    ProgressEvent(iteration = 2, content = "two"),
                ),
                phase = "thinking",
            ),
        )

        assertEquals(listOf(1, 2), state.turns.single().iterations.map { it.index })
        assertEquals("one", state.turns.single().iterations[0].content)
        assertEquals("two", state.turns.single().iterations[1].content)
    }

    @Test
    fun `tool progress merges by call id and updates status`() {
        var state = ChatReducer.empty("chat_1", "web")
        state = ChatReducer.applyProgress(
            state,
            ProgressEvent(
                turnId = 1,
                iteration = 1,
                activeTools = listOf(
                    dev.xbot.android.data.ToolProgress(
                        name = "Shell",
                        callId = "call-1",
                        status = "running",
                        args = "{\"cmd\":\"ls\"}",
                    ),
                ),
                phase = "tool_exec",
            ),
        )
        state = ChatReducer.applyProgress(
            state,
            ProgressEvent(
                turnId = 1,
                iteration = 1,
                completedTools = listOf(
                    dev.xbot.android.data.ToolProgress(
                        name = "Shell",
                        callId = "call-1",
                        status = "done",
                        detail = "file.txt",
                    ),
                ),
                phase = "thinking",
            ),
        )

        val tools = state.turns.single().iterations.single().tools
        assertEquals(1, tools.size)
        assertEquals("done", tools.single().status)
        assertEquals("file.txt", tools.single().detail)
        assertEquals("{\"cmd\":\"ls\"}", tools.single().args)
    }

    @Test
    fun `command reply without turn id becomes a standalone turn`() {
        val state = ChatReducer.applyEvent(
            ChatReducer.empty("chat_1", "web"),
            WsMessage(type = "text", turnId = 0, content = "pwd output"),
        )
        assertEquals(1, state.turns.size)
        assertEquals("pwd output", state.turns.single().finalText)
    }

    @Test
    fun `latest reload merges into older pages and keeps the older cursor`() {
        val older = ChatReducer.withHistory(
            ChatReducer.empty("chat_1", "web"),
            HistoryResponse(
                ok = true,
                messages = listOf(
                    HistoryMessage(id = 1, role = "user", content = "old", turnId = 1),
                    HistoryMessage(id = 2, role = "assistant", content = "old reply", turnId = 1),
                ),
                hasMore = true,
                oldestId = 1,
            ),
        )

        val merged = ChatReducer.mergeLatestHistory(
            older,
            HistoryResponse(
                ok = true,
                messages = listOf(
                    HistoryMessage(id = 3, role = "user", content = "new", turnId = 2),
                    HistoryMessage(id = 4, role = "assistant", content = "new reply", turnId = 2),
                ),
                hasMore = true,
                oldestId = 3,
            ),
        )

        assertEquals(listOf(1L, 2L), merged.turns.map { it.turnId })
        assertEquals(1L, merged.oldestId)
        assertTrue(merged.hasMore)
    }

    @Test
    fun `completion-only active progress does not mark an idle session busy`() {
        val state = ChatReducer.withHistory(
            ChatReducer.empty("chat_1", "web"),
            HistoryResponse(
                ok = true,
                messages = listOf(
                    HistoryMessage(id = 1, role = "user", content = "hi", turnId = 1),
                    HistoryMessage(id = 2, role = "assistant", content = "hello", turnId = 1),
                ),
                processing = false,
                // This is exactly what the server returns for an idle session:
                // iteration 0, turn_id absent, phase "done".
                activeProgress = ProgressEvent(phase = "done", todos = emptyList()),
            ),
        )

        assertFalse(state.busy)
        assertEquals(0L, state.liveTurnId)
    }

    @Test
    fun `running active progress still marks the session busy`() {
        val state = ChatReducer.withHistory(
            ChatReducer.empty("chat_1", "web"),
            HistoryResponse(
                ok = true,
                processing = false,
                activeProgress = ProgressEvent(
                    turnId = 5,
                    iteration = 2,
                    phase = "thinking",
                    streamDelta = "partial",
                ),
            ),
        )

        assertTrue(state.busy)
        assertEquals(5L, state.liveTurnId)
        assertEquals("partial", state.turns.single().iterations.single().content)
    }

    @Test
    fun `terminal progress with no turn id clears a stale busy flag`() {
        val state = ChatReducer.applyProgress(
            ChatReducer.empty("chat_1", "web").copy(busy = true),
            ProgressEvent(iteration = 0, phase = "done"),
        )

        assertFalse(state.busy)
    }

    // ------------------------------------------------------------------
    // 上下文预算指示器
    // ------------------------------------------------------------------

    @Test
    fun `context budget prefers the server usage percent`() {
        val state = ChatReducer.withContextBudget(
            ChatReducer.empty("chat_1", "web"),
            TokenUsage(
                available = true,
                promptTokens = 85_000,
                maxContextTokens = 200_000,
                usagePercent = 42.5,
                model = "gpt-4o",
            ),
        )

        val budget = state.contextBudget
        assertNotNull(budget)
        assertEquals(0.425f, budget!!.percent, 0.001f)
        assertEquals(43, budget.usedPercent)
        assertEquals(200_000L, budget.maxContextTokens)
        assertEquals("gpt-4o", budget.model)
        assertTrue(budget.exact)
    }

    @Test
    fun `context budget falls back to prompt over max context`() {
        val budget = ChatReducer.withContextBudget(
            ChatReducer.empty("chat_1", "web"),
            TokenUsage(available = true, promptTokens = 30_000, maxContextTokens = 120_000),
        ).contextBudget!!

        assertEquals(0.25f, budget.percent, 0.001f)
        assertFalse(budget.exact)
    }

    @Test
    fun `context budget is hidden until the server has real token data`() {
        val state = ChatReducer.withContextBudget(
            ChatReducer.empty("chat_1", "web").copy(
                contextBudget = ContextBudgetUi(promptTokens = 1, percent = 0.5f),
            ),
            TokenUsage(available = false, usagePercent = null, promptTokens = 0),
        )

        assertNull(state.contextBudget)
    }

    @Test
    fun `context budget clamps a percent above one hundred`() {
        val budget = ChatReducer.withContextBudget(
            ChatReducer.empty("chat_1", "web"),
            TokenUsage(
                available = true,
                promptTokens = 300_000,
                maxContextTokens = 200_000,
                usagePercent = 150.0,
            ),
        ).contextBudget!!

        assertEquals(1f, budget.percent, 0.0001f)
        assertEquals(100, budget.usedPercent)
    }

    @Test
    fun `progress token usage updates the budget while the turn runs`() {
        val state = ChatReducer.applyProgress(
            ChatReducer.empty("chat_1", "web").copy(
                contextBudget = ContextBudgetUi(
                    maxContextTokens = 1_000,
                    percent = 0.1f,
                    exact = true,
                ),
            ),
            ProgressEvent(
                turnId = 3,
                iteration = 1,
                phase = "running",
                tokenUsage = TokenUsage(promptTokens = 250),
            ),
        )

        val budget = state.contextBudget!!
        assertEquals(250L, budget.promptTokens)
        assertEquals(0.25f, budget.percent, 0.001f)
        assertFalse(budget.exact)
    }

    @Test
    fun `progress token usage without a known context cap leaves the budget alone`() {
        val state = ChatReducer.applyProgress(
            ChatReducer.empty("chat_1", "web"),
            ProgressEvent(
                iteration = 1,
                phase = "running",
                tokenUsage = TokenUsage(promptTokens = 250),
            ),
        )

        assertNull(state.contextBudget)
    }
}
