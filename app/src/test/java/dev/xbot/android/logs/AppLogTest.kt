package dev.xbot.android.logs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLogTest {

    @Test
    fun `ring keeps the newest lines when over capacity`() {
        val ring = LogRing(capacity = 3)

        ring.add("a")
        ring.add("b")
        ring.add("c")
        ring.add("d")

        assertEquals(listOf("b", "c", "d"), ring.snapshot())
        assertEquals(3, ring.size())
    }

    @Test
    fun `ring clear empties everything`() {
        val ring = LogRing(capacity = 5)
        ring.add("a")

        ring.clear()

        assertTrue(ring.snapshot().isEmpty())
    }

    @Test
    fun `formatted line carries level tag and message`() {
        val line = formatLogLine(0L, "I", "upload", "photo.jpg 2MB")

        assertTrue(line.contains("I/upload: photo.jpg 2MB"))
        assertTrue(line.startsWith("["))
    }

    @Test
    fun `throwable is appended as a stack trace`() {
        val line = formatLogLine(
            System.currentTimeMillis(),
            "E",
            "http",
            "POST /api/files/upload 失败",
            IllegalStateException("boom"),
        )

        assertTrue(line.contains("E/http: POST /api/files/upload 失败"))
        assertTrue(line.contains("IllegalStateException: boom"))
        assertTrue(line.contains("AppLogTest"))
    }

    @Test
    fun `secrets are redacted before they reach the log`() {
        val raw = "Cookie: SESSION=abc123; password=hunter2 access_token=xyz token: t-9"

        val redacted = redactSecrets(raw)

        assertFalse(redacted.contains("abc123"))
        assertFalse(redacted.contains("hunter2"))
        assertFalse(redacted.contains("xyz"))
        assertFalse(redacted.contains("t-9"))
        assertTrue(redacted.contains("Cookie=***"))
        assertTrue(redacted.contains("password=***"))
        assertTrue(redacted.contains("access_token=***"))
    }

    @Test
    fun `redaction leaves ordinary text alone`() {
        val raw = "GET /api/sessions → 200 1024B"

        assertEquals(raw, redactSecrets(raw))
    }

    @Test
    fun `redacted secrets also apply to formatted lines`() {
        val line = formatLogLine(0L, "W", "http", "signature=abcdef123456 body={}")

        assertFalse(line.contains("abcdef123456"))
        assertTrue(line.contains("signature=***"))
    }
}
