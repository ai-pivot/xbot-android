package dev.xbot.android.ui

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 聊天列表「滚动 / 贴底」的环形日志（最近 [MAX] 条）。
 *
 * 「空闲会话里列表自己跳回底部」这类问题只在真机上复现：到底是哪一次滚动调用、
 * 调用时列表处于什么布局状态，光看代码猜不出来。这里把每一次相关动作和布局快照
 * 记下来（同时写 logcat `ChatScroll`），设置 → 滚动诊断 可以复制出来。
 */
internal object ChatScrollTrace {

    private const val MAX = 200
    private const val TAG = "ChatScroll"

    private val entries = ArrayDeque<String>(MAX)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun record(event: String) {
        val line = "${timeFormat.format(Date())}  $event"
        synchronized(entries) {
            if (entries.size >= MAX) entries.removeFirst()
            entries.addLast(line)
        }
        Log.d(TAG, event)
    }

    fun dump(): String = synchronized(entries) { entries.joinToString("\n") }

    fun clear() {
        synchronized(entries) { entries.clear() }
    }
}
