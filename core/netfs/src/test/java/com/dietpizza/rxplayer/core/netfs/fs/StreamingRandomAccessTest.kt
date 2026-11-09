package com.dietpizza.rxplayer.core.netfs.fs

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingRandomAccessTest {
    private val data = ByteArray(2_000_000) { (it xor (it ushr 8)).toByte() }

    private inner class Fake(skip: Long = 1000) : StreamingRandomAccess(skipThreshold = skip) {
        val opens = ArrayList<Long>()
        var closes = 0
        var released = false
        override val length: Long = data.size.toLong()

        override fun openStream(offset: Long): InputStream {
            opens += offset
            return ByteArrayInputStream(data, offset.toInt(), data.size - offset.toInt())
        }

        override fun closeStream(stream: InputStream) {
            closes++
            stream.close()
        }

        override fun release() {
            released = true
        }
    }

    private fun Fake.readAt(position: Long, n: Int): ByteArray {
        val buf = ByteArray(n)
        var got = 0
        while (got < n) {
            val r = read(position + got, buf, got, n - got)
            if (r < 0) break
            got += r
        }
        return buf.copyOf(got)
    }

    @Test fun `sequential reads reuse one stream`() {
        val f = Fake()
        assertArrayEquals(data.copyOfRange(0, 100), f.readAt(0, 100))
        assertArrayEquals(data.copyOfRange(100, 300), f.readAt(100, 200))
        assertEquals(listOf(0L), f.opens)
    }

    @Test fun `a short forward jump skips in-stream`() {
        val f = Fake(skip = 1000)
        f.readAt(0, 100)
        assertArrayEquals(data.copyOfRange(600, 700), f.readAt(600, 100))
        assertEquals(listOf(0L), f.opens)
    }

    @Test fun `a long jump or a backward seek reopens at the new offset`() {
        val f = Fake(skip = 1000)
        f.readAt(0, 100)
        assertArrayEquals(data.copyOfRange(500_000, 500_100), f.readAt(500_000, 100))
        assertArrayEquals(data.copyOfRange(10, 20), f.readAt(10, 10))
        assertEquals(listOf(0L, 500_000L, 10L), f.opens)
        assertEquals(2, f.closes)
    }

    @Test fun `reading at or past the end is EOF without opening`() {
        val f = Fake()
        assertEquals(-1, f.read(data.size.toLong(), ByteArray(4), 0, 4))
        assertTrue(f.opens.isEmpty())
    }

    @Test fun `close drops the stream and releases`() {
        val f = Fake()
        f.readAt(0, 10)
        f.close()
        assertEquals(1, f.closes)
        assertTrue(f.released)
    }
}
