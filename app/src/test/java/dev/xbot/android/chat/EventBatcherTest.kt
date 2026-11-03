package dev.xbot.android.chat

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventBatcherTest {

    @Test
    fun `first event is emitted immediately (leading)`() = runTest {
        val source = flow { emit(1) }

        val batches = source.batchByWindow(windowMs = 60, maxBatch = 8).toList()

        assertEquals(listOf(listOf(1)), batches)
    }

    @Test
    fun `a burst becomes the first event plus one trailing batch`() = runTest {
        val source = flow {
            emit(1)
            emit(2)
            emit(3)
        }

        val batches = source.batchByWindow(windowMs = 60, maxBatch = 8).toList()

        // 1 是 leading，2、3 落在同一个窗口里
        assertEquals(listOf(listOf(1), listOf(2, 3)), batches)
    }

    @Test
    fun `events separated by the window are flushed separately`() = runTest {
        val source = flow {
            emit(1)
            delay(200)
            emit(2)
        }

        val batches = source.batchByWindow(windowMs = 60, maxBatch = 8).toList()

        assertEquals(listOf(listOf(1), listOf(2)), batches)
    }

    @Test
    fun `max batch size splits a long burst`() = runTest {
        val source = flow { repeat(5) { emit(it) } }

        val batches = source.batchByWindow(windowMs = 1_000, maxBatch = 3).toList()

        assertEquals(listOf(listOf(0), listOf(1, 2, 3), listOf(4)), batches)
    }

    @Test
    fun `no events no batches`() = runTest {
        val batches = flow<Int> {}.batchByWindow().toList()

        assertTrue(batches.isEmpty())
    }

    @Test
    fun `batching preserves order and loses nothing`() = runTest {
        val source = flow {
            repeat(20) {
                emit(it)
                delay(5)
            }
        }

        val batches = source.batchByWindow(windowMs = 30, maxBatch = 8).toList()

        assertEquals((0 until 20).toList(), batches.flatten())
        assertTrue(batches.all { it.isNotEmpty() })
    }

    @Test
    fun `default window keeps the streaming rate around 16 per second`() = runTest {
        // 200 个事件、每个间隔 1ms：合批后批次数应该远小于事件数
        val source = flow {
            repeat(200) {
                emit(it)
                delay(1)
            }
        }

        val batches = source.batchByWindow().toList()

        assertTrue("expected far fewer batches than events, got ${batches.size}", batches.size < 40)
        assertEquals(200, batches.sumOf { it.size })
    }
}
