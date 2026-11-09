package com.dietpizza.rxplayer.core.netfs.fs

import java.io.IOException
import java.io.InputStream

/**
 * [RemoteRandomAccess] for protocols that read through a stream opened at an offset (FTP `REST`,
 * HTTP `Range`, SFTP read-ahead). Sequential reads continue the open stream; a short forward jump
 * skips within it; anything else closes it and reopens at the new offset.
 */
abstract class StreamingRandomAccess(
    /** Forward gaps up to this many bytes are skipped in-stream instead of reopening. */
    private val skipThreshold: Long = DEFAULT_SKIP_THRESHOLD,
) : RemoteRandomAccess {

    private var stream: InputStream? = null
    private var streamPosition = -1L
    private var closed = false

    /** Opens a stream that yields the file's bytes from [offset]. */
    protected abstract fun openStream(offset: Long): InputStream

    /** Ends an in-progress stream. Default closes it; FTP also finishes the pending command. */
    protected open fun closeStream(stream: InputStream) {
        stream.close()
    }

    /** Releases the underlying connection/handle once no stream is open. */
    protected abstract fun release()

    override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (closed) throw IOException("Handle closed")
        if (length == 0) return 0
        val total = this.length
        if (total >= 0 && position >= total) return -1

        var s = stream
        if (s != null && position != streamPosition) {
            val gap = position - streamPosition
            if (gap in 1..skipThreshold && skipFully(s, gap)) {
                streamPosition = position
            } else {
                dropStream()
                s = null
            }
        }
        if (s == null) {
            s = openStream(position)
            stream = s
            streamPosition = position
        }
        val n = try {
            s.read(buffer, offset, length)
        } catch (e: IOException) {
            dropStream()
            throw e
        }
        if (n > 0) streamPosition += n
        return n
    }

    private fun skipFully(s: InputStream, count: Long): Boolean {
        var remaining = count
        val scratch = ByteArray(minOf(count, SKIP_BUFFER.toLong()).toInt())
        while (remaining > 0) {
            val n = s.read(scratch, 0, minOf(remaining, scratch.size.toLong()).toInt())
            if (n < 0) return false
            remaining -= n
        }
        return true
    }

    private fun dropStream() {
        val s = stream ?: return
        stream = null
        streamPosition = -1L
        try {
            closeStream(s)
        } catch (_: IOException) {
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        dropStream()
        release()
    }

    companion object {
        const val DEFAULT_SKIP_THRESHOLD = 256L * 1024
        private const val SKIP_BUFFER = 64 * 1024
    }
}
