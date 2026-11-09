package com.dietpizza.rxplayer.feature.player.ui

import kotlin.math.abs

/** `m:ss` or `h:mm:ss`. Negative values clamp to 0. */
internal fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        "$h:${m.pad()}:${s.pad()}"
    } else {
        "$m:${s.pad()}"
    }
}

/** Signed delta for the swipe-seek overlay: `+0:42`, `-1:05:00`. */
internal fun formatDelta(ms: Long): String = (if (ms < 0) "-" else "+") + formatTime(abs(ms))

private fun Long.pad(): String = if (this < 10) "0$this" else toString()
