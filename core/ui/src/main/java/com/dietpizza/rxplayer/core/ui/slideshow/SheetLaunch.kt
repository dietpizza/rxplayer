package com.dietpizza.rxplayer.core.ui.slideshow

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

/**
 * Starts an Activity from a sheet's button once the sheet is really gone.
 *
 * The caller hides the sheet (drops it from composition), then calls [launch]. The Activity starts
 * a few frames later, after the sheet's dialog window has been removed. Started straight away, the
 * opening Activity stopped this one before that recomposition ran, so coming back showed the sheet
 * for a frame (and the back animation's snapshot had it too).
 *
 * The work runs in the Activity's lifecycle scope, so it isn't cancelled when the sheet that
 * started it leaves composition.
 */
class SheetLauncher internal constructor(private val scope: CoroutineScope, private val context: Context) {
    /** Builds the intent (null for nothing to open), waits for the sheet to be off screen, then starts it. */
    fun launch(build: suspend () -> Intent?) {
        scope.launch {
            val intent = build() ?: return@launch
            repeat(FRAMES) { awaitFrame() }
            context.startActivity(intent)
        }
    }

    private companion object {
        /** One frame for the recomposition that drops the sheet, one for the window to go, one spare. */
        const val FRAMES = 3
    }
}

@Composable
fun rememberSheetLauncher(): SheetLauncher {
    val owner = LocalLifecycleOwner.current
    val context = LocalContext.current
    return remember(owner, context) { SheetLauncher(owner.lifecycleScope, context) }
}
