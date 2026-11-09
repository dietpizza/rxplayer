package com.dietpizza.rxplayer.core.player.source

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FragmentIndexScannerTest {

    // region tiny MP4 writer

    private fun box(type: String, vararg children: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().apply { children.forEach { write(it) } }.toByteArray()
        return u32(8 + body.size) + type.toByteArray() + body
    }

    private fun fullBox(type: String, version: Int, payload: ByteArray) =
        box(type, byteArrayOf(version.toByte(), 0, 0, 0) + payload)

    private fun u32(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    private fun u32(v: Long) = u32(v.toInt())
    private fun u64(v: Long) = u32((v ushr 32).toInt()) + u32(v.toInt())
    private fun zeros(n: Int) = ByteArray(n)

    private fun trak(id: Int, timescale: Int, handler: String) = box(
        "trak",
        fullBox("tkhd", 0, u32(0) + u32(0) + u32(id) + zeros(68)),
        box(
            "mdia",
            fullBox("mdhd", 0, u32(0) + u32(0) + u32(timescale) + u32(0) + zeros(4)),
            fullBox("hdlr", 0, u32(0) + handler.toByteArray() + zeros(12)),
        ),
    )

    private fun moov(fragmented: Boolean = true, durationSec: Int = 0) = box(
        "moov",
        fullBox("mvhd", 0, u32(0) + u32(0) + u32(1000) + u32(durationSec * 1000) + zeros(80)),
        trak(1, 90_000, "vide"),
        trak(2, 48_000, "soun"),
        *(if (fragmented) arrayOf(box("mvex")) else emptyArray()),
    )

    private fun moof(track: Int, decodeTime: Long, version: Int = 1) = box(
        "moof",
        fullBox("mfhd", 0, u32(1)),
        box(
            "traf",
            fullBox("tfhd", 0, u32(track)),
            fullBox("tfdt", version, if (version == 1) u64(decodeTime) else u32(decodeTime)),
        ),
    )

    private fun mdat(bytes: Int) = box("mdat", zeros(bytes))

    private fun source(bytes: ByteArray) = object : ByteSource {
        override val size: Long = bytes.size.toLong()
        override fun read(position: Long, buf: ByteArray, len: Int): Boolean {
            if (position < 0 || position + len > bytes.size) return false
            System.arraycopy(bytes, position.toInt(), buf, 0, len)
            return true
        }
    }

    private fun file(vararg parts: ByteArray) = ByteArrayOutputStream().apply { parts.forEach { write(it) } }.toByteArray()

    // endregion

    @Test
    fun indexesVideoFragmentsInMicroseconds() {
        val head = file(box("ftyp", zeros(8)), moov(durationSec = 6))
        val f1 = moof(1, 0); val m1 = mdat(1000)
        val a1 = moof(2, 0); val am1 = mdat(100) // audio-only fragment: not a seek point
        val f2 = moof(1, 180_000); val m2 = mdat(1000) // 2 s at 90 kHz
        val f3 = moof(1, 360_000, version = 0); val m3 = mdat(1000)
        val bytes = file(head, f1, m1, a1, am1, f2, m2, f3, m3)

        val index = FragmentIndexScanner.scan(source(bytes))
        assertNotNull(index)
        index!!
        val o1 = head.size.toLong()
        val o2 = o1 + f1.size + m1.size + a1.size + am1.size
        val o3 = o2 + f2.size + m2.size
        assertArrayEquals(longArrayOf(o1, o2, o3), index.offsets)
        assertArrayEquals(longArrayOf(0, 2_000_000, 4_000_000), index.timesUs)
        // Last fragment runs to the 6 s movie duration.
        assertArrayEquals(longArrayOf(2_000_000, 2_000_000, 2_000_000), index.durationsUs)
        assertEquals((bytes.size - o3).toInt(), index.sizes[2])
    }

    @Test
    fun leavesIndexedFilesToExoPlayer() {
        val bytes = file(moov(), box("sidx", zeros(24)), moof(1, 0), mdat(10), moof(1, 90_000), mdat(10))
        assertNull(FragmentIndexScanner.scan(source(bytes)))
    }

    @Test
    fun ignoresUnfragmentedFiles() {
        val bytes = file(moov(fragmented = false), mdat(100))
        assertNull(FragmentIndexScanner.scan(source(bytes)))
    }

    @Test
    fun needsAtLeastTwoFragments() {
        assertNull(FragmentIndexScanner.scan(source(file(moov(), moof(1, 0), mdat(10)))))
    }

    @Test
    fun rejectsTruncatedOrGarbage() {
        val good = file(moov(), moof(1, 0), mdat(10), moof(1, 90_000), mdat(10))
        assertNull(FragmentIndexScanner.scan(source(good.copyOf(good.size / 3))))
        assertNull(FragmentIndexScanner.scan(source(ByteArray(64) { 0x7f })))
    }
}
