package dev.xbot.android.data

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * 真起一个 HTTP 服务端（MockWebServer），用真实的 OkHttp 客户端把 [StreamingFileBody]
 * 发出去，校验「服务端收到的字节 == 文件字节」且 `Content-Length` 与实际长度一致。
 *
 * 这类问题（内容没写全 / 长度不对 / contentType 抛异常）在真机上只会表现成
 * 「上传失败」一个红 chip，拿不到原因，所以在这里钉死。
 */
class StreamingFileBodyTest {

    private fun post(body: StreamingFileBody, fileName: String): Pair<Int, ByteArray> {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("{}"))
        server.start()
        try {
            val part = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, body)
                .build()
            val code = OkHttpClient()
                .newCall(Request.Builder().url(server.url("/api/files/upload")).post(part).build())
                .execute()
                .use { it.code }
            val recorded = server.takeRequest()
            val sent = recorded.body.readByteArray()
            assertEquals(
                "Content-Length must match the bytes actually written",
                recorded.getHeader("Content-Length")?.toLong(),
                sent.size.toLong(),
            )
            return code to sent
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `streams the whole file with an exact content length`() {
        val payload = ByteArray(64 * 1024) { (it % 251).toByte() }

        val (code, sent) = post(
            body = StreamingFileBody("image/jpeg", payload.size.toLong()) {
                ByteArrayInputStream(payload)
            },
            fileName = "photo.jpg",
        )

        assertEquals(200, code)
        assertTrue("multipart form field expected", sent.containsAscii("name=\"file\""))
        assertTrue("part content type expected", sent.containsAscii("image/jpeg"))
        assertTrue("file bytes must be present verbatim", sent.containsBytes(payload))
    }

    @Test
    fun `invalid mime falls back instead of throwing on the io thread`() {
        val payload = "hello".toByteArray()

        val (code, sent) = post(
            body = StreamingFileBody("not a mime", payload.size.toLong()) {
                ByteArrayInputStream(payload)
            },
            fileName = "note.txt",
        )

        assertEquals(200, code)
        assertTrue(sent.containsAscii("application/octet-stream"))
    }

    @Test
    fun `zero length body still produces a valid request`() {
        val (code, _) = post(
            body = StreamingFileBody("text/plain", 0L) { ByteArrayInputStream(ByteArray(0)) },
            fileName = "empty.txt",
        )

        assertEquals(200, code)
    }
}

private fun ByteArray.containsAscii(needle: String): Boolean =
    String(this, Charsets.ISO_8859_1).contains(needle)

private fun ByteArray.containsBytes(needle: ByteArray): Boolean {
    if (needle.isEmpty() || needle.size > size) return false
    outer@ for (start in 0..(size - needle.size)) {
        for (offset in needle.indices) {
            if (this[start + offset] != needle[offset]) continue@outer
        }
        return true
    }
    return false
}
