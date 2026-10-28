package dev.xbot.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageUrlTest {

    private val base = "http://192.168.0.103:16000"

    @Test
    fun `relative api path is joined to server base`() {
        assertEquals(
            "$base/api/files/download?key=uploads%2F1%2Fa.png&inline=1",
            resolveImageUrl(base, "/api/files/download?key=uploads%2F1%2Fa.png&inline=1"),
        )
    }

    @Test
    fun `absolute https url is unchanged`() {
        assertEquals(
            "https://example.com/a.png",
            resolveImageUrl(base, "https://example.com/a.png"),
        )
    }

    @Test
    fun `localhost absolute url is rewritten to the configured media base`() {
        assertEquals(
            "https://media.example.com/uploads/a.png",
            resolveImageUrl(
                base,
                "http://localhost:9000/uploads/a.png",
                "https://media.example.com",
            ),
        )
    }

    @Test
    fun `localhost without a media base is rejected`() {
        assertNull(resolveImageUrl(base, "http://localhost:9000/uploads/a.png", ""))
    }

    @Test
    fun `relative path without slash is joined`() {
        assertEquals("$base/uploads/a.png", resolveImageUrl(base, "uploads/a.png"))
    }

    @Test
    fun `dangerous schemes are rejected`() {
        assertNull(resolveImageUrl(base, "javascript:alert(1)"))
        assertNull(resolveImageUrl(base, "file:///etc/passwd"))
        assertNull(resolveImageUrl(base, "data:image/png;base64,AAAA"))
    }

    @Test
    fun `relative path without base is rejected`() {
        assertNull(resolveImageUrl("", "/api/files/download?key=x"))
    }
}
