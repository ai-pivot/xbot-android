package dev.xbot.android.ui

import dev.xbot.android.chat.ChatUiState
import dev.xbot.android.chat.IterationUi
import dev.xbot.android.chat.ToolUi
import dev.xbot.android.chat.TurnUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScrollTest {

    private fun iteration(content: String, tools: List<ToolUi> = emptyList()) =
        IterationUi(index = 1, content = content, tools = tools)

    private val olderTurn = TurnUi(
        turnId = 1,
        userText = "older question",
        iterations = listOf(iteration("older answer")),
    )
    private val tailTurn = TurnUi(
        turnId = 9,
        userText = "newest question",
        iterations = listOf(iteration("newest answer")),
        finalText = "newest answer",
    )

    /**
     * 回归：上滑补页是往列表**前面**插入历史，尾部没变，绝不能触发贴底滚动。
     */
    @Test
    fun `prepending older turns does not change the tail fingerprint`() {
        val before = ChatUiState(turns = listOf(tailTurn)).tailFingerprint(thinking = false)
        val prepended = ChatUiState(
            turns = listOf(olderTurn, olderTurn.copy(turnId = 5, userText = "mid"), tailTurn),
            hasMore = true,
            oldestId = 1L,
            loadingMore = true,
        ).tailFingerprint(thinking = false)

        assertEquals(before, prepended)
    }

    @Test
    fun `appending a new turn changes the tail fingerprint`() {
        val before = ChatUiState(turns = listOf(tailTurn)).tailFingerprint(thinking = false)
        val after = ChatUiState(
            turns = listOf(tailTurn, TurnUi(turnId = 10, userText = "next")),
        ).tailFingerprint(thinking = false)

        assertNotEquals(before, after)
    }

    @Test
    fun `streamed text changes the tail fingerprint`() {
        val before = ChatUiState(turns = listOf(tailTurn)).tailFingerprint(thinking = false)
        val after = ChatUiState(
            turns = listOf(tailTurn.copy(iterations = listOf(iteration("newest answer + more")))),
        ).tailFingerprint(thinking = false)

        assertNotEquals(before, after)
    }

    @Test
    fun `tool state changes the tail fingerprint`() {
        val running = ToolUi(
            key = "t1", name = "shell", label = "shell", status = "running",
            args = "", detail = "", summary = "", elapsedMs = 0L, iteration = 1,
        )
        val before = ChatUiState(turns = listOf(tailTurn)).tailFingerprint(thinking = false)
        val after = ChatUiState(
            turns = listOf(tailTurn.copy(iterations = listOf(iteration("newest answer", listOf(running))))),
        ).tailFingerprint(thinking = false)

        assertNotEquals(before, after)
    }

    @Test
    fun `thinking row appearing changes the tail fingerprint`() {
        val state = ChatUiState(turns = listOf(tailTurn))

        assertNotEquals(
            state.tailFingerprint(thinking = false),
            state.tailFingerprint(thinking = true),
        )
    }

    @Test
    fun `user scroll always drops follow`() {
        assertFalse(followAfterUserScroll())
    }

    @Test
    fun `follow returns only when settled at the bottom`() {
        assertTrue(followAfterSettle(follow = false, atBottom = true))
        assertFalse(followAfterSettle(follow = false, atBottom = false))
        assertTrue(followAfterSettle(follow = true, atBottom = false))
    }

    @Test
    fun `never pin while the user is scrolling`() {
        assertFalse(
            shouldPinToBottom(follow = true, loading = false, itemCount = 10, userScrolling = true),
        )
        assertFalse(
            shouldPinToBottom(follow = false, loading = false, itemCount = 10, userScrolling = false),
        )
        assertFalse(
            shouldPinToBottom(follow = true, loading = true, itemCount = 10, userScrolling = false),
        )
        assertFalse(
            shouldPinToBottom(follow = true, loading = false, itemCount = 0, userScrolling = false),
        )
        assertTrue(
            shouldPinToBottom(follow = true, loading = false, itemCount = 10, userScrolling = false),
        )
    }

    /**
     * 回归：remeasure 期间 `visibleItemsInfo` 为空、`canScrollForward` 瞬时变 false，
     * 这不等于「用户回到底部」。空闲会话里输入区状态栏一渲染就会触发一次 remeasure，
     * 旧实现因此在没有流式输出时也会把列表拽回底部。
     */
    @Test
    fun `empty layout is never considered at the bottom`() {
        assertFalse(
            isSettledAtBottom(
                totalItemsCount = 0,
                lastVisibleIndex = 0,
                lastVisibleEndOffset = 0,
                viewportEndOffset = 1200,
                canScrollForward = false,
            ),
        )
    }

    @Test
    fun `not the last item means not at the bottom`() {
        assertFalse(
            isSettledAtBottom(
                totalItemsCount = 20,
                lastVisibleIndex = 7,
                lastVisibleEndOffset = 1200,
                viewportEndOffset = 1200,
                canScrollForward = false,
            ),
        )
    }

    @Test
    fun `still scrollable means not at the bottom`() {
        assertFalse(
            isSettledAtBottom(
                totalItemsCount = 20,
                lastVisibleIndex = 19,
                lastVisibleEndOffset = 900,
                viewportEndOffset = 1200,
                canScrollForward = true,
            ),
        )
    }

    @Test
    fun `last item fully inside the viewport is at the bottom`() {
        assertTrue(
            isSettledAtBottom(
                totalItemsCount = 20,
                lastVisibleIndex = 19,
                lastVisibleEndOffset = 1200,
                viewportEndOffset = 1200,
                canScrollForward = false,
            ),
        )
        // 底部 contentPadding 让最后一项的底边落在可视区之内，也算到底。
        assertTrue(
            isSettledAtBottom(
                totalItemsCount = 20,
                lastVisibleIndex = 19,
                lastVisibleEndOffset = 1136,
                viewportEndOffset = 1200,
                canScrollForward = false,
            ),
        )
    }
}
