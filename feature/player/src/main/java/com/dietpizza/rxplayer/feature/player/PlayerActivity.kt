package com.dietpizza.rxplayer.feature.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.dietpizza.rxplayer.core.designsystem.theme.RxTheme
import com.dietpizza.rxplayer.core.model.AppearanceSettings
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.ThemeMode
import com.dietpizza.rxplayer.core.player.PlaybackController
import com.dietpizza.rxplayer.core.player.VideoDimensions
import com.dietpizza.rxplayer.core.ui.RxActivity
import com.dietpizza.rxplayer.core.ui.RxIntents
import com.dietpizza.rxplayer.feature.player.ui.PlayerScreen
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen player (plan §6). Always black and immersive; owns a [MediaSession] for hardware /
 * Bluetooth media keys while it exists (no service, no notification in M1).
 *
 * Leaving it pauses playback (decision 28) and coming back resumes if it was playing when you
 * left (plan §6.4). Closing it saves progress and unloads the player.
 */
@AndroidEntryPoint
class PlayerActivity : RxActivity() {

    @Inject
    lateinit var controller: PlaybackController

    private var mediaSession: MediaSession? = null
    private var rotationLocked by mutableStateOf(false)

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaSession = MediaSession.Builder(this, controller.sessionPlayer)
            .setId("rx.player.${System.identityHashCode(this)}")
            .build()

        openFrom(intent, restoring = savedInstanceState != null)

        setRxContent {
            val appearance by appearanceRepository.appearance.collectAsStateWithLifecycle(null)
            // The player is always dark / pure black, whatever the app theme (plan §9).
            RxTheme(
                AppearanceSettings(
                    themeMode = ThemeMode.DARK,
                    dynamicColor = appearance?.dynamicColor ?: true,
                    pureBlack = true,
                ),
            ) {
                PlayerScreen(
                    controller = controller,
                    rotationLocked = rotationLocked,
                    onToggleRotationLock = ::toggleRotationLock,
                    onControlsVisibleChange = ::setSystemBarsVisible,
                    onBack = ::finish,
                )
            }
        }

        // After setRxContent (enableEdgeToEdge): video runs under the cutout in landscape too.
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        window.insetsController?.systemBarsBehavior =
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { controller.videoSize.collect { applyOrientation(it) } }
                launch {
                    controller.isPlaying.collect { playing ->
                        if (playing) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        rotationLocked = false
        openFrom(intent, restoring = false)
    }

    override fun onStart() {
        super.onStart()
        controller.onHostStarted()
    }

    override fun onStop() {
        controller.onHostStopped()
        super.onStop()
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        if (isFinishing) controller.stop()
        super.onDestroy()
    }

    // region intent

    private fun openFrom(intent: Intent, restoring: Boolean) {
        val playlist = intent.getStringArrayListExtra(RxIntents.EXTRA_PLAYLIST)
        if (!playlist.isNullOrEmpty()) {
            val keys = playlist.map(::MediaKey)
            val start = intent.getIntExtra(RxIntents.EXTRA_START_INDEX, 0)
            if (!(restoring && controller.isShowing(keys, start))) controller.setPlaylist(keys, start)
            return
        }
        val data = intent.data
        if (intent.action == Intent.ACTION_VIEW && data != null) {
            lifecycleScope.launch {
                val key = withContext(Dispatchers.IO) { keyFor(applicationContext, data) }
                if (!(restoring && controller.isShowing(listOf(key), 0))) {
                    controller.setPlaylist(listOf(key), 0)
                }
            }
            return
        }
        finish()
    }

    // endregion

    // region window

    private fun setSystemBarsVisible(visible: Boolean) {
        val controller = window.insetsController ?: return
        if (visible) {
            controller.show(WindowInsets.Type.systemBars())
            // Light icons on the black player, whatever the app theme set.
            controller.setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
        } else {
            controller.hide(WindowInsets.Type.systemBars())
        }
    }

    private fun toggleRotationLock() {
        rotationLocked = !rotationLocked
        applyOrientation(controller.videoSize.value)
    }

    /** Decision 18: orientation follows the video's aspect, unless the user locked it. */
    private fun applyOrientation(size: VideoDimensions) {
        val wanted = orientationFor(size, rotationLocked) ?: return
        if (requestedOrientation != wanted) requestedOrientation = wanted
    }

    // endregion

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_K -> controller.togglePlayPause()
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_J -> controller.seekBack()
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_L -> controller.seekForward()
            KeyEvent.KEYCODE_M -> controller.toggleMute()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    companion object {
        /** Opens the player on [playlist] at [startIndex] (same contract as [RxIntents.player]). */
        fun intent(context: Context, playlist: List<MediaKey>, startIndex: Int): Intent =
            RxIntents.player(context, playlist, startIndex)

        /**
         * Requested orientation for a video, or null to leave it as is (size unknown yet).
         * Locked keeps whatever the screen is showing now.
         */
        internal fun orientationFor(size: VideoDimensions, locked: Boolean): Int? = when {
            locked -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
            size.isLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            size.isPortrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            size.width > 0 -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else -> null
        }

        /**
         * `file://` → a local key. MediaStore content URIs are mapped back to their file when we
         * can (so resume/history work); anything else keeps the content URI as its key.
         */
        private fun keyFor(context: Context, uri: Uri): MediaKey {
            if (uri.scheme == "file") uri.path?.let { return MediaKey.forFile(it) }
            if (uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY) {
                val path = runCatching {
                    context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                }.getOrNull()
                if (path != null && File(path).canRead()) return MediaKey.forFile(path)
            }
            return MediaKey(uri.toString())
        }
    }
}
