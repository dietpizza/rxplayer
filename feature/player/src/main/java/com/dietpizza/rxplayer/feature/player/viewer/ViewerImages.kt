package com.dietpizza.rxplayer.feature.player.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.player.MediaKeys
import com.dietpizza.rxplayer.core.player.SchemeDataSourceFactory
import com.dietpizza.rxplayer.core.player.source.SchemeRouter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A decoded viewer image. [width]/[height] are the decoded (possibly sampled) pixels. */
@Stable
internal sealed interface DecodedImage {
    val width: Int
    val height: Int

    /** Long edge of the encoded image, px (to tell whether a sharper decode is worth it). */
    val sourceLongEdge: Int

    /** Has an Ultra HDR gain map or an HDR (PQ/HLG) colour space: the window should go HDR. */
    val hdr: Boolean

    val aspect: Float get() = if (height > 0) width.toFloat() / height else 0f

    /** What the info sheet / HDR switch need, without holding on to pixels. */
    fun meta(): ImageMeta = ImageMeta(width, height, sourceLongEdge, hdr, this is AnimatedImage)
}

@Immutable
internal data class ImageMeta(
    val width: Int,
    val height: Int,
    val sourceLongEdge: Int,
    val hdr: Boolean,
    val animated: Boolean,
)

internal class StaticImage(
    val bitmap: ImageBitmap,
    override val width: Int,
    override val height: Int,
    override val sourceLongEdge: Int,
    override val hdr: Boolean,
) : DecodedImage

/** GIF / animated WebP / HEIF / AVIF. Not started until the page settles. */
internal class AnimatedImage(
    val drawable: AnimatedImageDrawable,
    override val width: Int,
    override val height: Int,
    override val sourceLongEdge: Int,
) : DecodedImage {
    override val hdr: Boolean get() = false
}

/**
 * Viewer image pipeline (plan §5 "Photo viewer extras"). Everything goes through the platform
 * [ImageDecoder] (minSdk 34) rather than Coil: one path that keeps Ultra HDR gain maps, applies
 * EXIF orientation, decodes HEIC/AVIF, and yields [AnimatedImageDrawable]s for animated files.
 * Coil 3's gif module isn't in the catalog and its gainmap handling is unverified.
 *
 * Network keys are first copied to a cache file through the injected scheme data sources (the
 * same ones playback uses), so decoding, sharper re-decodes and EXIF all read a local file.
 *
 * All work runs off the main thread on a small dispatcher (at most two decodes at once).
 */
@OptIn(UnstableApi::class)
@Singleton
internal class ViewerImages @Inject constructor(
    @ApplicationContext private val context: Context,
    schemeDataSourceFactories: Set<@JvmSuppressWildcards SchemeDataSourceFactory>,
) {
    private val router = SchemeRouter(schemeDataSourceFactories)
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(2)
    private val fetchLock = Mutex()

    /** A few screen-size statics, so swiping back and forth never re-decodes. */
    private val memory = object : LruCache<String, StaticImage>(CACHE_ENTRIES) {}

    private val cacheDir: File get() = File(context.cacheDir, CACHE_DIR)

    fun cached(item: ViewerItem, targetLongEdge: Int): StaticImage? = memory.get(cacheKey(item.key, targetLongEdge))

    /**
     * Decodes [item] so its long edge is at least [targetLongEdge] px (power-of-two sampled,
     * never upscaled). Throws on failure (unsupported, missing, network error).
     */
    suspend fun decode(item: ViewerItem, targetLongEdge: Int, cache: Boolean = true): DecodedImage = withContext(decodeDispatcher) {
        cached(item, targetLongEdge)?.let { return@withContext it }
        val source = source(item)
        ensureActive()
        var sourceLong = 0
        val drawable = ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
            sourceLong = max(info.size.width, info.size.height)
            decoder.setTargetSampleSize(ZoomMath.sampleSize(sourceLong, targetLongEdge))
        }
        ensureActive()
        when (drawable) {
            is AnimatedImageDrawable -> {
                drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                AnimatedImage(drawable, drawable.intrinsicWidth, drawable.intrinsicHeight, sourceLong)
            }
            else -> {
                val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: rasterize(drawable)
                StaticImage(
                    bitmap = bitmap.asImageBitmap(),
                    width = bitmap.width,
                    height = bitmap.height,
                    sourceLongEdge = sourceLong,
                    hdr = isHdr(bitmap),
                ).also { if (cache) memory.put(cacheKey(item.key, targetLongEdge), it) }
            }
        }
    }

    /** Info-sheet rows for a photo or video (plan §4 #12). Never includes location. IO. */
    suspend fun info(item: ViewerItem, decoded: ImageMeta?): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val rows = ArrayList<Pair<String, String>>()
        val g = item.gallery
        rows += "Name" to (g?.displayName ?: item.title)

        val exif = if (item.isVideo) emptyList() else runCatching { exifRows(item) }.getOrDefault(emptyList())
        val taken = exif.firstOrNull { it.first == "Taken" }?.second
            ?: g?.dateTakenMs?.takeIf { it > 0 }?.let(::formatEpoch)
        taken?.let { rows += "Taken" to it }

        val gw = g?.displayWidth ?: 0
        val gh = g?.displayHeight ?: 0
        val resolution = when {
            gw > 0 && gh > 0 -> ExifFormat.resolution(gw, gh)
            decoded != null -> {
                // Only the sampled size is known: scale it back up to the source.
                val longEdge = max(decoded.width, decoded.height).coerceAtLeast(1)
                val scale = if (decoded.sourceLongEdge > 0) decoded.sourceLongEdge.toFloat() / longEdge else 1f
                ExifFormat.resolution((decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt())
            }
            else -> null
        }
        resolution?.let { rows += "Resolution" to it }
        if (decoded?.hdr == true) rows += "HDR" to "Yes"
        if (decoded?.animated == true) rows += "Animated" to "Yes"
        (item.mimeType ?: contentType(item))?.let { rows += "Type" to it }
        ExifFormat.size(sizeOf(item))?.let { rows += "Size" to it }
        rows += exif.filter { it.first != "Taken" }
        rows += "Path" to (g?.path ?: item.key.filePath ?: item.key.value)
        rows
    }

    /** Deletes the network image cache (the viewer closed). */
    fun clearNetworkCache() {
        runCatching { cacheDir.deleteRecursively() }
    }

    // region sources

    private suspend fun source(item: ViewerItem): ImageDecoder.Source {
        val key = item.key
        key.filePath?.let { path ->
            val f = File(path)
            if (f.canRead()) return ImageDecoder.createSource(f)
            item.gallery?.contentUri?.let { return ImageDecoder.createSource(context.contentResolver, Uri.parse(it)) }
            return ImageDecoder.createSource(f)
        }
        if (key.value.startsWith("content://")) {
            return ImageDecoder.createSource(context.contentResolver, Uri.parse(key.value))
        }
        return ImageDecoder.createSource(fetch(key))
    }

    /** The network image as a local cache file (downloaded once per viewer session). */
    private suspend fun fetch(key: MediaKey): File = fetchLock.withLock {
        withContext(Dispatchers.IO) {
            val dir = cacheDir.apply { mkdirs() }
            val ext = PageKinds.extension(key.value).take(8)
            val file = File(dir, sha1(key.value) + if (ext.isEmpty()) "" else ".$ext")
            if (file.length() > 0) return@withContext file
            val part = File(dir, file.name + ".part")
            val factory = router.factoryFor(MediaKeys.scheme(key.value)) ?: DefaultDataSource.Factory(context)
            val source = factory.createDataSource()
            try {
                source.open(DataSpec(Uri.parse(key.value)))
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = source.read(buffer, 0, buffer.size)
                        if (n == C.RESULT_END_OF_INPUT) break
                        out.write(buffer, 0, n)
                    }
                }
            } catch (t: Throwable) {
                part.delete()
                throw t
            } finally {
                runCatching { source.close() }
            }
            check(part.renameTo(file)) { "cache write failed" }
            file
        }
    }

    // endregion

    // region info helpers

    private suspend fun exifRows(item: ViewerItem): List<Pair<String, String>> {
        val exif = when {
            item.key.filePath != null && File(item.key.filePath!!).canRead() -> ExifInterface(File(item.key.filePath!!))
            item.key.value.startsWith("content://") ->
                context.contentResolver.openInputStream(Uri.parse(item.key.value))?.use { ExifInterface(it) }
            item.origin == ItemOrigin.Network -> ExifInterface(fetch(item.key))
            else -> null
        } ?: return emptyList()
        // Only whitelisted, location-free tags are read (ExifFormat.READ_TAGS).
        return ExifFormat.rows({ tag -> exif.getAttribute(tag) })
    }

    private fun sizeOf(item: ViewerItem): Long {
        item.gallery?.sizeBytes?.takeIf { it > 0 }?.let { return it }
        item.key.filePath?.let { return File(it).length() }
        if (item.key.value.startsWith("content://")) {
            return runCatching {
                context.contentResolver.query(Uri.parse(item.key.value), arrayOf(OpenableColumns.SIZE), null, null, null)
                    ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L } ?: -1L
            }.getOrDefault(-1L)
        }
        return networkCacheFile(item.key)?.length() ?: -1L
    }

    private fun contentType(item: ViewerItem): String? =
        if (item.key.value.startsWith("content://")) {
            runCatching { context.contentResolver.getType(Uri.parse(item.key.value)) }.getOrNull()
        } else {
            null
        }

    private fun networkCacheFile(key: MediaKey): File? {
        val ext = PageKinds.extension(key.value).take(8)
        return File(cacheDir, sha1(key.value) + if (ext.isEmpty()) "" else ".$ext").takeIf { it.exists() }
    }

    private fun formatEpoch(ms: Long): String =
        java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            .format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM))

    // endregion

    private fun isHdr(bitmap: Bitmap): Boolean {
        if (bitmap.hasGainmap()) return true
        val cs = bitmap.colorSpace ?: return false
        return cs == ColorSpace.get(ColorSpace.Named.BT2020_HLG) || cs == ColorSpace.get(ColorSpace.Named.BT2020_PQ)
    }

    private fun rasterize(drawable: Drawable): Bitmap {
        val w = drawable.intrinsicWidth.coerceAtLeast(1)
        val h = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(android.graphics.Canvas(bitmap))
        return bitmap
    }

    private fun cacheKey(key: MediaKey, targetLongEdge: Int) = key.value + "@" + targetLongEdge

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val CACHE_DIR = "viewer"
        const val CACHE_ENTRIES = 5
    }
}
