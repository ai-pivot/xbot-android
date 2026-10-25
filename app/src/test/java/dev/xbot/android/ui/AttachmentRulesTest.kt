package dev.xbot.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentRulesTest {

    private fun pick(name: String, size: Long) = AttachmentPick(name, size)

    @Test
    fun `accepts everything when within limits`() {
        val plan = planAttachmentPick(
            existingCount = 0,
            picks = listOf(pick("a.txt", 10), pick("b.png", 2048)),
        )

        assertEquals(listOf(0, 1), plan.acceptedIndices)
        assertTrue(plan.rejected.isEmpty())
    }

    @Test
    fun `rejects empty files`() {
        val plan = planAttachmentPick(
            existingCount = 0,
            picks = listOf(pick("empty.txt", 0), pick("ok.txt", 1)),
        )

        assertEquals(listOf(1), plan.acceptedIndices)
        assertEquals(1, plan.rejected.size)
        assertEquals("空文件", plan.rejected.first().reason)
    }

    @Test
    fun `rejects files above the server upload limit`() {
        val plan = planAttachmentPick(
            existingCount = 0,
            picks = listOf(pick("big.bin", MAX_ATTACHMENT_BYTES + 1), pick("edge.bin", MAX_ATTACHMENT_BYTES)),
        )

        assertEquals(listOf(1), plan.acceptedIndices)
        assertEquals("超过 10MB", plan.rejected.single().reason)
    }

    @Test
    fun `stops at the max count and reports the rest`() {
        val picks = (1..MAX_ATTACHMENTS + 2).map { pick("f$it.txt", 10) }

        val plan = planAttachmentPick(existingCount = 0, picks = picks)

        assertEquals((0 until MAX_ATTACHMENTS).toList(), plan.acceptedIndices)
        assertEquals(2, plan.rejected.size)
        assertEquals("最多 $MAX_ATTACHMENTS 个附件", plan.rejected.first().reason)
    }

    @Test
    fun `existing attachments consume the quota`() {
        val picks = listOf(pick("a.txt", 10), pick("b.txt", 10), pick("c.txt", 10))

        val plan = planAttachmentPick(existingCount = MAX_ATTACHMENTS - 1, picks = picks)

        assertEquals(listOf(0), plan.acceptedIndices)
        assertEquals(2, plan.rejected.size)
    }

    @Test
    fun `already full quota rejects every pick`() {
        val plan = planAttachmentPick(
            existingCount = MAX_ATTACHMENTS,
            picks = listOf(pick("a.txt", 10)),
        )

        assertTrue(plan.acceptedIndices.isEmpty())
        assertEquals(1, plan.rejected.size)
    }

    @Test
    fun `rejection message lists name and reason`() {
        val plan = planAttachmentPick(
            existingCount = 0,
            picks = listOf(pick("huge.bin", MAX_ATTACHMENT_BYTES + 1)),
        )

        assertEquals("已跳过 1 个文件：huge.bin（超过 10MB）", attachmentRejectionMessage(plan.rejected))
        assertNull(attachmentRejectionMessage(emptyList()))
    }
}
