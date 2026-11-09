package com.dietpizza.rxplayer.feature.player.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.dietpizza.rxplayer.core.player.PlaybackController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Screenshot (decision 24): the current frame at full video resolution via PixelCopy from the
 * player's SurfaceView, falling back to MediaMetadataRetriever at the current position. Saved
 * to `Pictures/Rx Player/` through MediaStore (`IS_PENDING` while writing).
 */
internal object Screenshots {
    private const val TAG = "Screenshot"
    private const val ALBUM = "Rx Player"

    suspend fun captureAndOffer(context: Context, view: View, controller: PlaybackController, snackbar: SnackbarHostState) {
        val uri = runCatching { capture(context, view, controller) }
            .onFailure { Log.w(TAG, "screenshot failed", it) }
            .getOrNull()
        snackbar.currentSnackbarData?.dismiss()
        if (uri == null) {
            snackbar.showSnackbar("Couldn't take a screenshot", duration = SnackbarDuration.Short)
        } else {
            snackbar.showSnackbar(ScreenshotVisuals(uri))
        }
    }

    private suspend fun capture(context: Context, view: View, controller: PlaybackController): Uri? {
        val title = controller.nowPlaying.value?.title ?: "Screenshot"
        val positionMs = controller.currentPositionMs
        val bitmap = pixelCopy(view, controller) ?: retrieverFrame(context, controller, positionMs) ?: return null
        return try {
            withContext(Dispatchers.IO) { save(context, bitmap, title, positionMs) }
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun pixelCopy(view: View, controller: PlaybackController): Bitmap? {
        val surfaceView = findSurfaceView(view.rootView) ?: return null
        if (!surfaceView.holder.surface.isValid) return null
        val size = controller.videoSize.value
        if (size.width <= 0 || size.height <= 0) return null
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val result = suspendCancellableCoroutine { cont ->
            PixelCopy.request(surfaceView, bitmap, { code -> cont.resume(code) }, Handler(Looper.getMainLooper()))
        }
        if (result == PixelCopy.SUCCESS) return bitmap
        bitmap.recycle()
        Log.i(TAG, "PixelCopy failed ($result), falling back to the retriever")
        return null
    }

    private suspend fun retrieverFrame(context: Context, controller: PlaybackController, positionMs: Long): Bitmap? {
        val key = controller.nowPlaying.value?.key ?: return null
        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                val path = key.filePath
                when {
                    path != null -> retriever.setDataSource(path)
                    key.value.startsWith("content://") -> retriever.setDataSource(context, Uri.parse(key.value))
                    else -> return@withContext null // network: no random access here
                }
                retriever.getFrameAtTime(positionMs * 1_000, MediaMetadataRetriever.OPTION_CLOSEST)
            } catch (e: Exception) {
                Log.w(TAG, "retriever failed", e)
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    private fun findSurfaceView(v: View): SurfaceView? {
        if (v is SurfaceView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findSurfaceView(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun save(context: Context, bitmap: Bitmap, title: String, positionMs: Long): Uri? {
        val resolver = context.contentResolver
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
        val name = "${safeTitle}_${formatTime(positionMs).replace(':', '-')}_$stamp.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return null
        val ok = runCatching {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) } == true
        }.getOrDefault(false)
        if (!ok) {
            resolver.delete(uri, null, null)
            return null
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }
}

internal class ScreenshotVisuals(val uri: Uri) : SnackbarVisuals {
    override val message: String = "Screenshot saved"
    override val actionLabel: String = "View"
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Long
}

/** "Screenshot saved · View · Share" (decision 24). */
@Composable
internal fun ScreenshotSnackbar(data: SnackbarData) {
    val visuals = data.visuals
    if (visuals !is ScreenshotVisuals) {
        Snackbar(data)
        return
    }
    val context = LocalContext.current
    Snackbar(
        action = {
            Row {
                TextButton(onClick = {
                    data.dismiss()
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW)
                                .setDataAndType(visuals.uri, "image/jpeg")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text("View", color = MaterialTheme.colorScheme.inversePrimary) }
                TextButton(onClick = {
                    data.dismiss()
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("image/jpeg")
                        .putExtra(Intent.EXTRA_STREAM, visuals.uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching {
                        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text("Share", color = MaterialTheme.colorScheme.inversePrimary) }
            }
        },
    ) { Text(visuals.message) }
}
