package com.dietpizza.rxplayer.feature.player

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import kotlin.math.roundToInt

/**
 * Debug HUD numbers for one morph (start of tap/drag/back until the spring settles).
 *
 * Two independent frame sources:
 *  - Choreographer deltas: how regularly the main thread got a vsync callback. A delta of ~2x the
 *    vsync period is one missed vsync. This is what the user perceives as a stutter in the morph.
 *  - FrameMetrics (per window, RenderThread side): frames whose TOTAL_DURATION exceeded the
 *    DEADLINE the system gave them, i.e. frames that missed their present. Filtered to frames whose
 *    intended vsync falls inside the morph window.
 *
 * Player-side counters catch the surface-specific failure modes: a surface that gets destroyed
 * (onSurfaceSizeChanged 0x0 -> black flash), a re-render of the first frame (surface swapped), and
 * any rebuffer (STATE_BUFFERING) during the morph.
 */
internal class MorphSpikeStats(private val tag: String = "MorphSpike") {

    data class Result(
        val label: String,
        val durationMs: Int,
        val vsyncMs: Float,
        val frames: Int,
        val avgMs: Float,
        val p95Ms: Float,
        val maxMs: Float,
        val missedVsyncs: Int,
        val fmFrames: Int,
        val fmJanky: Int,
        val fmMaxMs: Float,
        val surfaceResizes: Int,
        val surfaceLost: Int,
        val firstFrames: Int,
        val buffering: Int,
    ) {
        fun summary(): String =
            "$label ${durationMs}ms | choreo $frames fr avg ${avgMs.f1} p95 ${p95Ms.f1} max ${maxMs.f1} " +
                "missed $missedVsyncs | fm $fmFrames fr janky $fmJanky max ${fmMaxMs.f1} | " +
                "resize $surfaceResizes lost $surfaceLost firstFrame $firstFrames buf $buffering"
    }

    var recording by mutableStateOf(false)
        private set
    var last by mutableStateOf<Result?>(null)
        private set
    val history = mutableStateListOf<Result>()

    // Live (non-windowed) player info for the HUD.
    var surfaceSize by mutableStateOf("?")
        private set
    var lifetimeSurfaceLost by mutableIntStateOf(0)
        private set
    var lifetimeFirstFrames by mutableIntStateOf(0)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var label = ""
    private var startNs = 0L
    private var vsyncNs = 16_666_667L
    private val deltas = ArrayList<Long>(512)
    private var lastFrameNs = 0L
    private var surfaceResizes = 0
    private var surfaceLost = 0
    private var firstFrames = 0
    private var buffering = 0

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!recording) return
            if (lastFrameNs != 0L) deltas += frameTimeNanos - lastFrameNs
            lastFrameNs = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    // FrameMetrics arrive on a background thread, slightly after the frame.
    private val fmLock = Any()
    private val fmFrames = ArrayList<LongArray>(512) // [intendedVsync, total, deadline]
    private var fmThread: HandlerThread? = null
    private val fmListener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        val row = longArrayOf(
            metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),
            metrics.getMetric(FrameMetrics.TOTAL_DURATION),
            metrics.getMetric(FrameMetrics.DEADLINE),
        )
        synchronized(fmLock) { if (fmFrames.size < 4096) fmFrames += row }
    }

    val playerListener = object : Player.Listener {
        override fun onSurfaceSizeChanged(width: Int, height: Int) {
            surfaceSize = "${width}x$height"
            if (width == 0 && height == 0) {
                lifetimeSurfaceLost++
                if (recording) surfaceLost++
            } else if (recording) {
                surfaceResizes++
            }
        }

        override fun onRenderedFirstFrame() {
            lifetimeFirstFrames++
            if (recording) firstFrames++
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (recording && playbackState == Player.STATE_BUFFERING) buffering++
        }
    }

    fun attach(window: Window) {
        val thread = HandlerThread("morph-frame-metrics").also { it.start() }
        fmThread = thread
        window.addOnFrameMetricsAvailableListener(fmListener, Handler(thread.looper))
    }

    fun detach(window: Window) {
        runCatching { window.removeOnFrameMetricsAvailableListener(fmListener) }
        fmThread?.quitSafely()
        fmThread = null
    }

    /** Starts a window unless one is already running (a drag that turns into a fling keeps one window). */
    fun start(label: String, refreshRate: Float) {
        if (recording) return
        main.removeCallbacksAndMessages(null)
        this.label = label
        vsyncNs = (1_000_000_000.0 / refreshRate.coerceAtLeast(1f)).toLong()
        deltas.clear()
        lastFrameNs = 0L
        surfaceResizes = 0; surfaceLost = 0; firstFrames = 0; buffering = 0
        synchronized(fmLock) { fmFrames.clear() }
        startNs = System.nanoTime()
        recording = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun relabel(label: String) {
        if (recording) this.label = label
    }

    fun stop() {
        if (!recording) return
        recording = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        val endNs = System.nanoTime()
        // Let the trailing FrameMetrics reports for the last frames arrive before computing.
        main.postDelayed({ finish(endNs) }, 300)
    }

    private fun finish(endNs: Long) {
        val d = deltas.sorted()
        val ms = { ns: Long -> ns / 1_000_000f }
        val missed = deltas.sumOf { ((it.toDouble() / vsyncNs).roundToInt() - 1).coerceAtLeast(0) }
        val fm = synchronized(fmLock) { fmFrames.filter { it[0] in startNs..endNs } }
        val result = Result(
            label = label,
            durationMs = ((endNs - startNs) / 1_000_000).toInt(),
            vsyncMs = ms(vsyncNs),
            frames = d.size,
            avgMs = if (d.isEmpty()) 0f else ms(d.sum() / d.size),
            p95Ms = if (d.isEmpty()) 0f else ms(d[((d.size - 1) * 0.95).toInt()]),
            maxMs = if (d.isEmpty()) 0f else ms(d.last()),
            missedVsyncs = missed,
            fmFrames = fm.size,
            fmJanky = fm.count { it[1] > it[2] },
            fmMaxMs = fm.maxOfOrNull { ms(it[1]) } ?: 0f,
            surfaceResizes = surfaceResizes,
            surfaceLost = surfaceLost,
            firstFrames = firstFrames,
            buffering = buffering,
        )
        last = result
        history.add(0, result)
        while (history.size > 6) history.removeAt(history.lastIndex)
        Log.i(tag, result.summary())
    }
}

private val Float.f1: String get() = "%.1f".format(this)
