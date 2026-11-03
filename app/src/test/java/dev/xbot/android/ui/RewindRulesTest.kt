package dev.xbot.android.ui

import dev.xbot.android.chat.TurnUi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RewindRulesTest {

    private fun turn(historyId: Long, text: String) =
        TurnUi(turnId = 1, userText = text, userHistoryId = historyId)

    @Test
    fun `persisted user message can be edited and resent`() {
        assertTrue(canRewindTurn(turn(42L, "跑一下这个脚本"), busy = false, rewinding = false))
    }

    @Test
    fun `live turn without a history id cannot be rewound`() {
        // 实时回合 userHistoryId = 0，rewind 接口要的就是历史 id，没有就没法回退
        assertFalse(canRewindTurn(turn(0L, "刚发的消息"), busy = false, rewinding = false))
    }

    @Test
    fun `empty user text cannot be edited`() {
        assertFalse(canRewindTurn(turn(42L, "   "), busy = false, rewinding = false))
    }

    @Test
    fun `no editing while a turn is running`() {
        assertFalse(canRewindTurn(turn(42L, "hi"), busy = true, rewinding = false))
    }

    @Test
    fun `no editing while another rewind is in flight`() {
        assertFalse(canRewindTurn(turn(42L, "hi"), busy = false, rewinding = true))
    }
}
