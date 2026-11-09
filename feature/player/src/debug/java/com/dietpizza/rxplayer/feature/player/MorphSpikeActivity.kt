package com.dietpizza.rxplayer.feature.player

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.dietpizza.rxplayer.core.designsystem.theme.RxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DEBUG ONLY. M1 spike: inline (muted, rounded card in a viewer pager) -> full-screen morph of ONE
 * PlayerSurface, without re-preparing or recreating the surface. See plan §4.1 and §12 risk 1.
 *
 * adb shell am start -n com.dietpizza.rxplayer/com.dietpizza.rxplayer.feature.player.MorphSpikeActivity \
 *     [--es surface surface|texture] [--es mode bounds|scale|movable] [--es clip mask|clip|outline|none] \
 *     [--es spec default|fast|slow] [--es uri content://...]
 *
 * Results are also logged: adb logcat -s MorphSpike
 */
class MorphSpikeActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private val stats = MorphSpikeStats()
    private var status by mutableStateOf("Looking for the newest video...")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        player = ExoPlayer.Builder(this)
            // Plan §3.3: never take audio focus.
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ false)
            .build()
            .apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = true
                addListener(stats.playerListener)
            }

        val initial = MorphSpikeConfig.fromIntent(
            surface = intent.getStringExtra("surface"),
            mode = intent.getStringExtra("mode"),
            clip = intent.getStringExtra("clip"),
            spec = intent.getStringExtra("spec"),
        )

        lifecycleScope.launch {
            val uri = intent.getStringExtra("uri")?.let(Uri::parse)
                ?: withContext(Dispatchers.IO) { newestVideo() }
            if (uri == null) {
                status = "No video found in MediaStore (is all-files access granted?)"
                return@launch
            }
            status = uri.toString()
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
        }

        setContent {
            RxTheme {
                MorphSpikeScreen(
                    player = player,
                    stats = stats,
                    status = status,
                    initialConfig = initial,
                    window = window,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        stats.attach(window)
        player.play()
    }

    override fun onStop() {
        player.pause()
        stats.detach(window)
        super.onStop()
    }

    override fun onDestroy() {
        player.removeListener(stats.playerListener)
        player.release()
        super.onDestroy()
    }

    private fun newestVideo(): Uri? {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val args = Bundle().apply {
            putStringArray(
                android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.Video.Media.DATE_ADDED),
            )
            putInt(
                android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING,
            )
            putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 1)
        }
        return contentResolver.query(collection, arrayOf(MediaStore.Video.Media._ID), args, null)
            ?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)) else null }
    }
}
