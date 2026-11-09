package com.dietpizza.rxplayer.core.player

/**
 * Live-seek throttle (decision 31): while dragging, at most one fast (closest-sync) seek is in
 * flight; newer targets overwrite a single pending slot and are sent once the in-flight seek
 * lands. Releasing sends one exact seek. Pure Kotlin: the owner reports completion via
 * [onSeekProcessed] and drives the safety timeout with [onTick].
 */
class SeekCoalescer(
    private val seeker: Seeker,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    fun interface Seeker {
        fun seek(positionMs: Long, exact: Boolean)
    }

    var inFlight: Boolean = false
        private set
    private var inFlightSince = 0L
    private var pending = NONE
    private var lastSent = NONE

    val hasPending: Boolean get() = pending != NONE

    /** A drag moved to [targetMs]. */
    fun request(targetMs: Long, nowMs: Long) {
        if (inFlight) {
            pending = if (targetMs == lastSent) NONE else targetMs
            return
        }
        if (targetMs == lastSent) return
        send(targetMs, nowMs)
    }

    /** The in-flight seek finished (player READY again / frame rendered). */
    fun onSeekProcessed(nowMs: Long) {
        if (!inFlight) return
        inFlight = false
        val next = pending
        pending = NONE
        if (next != NONE) send(next, nowMs)
    }

    /** Treats a seek that never reports back as done after [timeoutMs]. */
    fun onTick(nowMs: Long) {
        if (inFlight && nowMs - inFlightSince >= timeoutMs) onSeekProcessed(nowMs)
    }

    /** Drag released: drop anything pending and do one exact seek. */
    fun commit(targetMs: Long) {
        pending = NONE
        inFlight = false
        lastSent = NONE
        seeker.seek(targetMs, exact = true)
    }

    /** Drag abandoned without a final seek. */
    fun reset() {
        pending = NONE
        inFlight = false
        lastSent = NONE
    }

    private fun send(targetMs: Long, nowMs: Long) {
        inFlight = true
        inFlightSince = nowMs
        lastSent = targetMs
        seeker.seek(targetMs, exact = false)
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 500L
        private const val NONE = Long.MIN_VALUE
    }
}
