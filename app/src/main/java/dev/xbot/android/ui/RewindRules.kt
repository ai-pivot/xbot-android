package dev.xbot.android.ui

import dev.xbot.android.chat.TurnUi

/**
 * 这条用户消息能不能「编辑并重新发送」。
 *
 * 需要同时满足：
 * - 来自持久化历史（`userHistoryId > 0`）—— rewind 接口要的就是这个 id，实时回合没有；
 * - 有正文（纯附件消息没法编辑）；
 * - 当前没有别的回合在跑，也没在回退 —— 否则会和 SSE 回声、历史重载打架。
 */
internal fun canRewindTurn(turn: TurnUi, busy: Boolean, rewinding: Boolean): Boolean =
    turn.userHistoryId > 0L && turn.userText.isNotBlank() && !busy && !rewinding
