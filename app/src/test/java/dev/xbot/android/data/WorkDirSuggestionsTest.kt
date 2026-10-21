package dev.xbot.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 新建会话弹窗的工作目录候选：最近用过的在前，其次来自现有会话，去重并截断。
 * 服务端没有目录浏览接口，这串候选就是手机侧唯一能给的「方便输入」。
 */
class WorkDirSuggestionsTest {

    private fun chat(id: String, workDir: String) = UserChat(chatId = id, channel = "web", workDir = workDir)

    @Test
    fun `recent dirs come first and stay in order`() {
        val result = workDirSuggestions(
            recent = listOf("/home/cjw/b", "/home/cjw/a"),
            sessions = emptyList(),
        )

        assertEquals(listOf("/home/cjw/b", "/home/cjw/a"), result)
    }

    @Test
    fun `session work dirs fill the gaps without duplicating recent ones`() {
        val result = workDirSuggestions(
            recent = listOf("/home/cjw/xbot"),
            sessions = listOf(
                chat("c1", "/home/cjw/xbot"),
                chat("c2", "/home/cjw/blog"),
                chat("c3", "/home/cjw/blog"),
            ),
        )

        assertEquals(listOf("/home/cjw/xbot", "/home/cjw/blog"), result)
    }

    @Test
    fun `blank entries are skipped and the list is capped`() {
        val result = workDirSuggestions(
            recent = listOf("", "   "),
            sessions = listOf(
                chat("c1", " /a "),
                chat("c2", ""),
                chat("c3", "/b"),
                chat("c4", "/c"),
                chat("c5", "/d"),
                chat("c6", "/e"),
            ),
            limit = 3,
        )

        assertEquals(listOf("/a", "/b", "/c"), result)
    }

    @Test
    fun `no candidates yields an empty list`() {
        assertEquals(emptyList<String>(), workDirSuggestions(emptyList(), emptyList()))
    }
}
