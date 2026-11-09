package com.dietpizza.rxplayer.core.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager

/**
 * Self-managed interruptions (plan §3.3): we never hold audio focus, so nothing pauses us for
 * free. Watches headphone unplug (`ACTION_AUDIO_BECOMING_NOISY`) and the audio mode (calls,
 * ringing, VoIP) via `AudioManager.addOnModeChangedListener` (no permission needed).
 */
internal class AutoPauseMonitor(
    private val context: Context,
    private val onBecomingNoisy: () -> Unit,
    private val onCallStateChanged: (inCall: Boolean) -> Unit,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var started = false

    var inCall: Boolean = false
        private set

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onBecomingNoisy()
        }
    }

    private val modeListener = AudioManager.OnModeChangedListener { mode -> update(isCallMode(mode)) }

    fun start() {
        if (started) return
        started = true
        context.registerReceiver(
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            Context.RECEIVER_NOT_EXPORTED,
        )
        audioManager.addOnModeChangedListener(context.mainExecutor, modeListener)
        inCall = isCallMode(audioManager.mode)
    }

    fun stop() {
        if (!started) return
        started = false
        context.unregisterReceiver(noisyReceiver)
        audioManager.removeOnModeChangedListener(modeListener)
        inCall = false
    }

    private fun update(nowInCall: Boolean) {
        if (nowInCall == inCall) return
        inCall = nowInCall
        onCallStateChanged(nowInCall)
    }

    private fun isCallMode(mode: Int): Boolean = when (mode) {
        AudioManager.MODE_IN_CALL,
        AudioManager.MODE_IN_COMMUNICATION,
        AudioManager.MODE_RINGTONE,
        AudioManager.MODE_CALL_SCREENING,
        AudioManager.MODE_CALL_REDIRECT,
        AudioManager.MODE_COMMUNICATION_REDIRECT,
        -> true
        else -> false
    }
}
