package com.dietpizza.rxplayer.core.player.source

/** Random access to a file's bytes (a [java.io.RandomAccessFile], a FileChannel, a test array). */
internal interface ByteSource {
    val size: Long

    /** Reads exactly [len] bytes at [position] into [buf], or returns false (EOF / error). */
    fun read(position: Long, buf: ByteArray, len: Int): Boolean
}

/** A seek table for a fragmented MP4: one entry per fragment (`moof`), ascending in time. */
internal class FragmentIndex(
    val offsets: LongArray,
    val sizes: IntArray,
    val timesUs: LongArray,
    val durationsUs: LongArray,
) {
    val count: Int get() = offsets.size
}

/**
 * Builds a seek table for a fragmented MP4 that has no `sidx` index (common for downloads
 * saved from streaming sites), which ExoPlayer otherwise treats as unseekable.
 *
 * It walks the top-level boxes reading only headers, skipping every `mdat` payload, so the
 * cost is a few hundred bytes per fragment. Times come from each fragment's `tfdt` for the
 * video track (or the first track when there is no video), using that track's `mdhd`
 * timescale from `moov`.
 *
 * Returns null when the file isn't a fragmented MP4, already has a `sidx` (ExoPlayer seeks
 * with it), or lacks what's needed (no `tfdt`, fewer than two fragments, unordered times).
 */
internal object FragmentIndexScanner {

    /** Upper bounds so a corrupt header can't make us allocate or loop wildly. */
    private const val MAX_MOOV_BYTES = 32L * 1024 * 1024
    private const val MAX_MOOF_BYTES = 4L * 1024 * 1024
    private const val MAX_FRAGMENTS = 200_000

    fun scan(source: ByteSource): FragmentIndex? {
        val header = ByteArray(16)
        var pos = 0L
        val fileSize = source.size
        var tracks: Map<Int, TrackInfo>? = null
        var indexTrack = -1
        var movieDurationUs = -1L
        val offsets = ArrayList<Long>()
        val times = ArrayList<Long>()

        while (pos + 8 <= fileSize) {
            if (!source.read(pos, header, 8)) return null
            var boxSize = u32(header, 0)
            val type = fourcc(header, 4)
            var headerSize = 8
            if (boxSize == 1L) {
                if (!source.read(pos + 8, header, 8)) return null
                boxSize = u64(header, 0)
                headerSize = 16
            } else if (boxSize == 0L) {
                boxSize = fileSize - pos
            }
            if (boxSize < headerSize || pos + boxSize > fileSize + 8) return null

            when (type) {
                "sidx" -> return null // ExoPlayer builds a seek map from this itself.
                "moov" -> {
                    if (boxSize > MAX_MOOV_BYTES) return null
                    val body = readBody(source, pos + headerSize, boxSize - headerSize) ?: return null
                    val movie = parseMoov(body) ?: return null
                    if (!movie.fragmented) return null
                    tracks = movie.tracks
                    indexTrack = movie.tracks.values.firstOrNull { it.isVideo }?.id
                        ?: movie.tracks.keys.minOrNull() ?: return null
                    movieDurationUs = movie.durationUs
                }
                "moof" -> {
                    val known = tracks ?: return null // moof before moov: not something we handle
                    if (boxSize > MAX_MOOF_BYTES) return null
                    val body = readBody(source, pos + headerSize, boxSize - headerSize) ?: return null
                    val decodeTime = moofDecodeTime(body, indexTrack)
                    if (decodeTime != null) {
                        val timescale = known[indexTrack]?.timescale ?: return null
                        val timeUs = scale(decodeTime, timescale)
                        if (times.isNotEmpty() && timeUs < times.last()) return null
                        offsets += pos
                        times += timeUs
                        if (offsets.size > MAX_FRAGMENTS) return null
                    }
                }
            }
            pos += boxSize
        }

        if (offsets.size < 2) return null
        val n = offsets.size
        val sizes = IntArray(n)
        val durations = LongArray(n)
        for (i in 0 until n) {
            val end = if (i + 1 < n) offsets[i + 1] else fileSize
            sizes[i] = (end - offsets[i]).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
            durations[i] = if (i + 1 < n) {
                times[i + 1] - times[i]
            } else {
                // Last fragment: up to the movie duration if known, else the average fragment.
                val average = (times[n - 1] - times[0]) / (n - 1)
                if (movieDurationUs > times[i]) movieDurationUs - times[i] else average
            }
        }
        return FragmentIndex(offsets.toLongArray(), sizes, times.toLongArray(), durations)
    }

    // region moov

    private class TrackInfo(val id: Int, val timescale: Long, val isVideo: Boolean)

    private class Movie(val tracks: Map<Int, TrackInfo>, val fragmented: Boolean, val durationUs: Long)

    private fun parseMoov(moov: ByteArray): Movie? {
        val tracks = HashMap<Int, TrackInfo>()
        var fragmented = false
        var movieTimescale = 0L
        var movieDuration = 0L
        var fragmentDuration = 0L
        forEachChild(moov, 0, moov.size) { type, start, end ->
            when (type) {
                "mvhd" -> {
                    val v = moov[start].toInt()
                    if (v == 1) {
                        movieTimescale = u32(moov, start + 4 + 16)
                        movieDuration = u64(moov, start + 4 + 20)
                    } else {
                        movieTimescale = u32(moov, start + 4 + 8)
                        movieDuration = u32(moov, start + 4 + 12)
                    }
                }
                "mvex" -> {
                    fragmented = true
                    forEachChild(moov, start, end) { t, s, _ ->
                        if (t == "mehd") {
                            fragmentDuration = if (moov[s].toInt() == 1) u64(moov, s + 4) else u32(moov, s + 4)
                        }
                    }
                }
                "trak" -> parseTrak(moov, start, end)?.let { tracks[it.id] = it }
            }
        }
        if (tracks.isEmpty()) return null
        val duration = if (fragmentDuration > 0) fragmentDuration else movieDuration
        val durationUs = if (movieTimescale > 0 && duration > 0) scale(duration, movieTimescale) else -1L
        return Movie(tracks, fragmented, durationUs)
    }

    private fun parseTrak(b: ByteArray, start: Int, end: Int): TrackInfo? {
        var id = -1
        var timescale = 0L
        var handler = ""
        forEachChild(b, start, end) { type, s, e ->
            when (type) {
                "tkhd" -> id = (if (b[s].toInt() == 1) u32(b, s + 4 + 16) else u32(b, s + 4 + 8)).toInt()
                "mdia" -> forEachChild(b, s, e) { t, cs, _ ->
                    when (t) {
                        "mdhd" -> timescale = if (b[cs].toInt() == 1) u32(b, cs + 4 + 16) else u32(b, cs + 4 + 8)
                        "hdlr" -> handler = fourcc(b, cs + 8)
                    }
                }
            }
        }
        if (id < 0 || timescale <= 0) return null
        return TrackInfo(id, timescale, handler == "vide")
    }

    // endregion

    // region moof

    /** `tfdt` base decode time of [trackId]'s `traf` in this `moof`, or null if absent. */
    private fun moofDecodeTime(moof: ByteArray, trackId: Int): Long? {
        var result: Long? = null
        forEachChild(moof, 0, moof.size) { type, start, end ->
            if (type != "traf" || result != null) return@forEachChild
            var tfhdTrack = -1
            var decodeTime: Long? = null
            forEachChild(moof, start, end) { t, s, _ ->
                when (t) {
                    "tfhd" -> tfhdTrack = u32(moof, s + 4).toInt()
                    "tfdt" -> decodeTime = if (moof[s].toInt() == 1) u64(moof, s + 4) else u32(moof, s + 4)
                }
            }
            if (tfhdTrack == trackId) result = decodeTime
        }
        return result
    }

    // endregion

    // region bytes

    /** Calls [block] with (type, payloadStart, payloadEnd) for each child box in [start, end). */
    private inline fun forEachChild(b: ByteArray, start: Int, end: Int, block: (String, Int, Int) -> Unit) {
        var p = start
        while (p + 8 <= end) {
            var size = u32(b, p)
            var header = 8
            if (size == 1L) {
                if (p + 16 > end) return
                size = u64(b, p + 8)
                header = 16
            } else if (size == 0L) {
                size = (end - p).toLong()
            }
            if (size < header || p + size > end) return
            block(fourcc(b, p + 4), p + header, (p + size).toInt())
            p += size.toInt()
        }
    }

    private fun readBody(source: ByteSource, position: Long, length: Long): ByteArray? {
        if (length < 0 || length > Int.MAX_VALUE) return null
        val buf = ByteArray(length.toInt())
        return if (source.read(position, buf, buf.size)) buf else null
    }

    private fun scale(value: Long, timescale: Long): Long =
        if (value <= Long.MAX_VALUE / 1_000_000L) value * 1_000_000L / timescale else value / timescale * 1_000_000L

    private fun u32(b: ByteArray, i: Int): Long =
        ((b[i].toLong() and 0xFF) shl 24) or ((b[i + 1].toLong() and 0xFF) shl 16) or
            ((b[i + 2].toLong() and 0xFF) shl 8) or (b[i + 3].toLong() and 0xFF)

    private fun u64(b: ByteArray, i: Int): Long = (u32(b, i) shl 32) or u32(b, i + 4)

    private fun fourcc(b: ByteArray, i: Int): String =
        String(charArrayOf(b[i].toInt().toChar(), b[i + 1].toInt().toChar(), b[i + 2].toInt().toChar(), b[i + 3].toInt().toChar()))

    // endregion
}
