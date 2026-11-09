package com.dietpizza.rxplayer.core.player.preview

/**
 * How densely to sample seek-preview frames (plan §6.4: one frame every N seconds, N adapted to
 * the duration). Aims for about [TARGET_FRAMES] frames on "nice" steps, never more than
 * [MAX_FRAMES], so a sheet stays a few MB whatever the length.
 */
object SpriteInterval {
    const val TARGET_FRAMES = 120
    const val MAX_FRAMES = 200

    private val STEPS_MS = longArrayOf(
        1_000, 2_000, 5_000, 10_000, 15_000, 20_000, 30_000, 60_000, 120_000, 300_000,
    )

    /** Sampling interval for a video of [durationMs]. */
    fun intervalMs(durationMs: Long): Long {
        if (durationMs <= 0) return STEPS_MS[0]
        for (step in STEPS_MS) if (durationMs / step <= TARGET_FRAMES) return step
        // Extremely long media: spread MAX_FRAMES evenly (rounded up to whole seconds).
        val raw = (durationMs + MAX_FRAMES - 1) / MAX_FRAMES
        return ((raw + 999) / 1_000) * 1_000
    }

    /** Frames at 0, interval, 2·interval … strictly before [durationMs]. */
    fun frameCount(durationMs: Long, intervalMs: Long): Int {
        if (durationMs <= 0 || intervalMs <= 0) return 0
        return ((durationMs - 1) / intervalMs + 1).coerceAtMost(MAX_FRAMES.toLong()).toInt()
    }

    /** The frame nearest to [positionMs]. */
    fun indexFor(positionMs: Long, intervalMs: Long, count: Int): Int {
        if (count <= 0 || intervalMs <= 0) return -1
        val i = (positionMs.coerceAtLeast(0) + intervalMs / 2) / intervalMs
        return i.coerceAtMost((count - 1).toLong()).toInt()
    }

    /**
     * Coarse-to-fine generation order (every 16th frame, then 8th, 4th, 2nd, the rest), so the
     * whole timeline gets an approximate preview within the first few frames.
     */
    fun generationOrder(count: Int): IntArray {
        val out = IntArray(count)
        if (count == 0) return out
        val seen = BooleanArray(count)
        var n = 0
        var stride = 16
        while (stride >= 1) {
            var i = 0
            while (i < count) {
                if (!seen[i]) {
                    seen[i] = true
                    out[n++] = i
                }
                i += stride
            }
            stride /= 2
        }
        return out
    }
}
