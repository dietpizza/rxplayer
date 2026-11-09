package com.dietpizza.rxplayer.core.netfs.playback

import com.dietpizza.rxplayer.core.netfs.fs.RemoteRandomAccess

/**
 * Buffered sequential reader over a [RemoteRandomAccess], starting at [start]. Pure logic (no
 * Media3 types) so it's unit-testable.
 *
 * Refills start small right after an open (a seek wants its first bytes fast) and double up to
 * [maxBuffer] while reading carries on, so steady playback makes few, large requests.
 */
class RemoteReader(
    private val access: RemoteRandomAccess,
    start: Long,
    /** Exclusive end offset, or -1 for "until the server says EOF". */
    private val end: Long,
    private val minBuffer: Int = MIN_BUFFER,
    private val maxBuffer: Int = MAX_BUFFER,
) {
    private val buffer = ByteArray(maxBuffer)
    private var bufStart = 0
    private var bufEnd = 0
    private var nextFill = minBuffer

    /** Offset of the next byte [read] returns. */
    var position: Long = start
        private set

    /** File offset the next refill reads from. */
    private var fetchPosition = start

    /** Reads up to [length] bytes into [target]; -1 at end of data. */
    fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (end >= 0 && position >= end) return -1
        if (bufStart == bufEnd && !fill()) return -1
        val n = minOf(length, bufEnd - bufStart).let { if (end >= 0) minOf(it.toLong(), end - position).toInt() else it }
        System.arraycopy(buffer, bufStart, target, offset, n)
        bufStart += n
        position += n
        return n
    }

    private fun fill(): Boolean {
        var want = nextFill
        if (end >= 0) want = minOf(want.toLong(), end - fetchPosition).toInt()
        if (want <= 0) return false
        val n = access.read(fetchPosition, buffer, 0, want)
        if (n <= 0) return false
        bufStart = 0
        bufEnd = n
        fetchPosition += n
        nextFill = minOf(nextFill * 2, maxBuffer)
        return true
    }

    companion object {
        const val MIN_BUFFER = 64 * 1024
        const val MAX_BUFFER = 1024 * 1024
    }
}
