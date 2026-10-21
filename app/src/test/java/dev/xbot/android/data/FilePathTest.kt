package dev.xbot.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 目录浏览器的路径拼接/上级计算。服务端返回的是子项名字（`entries[].name`），
 * 手机侧要自己拼出完整路径，这里钉住边界：根目录、尾斜杠、空值。
 */
class FilePathTest {

    @Test
    fun `join keeps a single slash`() {
        assertEquals("/home/cjw/proj", joinPath("/home/cjw", "proj"))
        assertEquals("/home", joinPath("/", "home"))
        assertEquals("/home/cjw", joinPath("/home/", "cjw"))
        assertEquals("/home", joinPath("/home", ""))
        assertEquals("/proj", joinPath("", "proj"))
    }

    @Test
    fun `parent walks up and stops at root`() {
        assertEquals("/home/cjw", parentPath("/home/cjw/proj"))
        assertEquals("/home", parentPath("/home/cjw/"))
        assertEquals("/", parentPath("/home"))
        assertEquals("/", parentPath("/"))
        assertEquals("/", parentPath(""))
    }

    @Test
    fun `directories filter out files and sort case-insensitively`() {
        val response = FsListResponse(
            entries = listOf(
                FsEntry(name = "zeta", isDir = true),
                FsEntry(name = "Alpha", isDir = true),
                FsEntry(name = "notes.txt", isDir = false),
                FsEntry(name = "", isDir = true),
            ),
        )

        assertEquals(listOf("Alpha", "zeta"), response.directories().map { it.name })
    }

    @Test
    fun `fs list decodes the server payload`() {
        val payload = """
            {"ok":true,"data":{"entries":[
              {"name":"xbot","isDir":true,"size":0,"mode":"drwxr-xr-x","modTime":"2026-10-21T14:00:00Z"},
              {"name":"a.txt","isDir":false,"size":12,"mode":"-rw-r--r--","modTime":"2026-10-21T14:00:00Z"}
            ]},"error":null}
        """.trimIndent()

        val envelope = XbotJson.decodeFromString<ApiEnvelope<FsListResponse>>(payload)
        val entries = envelope.data!!.entries

        assertEquals(2, entries.size)
        assertEquals(true, entries[0].isDir)
        assertEquals("a.txt", entries[1].name)
        assertEquals(12L, entries[1].size)
    }
}
