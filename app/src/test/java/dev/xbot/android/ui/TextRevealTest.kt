package dev.xbot.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextRevealTest {

    // ---------------------------------------------------------------- 配速步长

    @Test
    fun `nothing to reveal when the backlog is empty`() {
        assertEquals(0, computeRevealStep(backlog = 0, elapsedMs = 16))
        assertEquals(0, computeRevealStep(backlog = -5, elapsedMs = 16))
    }

    @Test
    fun `no time elapsed means no progress`() {
        assertEquals(0, computeRevealStep(backlog = 40, elapsedMs = 0))
    }

    @Test
    fun `a full horizon reveals the whole backlog`() {
        assertEquals(40, computeRevealStep(backlog = 40, elapsedMs = TEXT_REVEAL_HORIZON_MS))
        assertEquals(40, computeRevealStep(backlog = 40, elapsedMs = 5_000))
    }

    @Test
    fun `step is proportional to the backlog and always advances`() {
        // 16ms ≈ 1/9 个 horizon，40 字积压 → 约 5 字（向上取整）
        assertEquals(5, computeRevealStep(backlog = 40, elapsedMs = 16))
        // 积压越少，单帧推进越少，但至少 1 个字，保证尾巴一定能追平
        assertEquals(1, computeRevealStep(backlog = 2, elapsedMs = 16))
    }

    @Test
    fun `one frame never reveals more than the backlog`() {
        val step = computeRevealStep(backlog = 3, elapsedMs = 200, horizonMs = 150)
        assertTrue(step <= 3)
    }

    @Test
    fun `elapsed is clamped so a long stall does not dump everything`() {
        val stalled = computeRevealStep(backlog = 1_000, elapsedMs = 60_000)
        val capped = computeRevealStep(backlog = 1_000, elapsedMs = TEXT_REVEAL_HORIZON_MS)
        assertEquals(capped, stalled)
    }

    @Test
    fun `zero horizon disables pacing`() {
        assertEquals(12, computeRevealStep(backlog = 12, elapsedMs = 1, horizonMs = 0))
    }

    // ------------------------------------------------------------ 字素安全切点

    @Test
    fun `cut never splits a surrogate pair`() {
        val text = "a😀b" // 😀 = 2 个 char
        // 切在低位代理上 → 退回 1
        assertEquals(1, clampToSafeRevealBoundary(text, 2))
        assertEquals(3, clampToSafeRevealBoundary(text, 3))
    }

    @Test
    fun `cut does not separate combining marks`() {
        val text = "e\u0301x" // e + 组合重音
        assertEquals(0, clampToSafeRevealBoundary(text, 1))
        assertEquals(2, clampToSafeRevealBoundary(text, 2))
    }

    @Test
    fun `cut does not split a zwj sequence`() {
        val family = "👨\u200D👩\u200D👧" // 三个人 + 两个 ZWJ
        val afterFamily = family.length
        val text = family + "!"

        // 切在序列中间：一律退到序列起点
        assertEquals(0, clampToSafeRevealBoundary(text, 1))
        assertEquals(0, clampToSafeRevealBoundary(text, 2))
        assertEquals(0, clampToSafeRevealBoundary(text, 3))
        // 序列之后可以切
        assertEquals(afterFamily, clampToSafeRevealBoundary(text, afterFamily))
    }

    @Test
    fun `boundaries outside the string are clamped`() {
        val text = "abc"
        assertEquals(0, clampToSafeRevealBoundary(text, 0))
        assertEquals(0, clampToSafeRevealBoundary(text, -3))
        assertEquals(3, clampToSafeRevealBoundary(text, 3))
        assertEquals(3, clampToSafeRevealBoundary(text, 99))
    }

    @Test
    fun `plain ascii cuts exactly where asked`() {
        assertEquals(7, clampToSafeRevealBoundary("hello world", 7))
    }

    // -------------------------------------------------------- 稳定块边界切分

    @Test
    fun `boundary is right after the last blank line`() {
        val text = "第一段\n\n第二段还在写"
        // "第"(0)"一"(1)"段"(2)"\n"(3)"\n"(4) → 空行结束于 4，边界是它之后
        assertEquals(5, stableBlockBoundary(text))
        assertEquals("第二段还在写", text.substring(stableBlockBoundary(text)))
    }

    @Test
    fun `no blank line means nothing is stable yet`() {
        assertEquals(0, stableBlockBoundary("只有一行还在输出"))
    }

    @Test
    fun `blank lines inside an open code fence do not count`() {
        val text = "说明\n\n```kotlin\nval a = 1\n\nval b = 2"
        // 围栏内那个空行不算，稳定边界仍是「说明」之后
        assertEquals(4, stableBlockBoundary(text))
        assertEquals("```kotlin\nval a = 1\n\nval b = 2", text.substring(stableBlockBoundary(text)))
    }

    @Test
    fun `closed fence makes the following blank line stable again`() {
        val text = "```\ncode\n```\n\n后面"
        assertEquals(text.indexOf("后面"), stableBlockBoundary(text))
    }

    @Test
    fun `boundary of empty text is zero`() {
        assertEquals(0, stableBlockBoundary(""))
    }
}
