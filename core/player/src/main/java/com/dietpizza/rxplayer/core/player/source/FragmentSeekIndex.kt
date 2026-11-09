package com.dietpizza.rxplayer.core.player.source

import android.content.Context
import android.net.Uri
import android.util.LruCache
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ChunkIndex
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Seek tables for unindexed fragmented MP4s ([FragmentIndexScanner]), cached in memory and on
 * disk (`cacheDir/seekindex`), keyed by path + size + modified time so an edited file rescans.
 *
 * Built on the player's loading thread the first time such a file opens (box headers only:
 * milliseconds for a local file), then reused. Local files and content URIs only: scanning a
 * network file would cost a round trip per fragment.
 */
@UnstableApi
internal class FragmentSeekIndexStore(private val context: Context) {
    private val memory = LruCache<String, Entry>(64)
    private val dir: File get() = File(context.cacheDir, "seekindex")

    /** Null result is cached too, so an unfixable file isn't rescanned on every open. */
    private class Entry(val index: FragmentIndex?)

    fun indexFor(uri: Uri): FragmentIndex? {
        val opened = open(uri) ?: return null
        opened.use { file ->
            val key = cacheKey(uri, file.size, file.modifiedMs)
            memory.get(key)?.let { return it.index }
            val stored = readDisk(key)
            val index = stored ?: FragmentIndexScanner.scan(file)
            if (stored == null && index != null) writeDisk(key, index)
            memory.put(key, Entry(index))
            return index
        }
    }

    // region file access

    private interface OpenedFile : ByteSource, AutoCloseable {
        val modifiedMs: Long
    }

    private fun open(uri: Uri): OpenedFile? = runCatching {
        when (uri.scheme) {
            "file" -> {
                val f = File(uri.path ?: return null)
                if (!f.isFile) return null
                rafSource(RandomAccessFile(f, "r"), f.lastModified())
            }
            "content" -> {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
                val raf = RandomAccessFile("/proc/self/fd/${pfd.fd}", "r")
                object : OpenedFile by rafSource(raf, 0L) {
                    override fun close() {
                        raf.close()
                        pfd.close()
                    }
                }
            }
            else -> null
        }
    }.getOrNull()

    private fun rafSource(raf: RandomAccessFile, modified: Long) = object : OpenedFile {
        override val size: Long = raf.length()
        override val modifiedMs: Long = modified

        override fun read(position: Long, buf: ByteArray, len: Int): Boolean = runCatching {
            if (position < 0 || position + len > size) return false
            raf.seek(position)
            raf.readFully(buf, 0, len)
            true
        }.getOrDefault(false)

        override fun close() = raf.close()
    }

    // endregion

    // region disk cache

    private fun cacheKey(uri: Uri, size: Long, modifiedMs: Long): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("$uri|$size|$modifiedMs".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun readDisk(key: String): FragmentIndex? = runCatching {
        val f = File(dir, "$key.idx")
        if (!f.isFile) return null
        DataInputStream(f.inputStream().buffered()).use { input ->
            if (input.readInt() != DISK_VERSION) return null
            val n = input.readInt()
            if (n < 2 || n > 1_000_000) return null
            val offsets = LongArray(n) { input.readLong() }
            val sizes = IntArray(n) { input.readInt() }
            val times = LongArray(n) { input.readLong() }
            val durations = LongArray(n) { input.readLong() }
            FragmentIndex(offsets, sizes, times, durations)
        }
    }.getOrNull()

    private fun writeDisk(key: String, index: FragmentIndex) {
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "$key.tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { out ->
                out.writeInt(DISK_VERSION)
                out.writeInt(index.count)
                index.offsets.forEach(out::writeLong)
                index.sizes.forEach(out::writeInt)
                index.timesUs.forEach(out::writeLong)
                index.durationsUs.forEach(out::writeLong)
            }
            tmp.renameTo(File(dir, "$key.idx"))
        }
    }

    // endregion

    private companion object {
        const val DISK_VERSION = 1
    }
}

/**
 * Wraps the default extractors so a [FragmentedMp4Extractor] that ends up with no seek map of
 * its own (no `sidx`) gets one from [FragmentSeekIndexStore]. Everything else is untouched.
 */
@UnstableApi
internal class FragmentIndexingExtractorsFactory(
    private val delegate: ExtractorsFactory,
    private val store: FragmentSeekIndexStore,
) : ExtractorsFactory {
    override fun createExtractors(): Array<Extractor> = delegate.createExtractors()

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
        delegate.createExtractors(uri, responseHeaders).map { extractor ->
            if (extractor is FragmentedMp4Extractor) IndexingExtractor(extractor, uri, store) else extractor
        }.toTypedArray()
}

@UnstableApi
private class IndexingExtractor(
    private val inner: Extractor,
    private val uri: Uri,
    private val store: FragmentSeekIndexStore,
) : Extractor {
    override fun sniff(input: ExtractorInput): Boolean = inner.sniff(input)

    override fun init(output: ExtractorOutput) {
        inner.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput = output.track(id, type)
            override fun endTracks() = output.endTracks()
            override fun seekMap(seekMap: SeekMap) {
                if (seekMap.isSeekable) {
                    output.seekMap(seekMap)
                    return
                }
                val index = runCatching { store.indexFor(uri) }.getOrNull()
                output.seekMap(
                    if (index != null) ChunkIndex(index.sizes, index.offsets, index.durationsUs, index.timesUs) else seekMap,
                )
            }
        })
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = inner.read(input, seekPosition)

    override fun seek(position: Long, timeUs: Long) = inner.seek(position, timeUs)

    override fun release() = inner.release()
}
