package com.dietpizza.rxplayer.core.ui.thumbnail

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.os.CancellationSignal
import android.util.Size
import androidx.compose.runtime.Immutable
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.request.bitmapConfig
import coil3.size.pxOrElse
import coil3.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Coil model for a MediaStore item's thumbnail (photo or video), addressed by its `content://`
 * URI. Loaded with [ContentResolver.loadThumbnail], which the system caches, so it's fast and
 * never decodes the full file. Falls back to Coil's own pipeline (content URI → VideoFrameDecoder
 * for videos, BitmapFactory for images) if MediaStore can't produce one.
 */
@Immutable
data class MediaStoreThumbnail(val contentUri: String)

internal class MediaStoreThumbnailKeyer : Keyer<MediaStoreThumbnail> {
    // The memory cache adds the requested size itself.
    override fun key(data: MediaStoreThumbnail, options: Options): String = "mst:" + data.contentUri
}

internal class MediaStoreThumbnailFetcher(
    private val data: MediaStoreThumbnail,
    private val options: Options,
    private val imageLoader: ImageLoader,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val resolver = options.context.contentResolver
        val width = options.size.width.pxOrElse { DEFAULT_PX }
        val height = options.size.height.pxOrElse { width }
        val bitmap = try {
            loadThumbnail(resolver, Uri.parse(data.contentUri), Size(width, height))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // Not indexed, unsupported, or the provider failed: fall back below.
        }
        if (bitmap != null) {
            return ImageFetchResult(
                image = bitmap.toRequestedConfig().asImage(),
                isSampled = true,
                dataSource = DataSource.DISK,
            )
        }
        currentCoroutineContext().ensureActive()
        // Fallback: hand the raw content URI to Coil's next fetcher (ContentUriFetcher). Its
        // SourceFetchResult carries the MIME type, so the engine picks VideoFrameDecoder for
        // videos and the normal bitmap decoder for images.
        val fallback = imageLoader.components.newFetcher(data.contentUri.toUri(), options, imageLoader)
            ?: return null
        return fallback.first.fetch()
    }

    /** Blocking binder call, cancelled through a [CancellationSignal] when the request is. */
    private suspend fun loadThumbnail(resolver: ContentResolver, uri: Uri, size: Size): Bitmap =
        coroutineScope {
            val signal = CancellationSignal()
            // Resumes on Default (not this, blocked, thread) when the request is cancelled.
            val watcher = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    signal.cancel()
                }
            }
            try {
                resolver.loadThumbnail(uri, size, signal)
            } finally {
                watcher.cancel()
            }
        }

    /**
     * MediaStore returns software ARGB_8888 bitmaps. Uploading to a HARDWARE bitmap here, on the
     * fetch thread, keeps the GPU texture upload off the RenderThread while the list scrolls.
     */
    private fun Bitmap.toRequestedConfig(): Bitmap {
        if (options.bitmapConfig != Bitmap.Config.HARDWARE || config == Bitmap.Config.HARDWARE) return this
        val hardware = copy(Bitmap.Config.HARDWARE, false) ?: return this
        recycle()
        return hardware
    }

    class Factory : Fetcher.Factory<MediaStoreThumbnail> {
        override fun create(data: MediaStoreThumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            MediaStoreThumbnailFetcher(data, options, imageLoader)
    }

    private companion object {
        const val DEFAULT_PX = 512
    }
}
