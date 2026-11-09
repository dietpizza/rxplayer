package com.dietpizza.rxplayer.core.ui

import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.dietpizza.rxplayer.core.datastore.AppearanceRepository
import com.dietpizza.rxplayer.core.designsystem.theme.RxTheme
import com.dietpizza.rxplayer.core.designsystem.theme.isDark
import com.dietpizza.rxplayer.core.model.AppearanceSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Base for every screen-level Activity in Rx Player (plan §3.2: one Activity per full-screen
 * destination, so we get the OS's own activity transitions and predictive back).
 *
 * Subclasses must be `@AndroidEntryPoint` and call [setRxContent] from `onCreate`. It handles:
 * - edge-to-edge, with system-bar icon contrast following the *app* theme setting
 * - [RxTheme] driven live by [AppearanceRepository] (settings changes restyle every open activity)
 * - holding the first frame until the saved appearance is known, so there's no theme flash
 */
abstract class RxActivity : ComponentActivity() {

    @Inject
    lateinit var appearanceRepository: AppearanceRepository

    private val appearance = MutableStateFlow<AppearanceSettings?>(null)

    protected fun setRxContent(content: @Composable () -> Unit) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        lifecycleScope.launch { appearanceRepository.appearance.collect { appearance.value = it } }
        holdFirstFrameUntilAppearanceLoaded()

        setContent {
            val settings by appearance.collectAsStateWithLifecycle()
            val current = settings ?: return@setContent
            val darkTheme = current.themeMode.isDark()
            // Only flip the bar icon contrast. Re-running enableEdgeToEdge() here would reset
            // window flags and force a full insets/layout pass on the frame the theme changes.
            DisposableEffect(darkTheme) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
                onDispose {}
            }
            RxTheme(current) {
                // Expose Modifier.testTag as the view resource id so UiAutomator journeys
                // (:baselineprofile) can select nodes with By.res(tag). No layout effect:
                // propagateMinConstraints keeps the root's exact window constraints.
                Box(
                    modifier = Modifier.semantics { testTagsAsResourceId = true },
                    propagateMinConstraints = true,
                ) { content() }
            }
        }
    }

    private fun holdFirstFrameUntilAppearanceLoaded() {
        val root = findViewById<View>(android.R.id.content)
        val deadline = SystemClock.uptimeMillis() + FIRST_FRAME_TIMEOUT_MS
        root.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val ready = appearance.value != null || SystemClock.uptimeMillis() > deadline
                    if (ready) root.viewTreeObserver.removeOnPreDrawListener(this)
                    return ready
                }
            },
        )
    }

    private companion object {
        const val FIRST_FRAME_TIMEOUT_MS = 1_000L
    }
}
