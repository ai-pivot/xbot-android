package dev.xbot.android.data

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归测试：`/api/history/rewind` 的响应里 `history_rewound` / `files_rewound` 是 **bool**
 * （服务端 `protocol.HistoryRewindResult`），客户端曾把两者声明成 `Int`，
 * 导致回退其实成功了、却在解析阶段炸：
 * `Unexpected symbol 't' in numeric literal at path: $.data.files_rewound`。
 */
class RewindResponseTest {

    // 真机上抓到的原始响应（少数字段省略）。
    private val payload = """
        {"ok":true,"data":{"checkpoint_error":"","draft":"合并了，你打 Tag 测试一下。 ",
        "files_rewound":true,"history_rewound":true,"rewind_result":null,"target_history_id":37157},
        "error":null}
    """.trimIndent()

    @Test
    fun `boolean rewind flags decode from the server payload`() {
        val envelope = XbotJson.decodeFromString<ApiEnvelope<RewindResponse>>(payload)

        val data = envelope.data
        assertTrue(envelope.ok)
        assertTrue(data!!.historyRewound)
        assertTrue(data.filesRewound)
        assertEquals(37157L, data.targetHistoryId)
        assertEquals("合并了，你打 Tag 测试一下。 ", data.draft)
        assertEquals("", data.checkpointError)
    }

    @Test
    fun `failed rewind decodes to false flags instead of throwing`() {
        val envelope = XbotJson.decodeFromString<ApiEnvelope<RewindResponse>>(
            """{"ok":true,"data":{"history_rewound":false,"files_rewound":false,
               "checkpoint_error":"checkpoint unavailable","target_history_id":7},"error":null}""",
        )

        val data = envelope.data!!
        assertFalse(data.historyRewound)
        assertFalse(data.filesRewound)
        assertEquals("checkpoint unavailable", data.checkpointError)
    }
}
