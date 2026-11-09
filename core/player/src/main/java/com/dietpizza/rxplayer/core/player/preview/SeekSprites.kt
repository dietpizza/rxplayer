package com.dietpizza.rxplayer.core.player.preview

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A seek-preview sprite sheet for one video: [count] frames, [intervalMs] apart, laid out
 * [columns] per row in [sheet]. Frames fill in progressively (coarse to fine) on a background
 * thread; [readyCount] ticks as they land. The UI draws a sub-rect of [image]; lookups are
 * allocation-free.
 */
class SeekSprites internal constructor(
    val intervalMs: Long,
    val count: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val columns: Int,
    internal val sheet: Bitmap,
    allReady: Boolean,
) {
    val image: ImageBitmap = sheet.asImageBitmap()

    private val ready = AtomicIntegerArray(count).also { if (allReady) for (i in 0 until count) it.set(i, 1) }

    private val _readyCount = MutableStateFlow(if (allReady) count else 0)

    /** Number of frames ready; observe it to redraw while frames arrive. */
    val readyCount: StateFlow<Int> = _readyCount.asStateFlow()

    internal fun markReady(index: Int) {
        if (ready.getAndSet(index, 1) == 0) _readyCount.value = _readyCount.value + 1
    }

    fun isReady(index: Int): Boolean = index in 0 until count && ready.get(index) == 1

    /** Index of the ready frame nearest to [positionMs], or -1 if none is ready yet. */
    fun nearestReady(positionMs: Long): Int {
        val target = SpriteInterval.indexFor(positionMs, intervalMs, count)
        if (target < 0) return -1
        var d = 0
        while (d < count) {
            val lo = target - d
            val hi = target + d
            if (lo >= 0 && ready.get(lo) == 1) return lo
            if (hi < count && ready.get(hi) == 1) return hi
            if (lo < 0 && hi >= count) break
            d++
        }
        return -1
    }

    fun srcX(index: Int): Int = (index % columns) * frameWidth
    fun srcY(index: Int): Int = (index / columns) * frameHeight
}
