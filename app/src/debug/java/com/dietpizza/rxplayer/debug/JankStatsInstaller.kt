package com.dietpizza.rxplayer.debug

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState

/**
 * Debug builds only (declared in src/debug/AndroidManifest.xml, so release has no trace of it):
 * attaches [JankStats] to every activity's window and logs each janky frame with the activity
 * name under the `RxJank` tag:
 *
 * ```
 * adb logcat -s RxJank
 * ```
 *
 * Remember debug builds run Compose unoptimized, so absolute numbers are pessimistic; use this
 * to find *where* frames drop, and confirm on a release build with Perfetto.
 */
class JankStatsInstaller : ContentProvider() {

    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        app.registerActivityLifecycleCallbacks(JankCallbacks)
        return true
    }

    private object JankCallbacks : Application.ActivityLifecycleCallbacks {
        private val stats = HashMap<Activity, JankStats>()

        // Post-created: the activity has set its content, so touching the decor view is safe.
        override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
            val name = activity.javaClass.simpleName
            val window = activity.window
            PerformanceMetricsState.getHolderForHierarchy(window.decorView).state
                ?.putState("Activity", name)
            stats[activity] = JankStats.createAndTrack(window) { frame ->
                if (frame.isJank) {
                    Log.w(
                        TAG,
                        "$name janky frame: ${frame.frameDurationUiNanos / 1_000_000} ms ui, " +
                            "states=${frame.states}",
                    )
                }
            }.apply { isTrackingEnabled = false }
        }

        override fun onActivityResumed(activity: Activity) {
            stats[activity]?.isTrackingEnabled = true
        }

        override fun onActivityPaused(activity: Activity) {
            stats[activity]?.isTrackingEnabled = false
        }

        override fun onActivityDestroyed(activity: Activity) {
            stats.remove(activity)?.isTrackingEnabled = false
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object {
        const val TAG = "RxJank"
    }
}
