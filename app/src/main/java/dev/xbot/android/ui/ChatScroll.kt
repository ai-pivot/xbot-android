package dev.xbot.android.ui

import dev.xbot.android.chat.ChatUiState

/**
 * 聊天列表「贴底」（auto-follow）的判定逻辑，抽成纯函数以便纯 JVM 单测。
 *
 * 这里踩过的坑：判断「尾部是否有新内容」曾经用 `turns.size` + 末尾内容拼了一个
 * `followVersion`，并把它和 `itemCount` 一起作为 `LaunchedEffect` 的 key。上滑补页是往
 * 列表**前面**插入整页历史，`size` / `itemCount` 都会变大而尾部一个字节都没变，
 * 于是补页返回的瞬间列表被当成「有新消息」拽回底部 —— 表现就是长会话里滚动时
 * 间歇性弹回底部。
 */
internal data class ChatTail(
    val lastTurnId: Long = 0L,
    val contentLength: Int = 0,
    val reasoningLength: Int = 0,
    val finalLength: Int = 0,
    val toolsHash: Int = 0,
    val pendingLength: Int = 0,
    val thinking: Boolean = false,
)

/**
 * 尾部指纹：只包含列表末尾会随流式输出增长的内容。
 *
 * 刻意**不含** `turns.size`：前面插入历史不改变尾部，因此不会触发贴底滚动。
 * 新回合追加时 [ChatTail.lastTurnId] 变化；同一回合内靠各段长度 / tools 哈希变化。
 */
internal fun ChatUiState.tailFingerprint(thinking: Boolean): ChatTail {
    val lastTurn = turns.lastOrNull()
    val lastIteration = lastTurn?.iterations?.lastOrNull()
    return ChatTail(
        lastTurnId = lastTurn?.turnId ?: 0L,
        contentLength = lastIteration?.content?.length ?: 0,
        reasoningLength = lastIteration?.reasoning?.length ?: 0,
        finalLength = lastTurn?.finalText?.length ?: 0,
        toolsHash = lastIteration?.tools?.hashCode() ?: 0,
        pendingLength = pendingUserText?.length ?: 0,
        thinking = thinking,
    )
}

/**
 * 用户自己拖动 / 惯性滚动之后是否还贴底：永远不贴。
 *
 * 关键点：不能再看此刻的 `canScrollForward`。从底部起手向上拖时列表还「不能向下滚」，
 * 旧实现 `if (isDragged && canScrollForward) follow = false` 因此漏判，
 * 手势期间任何内容变化（补页、流式追加）都会把列表拽回底部。
 */
internal fun followAfterUserScroll(): Boolean = false

/**
 * 手势/滚动结束后的贴底状态：只有真的停在底部才恢复贴底，停在半路保持脱离。
 * 调用方必须保证此刻没有正在进行的滚动。
 */
internal fun followAfterSettle(follow: Boolean, atBottom: Boolean): Boolean = follow || atBottom

/**
 * 现在是否应该把列表滚到最底部。
 *
 * [userScrolling] 为 true 时不动列表：贴底滚动和用户手势抢 [androidx.compose.foundation.lazy.LazyListState]
 * 会表现为手势被打断、位置乱跳。
 */
internal fun shouldPinToBottom(
    follow: Boolean,
    loading: Boolean,
    itemCount: Int,
    userScrolling: Boolean,
): Boolean = follow && !loading && itemCount > 0 && !userScrolling
