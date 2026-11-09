package com.dietpizza.rxplayer

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dietpizza.rxplayer.core.designsystem.theme.RxTheme
import com.dietpizza.rxplayer.core.model.ThemeMode
import com.dietpizza.rxplayer.ui.RxApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        holdFirstFrameUntilAppearanceLoaded()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val appearance = (state as? MainUiState.Ready)?.appearance ?: return@setContent

            // Keep system bar icon contrast in sync with the app theme, not just the system one.
            val darkTheme = when (appearance.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(darkTheme) {
                val style = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { darkTheme }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            RxTheme(appearance) { RxApp() }
        }
    }

    /**
     * Keeps the system splash (DayNight window background) on screen until the persisted
     * appearance is known, so the first Compose frame is drawn with the right theme and there's
     * no light/dark or colour-scheme flash. DataStore normally answers in a few ms; the timeout
     * is only a safety net.
     */
    private fun holdFirstFrameUntilAppearanceLoaded() {
        val content = findViewById<View>(android.R.id.content)
        val deadline = SystemClock.uptimeMillis() + FIRST_FRAME_TIMEOUT_MS
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val ready = viewModel.uiState.value is MainUiState.Ready ||
                        SystemClock.uptimeMillis() > deadline
                    if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                    return ready
                }
            },
        )
    }

    private companion object {
        const val FIRST_FRAME_TIMEOUT_MS = 1_000L
    }
}
