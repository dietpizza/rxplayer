package com.dietpizza.rxplayer.core.player.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.util.Log
import com.dietpizza.rxplayer.core.model.MediaKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Seek-bar thumbnail sprites (plan §6.4, decision 31). When a local file opens, frames are
 * pulled with `MediaMetadataRetriever.getScaledFrameAtTime(OPTION_CLOSEST_SYNC)` on one
 * low-priority background thread, coarse to fine, into a single sprite sheet. Finished sheets
 * are cached in `cacheDir/sprites` (JPEG, keyed by mediaKey + size + mtime) with an LRU cap.
 *
 * Network keys are skipped for now (null sprites): random access over SMB/FTP is too slow for
 * eager generation (plan §12 risk 2).
 */
@Singleton
class SeekPreviewEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dispatcher = Executors.newSingleThreadExecutor { r ->
        Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }, "rx-seek-sprites")
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var job: Job? = null
    @Volatile private var openedKey: MediaKey? = null

    private val _sprites = MutableStateFlow<SeekSprites?>(null)

    /** Sprites of the open item, or null (not local, disabled, not started, or failed). */
    val sprites: StateFlow<SeekSprites?> = _sprites.asStateFlow()

    private val cacheDir: File get() = File(context.cacheDir, "sprites")

    /** Starts (or keeps) generation for [key]. Main thread. */
    fun open(key: MediaKey, startDelayMs: Long = START_DELAY_MS) {
        if (openedKey == key && job?.isActive == true) return
        close()
        val path = key.filePath ?: return
        openedKey = key
        job = scope.launch {
            delay(startDelayMs) // let the first frame and audio start without competition
            runCatching { generate(key, File(path)) }
                .onFailure { if (it !is CancellationException) Log.w(TAG, "sprites failed", it) }
        }
    }

    /** Stops generation and drops the sprites. Main thread. */
    fun close() {
        job?.cancel()
        job = null
        openedKey = null
        _sprites.value = null
    }

    private suspend fun generate(key: MediaKey, file: File) {
        if (!file.canRead()) return
        val hash = sha1("${key.value}|${file.length()}|${file.lastModified()}|$FRAME_WIDTH|$VERSION")
        loadCached(hash)?.let {
            publish(key, it)
            return
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: return
            var w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return
            var h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) w = h.also { h = w }
            if (w <= 0 || h <= 0) return

            val interval = SpriteInterval.intervalMs(durationMs)
            val count = SpriteInterval.frameCount(durationMs, interval)
            if (count <= 0) return
            val fw = FRAME_WIDTH
            val fh = ((fw.toFloat() * h / w).roundToInt() and 1.inv()).coerceIn(16, fw * 3)
            val columns = COLUMNS.coerceAtMost(count)
            val rows = (count + columns - 1) / columns
            val sheet = Bitmap.createBitmap(columns * fw, rows * fh, Bitmap.Config.RGB_565)
            val sprites = SeekSprites(interval, count, fw, fh, columns, sheet, allReady = false)
            publish(key, sprites)

            val canvas = Canvas(sheet)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            val dst = Rect()
            val params = MediaMetadataRetriever.BitmapParams().apply {
                preferredConfig = Bitmap.Config.RGB_565
            }
            var produced = 0
            for (i in SpriteInterval.generationOrder(count)) {
                currentCoroutineContext().ensureActive()
                val frame = runCatching {
                    retriever.getScaledFrameAtTime(
                        i * interval * 1_000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        fw,
                        fh,
                        params,
                    )
                }.getOrNull() ?: continue
                val x = sprites.srcX(i)
                val y = sprites.srcY(i)
                dst.set(x, y, x + fw, y + fh)
                canvas.drawBitmap(frame, null, dst, paint)
                frame.recycle()
                sprites.markReady(i)
                produced++
            }
            if (produced == count) save(hash, sprites)
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Publishes only while [key] is still the open item (close() may race the worker). */
    private suspend fun publish(key: MediaKey, sprites: SeekSprites) {
        currentCoroutineContext().ensureActive()
        if (openedKey == key) _sprites.value = sprites
    }

    // region disk cache

    private fun loadCached(hash: String): SeekSprites? {
        val dir = cacheDir
        val file = dir.listFiles { f -> f.name.startsWith("${hash}_") }?.firstOrNull() ?: return null
        // name: <hash>_<interval>_<count>_<columns>_<fw>_<fh>.jpg
        val parts = file.nameWithoutExtension.split('_')
        if (parts.size != 6) {
            file.delete()
            return null
        }
        val interval = parts[1].toLongOrNull() ?: return null
        val count = parts[2].toIntOrNull() ?: return null
        val columns = parts[3].toIntOrNull() ?: return null
        val fw = parts[4].toIntOrNull() ?: return null
        val fh = parts[5].toIntOrNull() ?: return null
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 },
        ) ?: run {
            file.delete()
            return null
        }
        file.setLastModified(System.currentTimeMillis())
        return SeekSprites(interval, count, fw, fh, columns, bitmap, allReady = true)
    }

    private fun save(hash: String, s: SeekSprites) {
        val dir = cacheDir
        if (!dir.isDirectory && !dir.mkdirs()) return
        val name = "${hash}_${s.intervalMs}_${s.count}_${s.columns}_${s.frameWidth}_${s.frameHeight}.jpg"
        val tmp = File(dir, "$name.tmp")
        runCatching {
            tmp.outputStream().buffered().use { s.sheet.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (!tmp.renameTo(File(dir, name))) tmp.delete()
        }.onFailure { tmp.delete() }
        trim(dir)
    }

    /** LRU by last-modified (touched on every cache hit). */
    private fun trim(dir: File) {
        val files = dir.listFiles() ?: return
        files.sortByDescending { it.lastModified() }
        var total = 0L
        for (f in files) {
            total += f.length()
            if (total > CACHE_CAP_BYTES) f.delete()
        }
    }

    // endregion

    private fun sha1(s: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0f])
        }
        return sb.toString()
    }

    companion object {
        private const val TAG = "SeekPreview"
        const val FRAME_WIDTH = 160
        private const val COLUMNS = 10
        private const val JPEG_QUALITY = 80
        private const val VERSION = 1
        private const val START_DELAY_MS = 1_500L
        const val CACHE_CAP_BYTES = 64L * 1024 * 1024
        private val HEX = "0123456789abcdef".toCharArray()
    }
}
