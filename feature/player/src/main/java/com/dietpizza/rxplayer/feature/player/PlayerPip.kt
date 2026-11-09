package com.dietpizza.rxplayer.feature.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.util.Rational
import com.dietpizza.rxplayer.core.player.PlaybackController
import com.dietpizza.rxplayer.core.player.VideoDimensions
import kotlin.math.roundToInt

/**
 * Auto-PiP (decision 10) for [PlayerActivity]: keeps `PictureInPictureParams` current (aspect
 * from the video, auto-enter only while playing with the setting on, a source-rect hint for a
 * smooth shrink) and serves the play/pause ±seek RemoteActions through a non-exported receiver.
 *
 * No MediaSessionService or notification is needed: PiP is an activity mode, its controls are
 * these RemoteActions, and the activity (with its MediaSession for hardware keys) stays alive
 * and STARTED while in PiP, so decision 28's pause-on-stop never fires there.
 */
internal class PlayerPip(
    private val activity: Activity,
    private val controller: PlaybackController,
) {
    private var registered = false
    private var lastParamsKey = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(EXTRA_CONTROL, 0)) {
                CONTROL_PLAY_PAUSE -> controller.togglePlayPause()
                CONTROL_REWIND -> controller.seekBack()
                CONTROL_FORWARD -> controller.seekForward()
            }
        }
    }

    fun register() {
        if (registered) return
        activity.registerReceiver(receiver, IntentFilter(ACTION_PIP_CONTROL), Context.RECEIVER_NOT_EXPORTED)
        registered = true
    }

    fun unregister() {
        if (!registered) return
        runCatching { activity.unregisterReceiver(receiver) }
        registered = false
    }

    /** Whether the system would accept a PiP request now (something is loaded). */
    val canEnter: Boolean get() = controller.nowPlaying.value != null

    /** Pushes params if anything relevant changed. Main thread. */
    fun update(autoEnter: Boolean, playing: Boolean, size: VideoDimensions, force: Boolean = false) {
        val key = (if (autoEnter) 1L else 0L) or (if (playing) 2L else 0L) or
            (size.width.toLong() shl 2) or (size.height.toLong() shl 22)
        if (!force && key == lastParamsKey) return
        lastParamsKey = key
        runCatching { activity.setPictureInPictureParams(params(autoEnter, playing, size)) }
    }

    fun enter(playing: Boolean, size: VideoDimensions): Boolean =
        runCatching { activity.enterPictureInPictureMode(params(autoEnter = false, playing, size)) }.getOrDefault(false)

    private fun params(autoEnter: Boolean, playing: Boolean, size: VideoDimensions): PictureInPictureParams {
        val b = PictureInPictureParams.Builder()
            .setAutoEnterEnabled(autoEnter)
            // Video: let the system cross-fade instead of re-laying out every resize frame.
            .setSeamlessResizeEnabled(false)
            .setActions(actions(playing))
        aspect(size)?.let { ratio ->
            b.setAspectRatio(ratio)
            sourceRect(size)?.let(b::setSourceRectHint)
        }
        return b.build()
    }

    private fun actions(playing: Boolean): List<RemoteAction> = listOf(
        action(CONTROL_REWIND, R.drawable.ic_pip_rewind, "Back"),
        if (playing) {
            action(CONTROL_PLAY_PAUSE, R.drawable.ic_pip_pause, "Pause")
        } else {
            action(CONTROL_PLAY_PAUSE, R.drawable.ic_pip_play, "Play")
        },
        action(CONTROL_FORWARD, R.drawable.ic_pip_forward, "Forward"),
    )

    private fun action(control: Int, icon: Int, title: String): RemoteAction {
        val intent = Intent(ACTION_PIP_CONTROL).setPackage(activity.packageName).putExtra(EXTRA_CONTROL, control)
        val pi = PendingIntent.getBroadcast(
            activity,
            control,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteAction(Icon.createWithResource(activity, icon), title, title, pi)
    }

    /** The video's fitted rect in the window, so the shrink animation starts from the picture. */
    private fun sourceRect(size: VideoDimensions): Rect? {
        val decor = activity.window.decorView
        val w = decor.width
        val h = decor.height
        if (w <= 0 || h <= 0 || size.width <= 0 || size.height <= 0) return null
        val scale = minOf(w.toFloat() / size.width, h.toFloat() / size.height)
        val vw = (size.width * scale).roundToInt()
        val vh = (size.height * scale).roundToInt()
        val left = (w - vw) / 2
        val top = (h - vh) / 2
        return Rect(left, top, left + vw, top + vh)
    }

    companion object {
        private const val ACTION_PIP_CONTROL = "com.dietpizza.rxplayer.action.PIP_CONTROL"
        private const val EXTRA_CONTROL = "control"
        private const val CONTROL_PLAY_PAUSE = 1
        private const val CONTROL_REWIND = 2
        private const val CONTROL_FORWARD = 3

        /** PiP accepts aspect ratios between 1:2.39 and 2.39:1. */
        private const val MAX_ASPECT = 2.39f

        internal fun aspect(size: VideoDimensions): Rational? {
            if (size.width <= 0 || size.height <= 0) return null
            val a = size.width.toFloat() / size.height
            return when {
                a > MAX_ASPECT -> Rational(239, 100)
                a < 1f / MAX_ASPECT -> Rational(100, 239)
                else -> Rational(size.width, size.height)
            }
        }
    }
}
