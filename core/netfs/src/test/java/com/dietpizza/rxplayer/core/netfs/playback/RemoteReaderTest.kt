package com.dietpizza.rxplayer.core.netfs.playback

import com.dietpizza.rxplayer.core.netfs.fs.RemoteRandomAccess
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** In-memory file that records every read request; [maxChunk] caps one response like SMB's max read size. */
class FakeRandomAccess(val data: ByteArray, private val maxChunk: Int = Int.MAX_VALUE) : RemoteRandomAccess {
    val requests = ArrayList<Pair<Long, Int>>()
    var closed = false
    override val length: Long get() = data.size.toLong()

    override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        requests += position to length
        if (position >= data.size) return -1
        val n = minOf(length, maxChunk, data.size - position.toInt())
        System.arraycopy(data, position.toInt(), buffer, offset, n)
        return n
    }

    override fun close() {
        closed = true
    }
}

class RemoteReaderTest {
    private val data = ByteArray(1_000_000) { (it * 31 + 7).toByte() }

    private fun readAll(reader: RemoteReader, chunk: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(chunk)
        while (true) {
            val n = reader.read(buf, 0, chunk)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    @Test fun `reads the whole file in order`() {
        val reader = RemoteReader(FakeRandomAccess(data), start = 0, end = data.size.toLong(), minBuffer = 1000, maxBuffer = 64_000)
        assertArrayEquals(data, readAll(reader, 4096))
        assertEquals(data.size.toLong(), reader.position)
    }

    @Test fun `starts at the seek position`() {
        val start = 123_457L
        val reader = RemoteReader(FakeRandomAccess(data), start, -1, minBuffer = 1000, maxBuffer = 64_000)
        assertArrayEquals(data.copyOfRange(start.toInt(), data.size), readAll(reader, 3000))
    }

    @Test fun `stops at the end bound`() {
        val reader = RemoteReader(FakeRandomAccess(data), 10, 5010, minBuffer = 1000, maxBuffer = 64_000)
        assertArrayEquals(data.copyOfRange(10, 5010), readAll(reader, 777))
    }

    @Test fun `first refill is small, later ones grow to the max`() {
        val fake = FakeRandomAccess(data)
        val reader = RemoteReader(fake, 0, -1, minBuffer = 1000, maxBuffer = 8000)
        readAll(reader, 500)
        val sizes = fake.requests.map { it.second }
        assertEquals(listOf(1000, 2000, 4000, 8000, 8000), sizes.take(5))
    }

    @Test fun `requests never cross the end bound`() {
        val fake = FakeRandomAccess(data)
        val reader = RemoteReader(fake, 0, 2500, minBuffer = 1000, maxBuffer = 8000)
        readAll(reader, 4096)
        assertEquals(listOf(0L to 1000, 1000L to 1500), fake.requests)
    }

    @Test fun `short server reads are fine`() {
        val fake = FakeRandomAccess(data, maxChunk = 333)
        val reader = RemoteReader(fake, 5, -1, minBuffer = 1000, maxBuffer = 8000)
        assertArrayEquals(data.copyOfRange(5, data.size), readAll(reader, 4096))
        // Each refill continues exactly where the last one ended.
        fake.requests.dropLast(1).forEachIndexed { i, r -> assertEquals(5L + 333L * i, r.first) }
    }

    @Test fun `end of file returns -1`() {
        val reader = RemoteReader(FakeRandomAccess(ByteArray(10)), 10, -1)
        assertEquals(-1, reader.read(ByteArray(10), 0, 10))
    }

    @Test fun `zero-length read`() {
        val fake = FakeRandomAccess(data)
        val reader = RemoteReader(fake, 0, -1)
        assertEquals(0, reader.read(ByteArray(10), 0, 0))
        assertEquals(0, fake.requests.size)
    }
}
