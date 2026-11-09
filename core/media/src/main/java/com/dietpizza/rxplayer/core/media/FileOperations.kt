package com.dietpizza.rxplayer.core.media

import android.content.Context
import android.media.MediaScannerConnection
import com.dietpizza.rxplayer.core.model.CopyProgress
import com.dietpizza.rxplayer.core.model.FileOpError
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Result for one item. [targetPath] is the new absolute path for rename/move/copy successes. */
data class FileOpResult(
    val sourcePath: String,
    val targetPath: String?,
    val error: FileOpError?,
) {
    val isSuccess: Boolean get() = error == null

    companion object {
        fun ok(source: String, target: String?) = FileOpResult(source, target, null)
        fun fail(source: String, error: FileOpError) = FileOpResult(source, null, error)
    }
}

/**
 * Local file operations through `java.io.File` (plan §3.4, MANAGE_EXTERNAL_STORAGE), each followed
 * by a media scan of the old and new paths so MediaStore catches up.
 *
 * All functions run on an IO dispatcher and return one result per input, in input order. They
 * never throw for per-item failures. Copy/move honour coroutine cancellation between buffers: the
 * item in flight is rolled back (a partial copy is deleted), items already finished are kept and
 * scanned, and the call then throws [CancellationException]. Callers that must record finished
 * items (e.g. a move's media_key rewrite) should run the call under `NonCancellable`.
 *
 * Move and copy never overwrite: a clash gets a free name ("clip (1).mp4"). Delete is permanent
 * (decision 20); confirming is the UI's job.
 */
interface FileOperations {
    suspend fun rename(path: String, newName: String): FileOpResult

    suspend fun move(
        paths: List<String>,
        targetDir: String,
        onProgress: (CopyProgress) -> Unit = {},
    ): List<FileOpResult>

    suspend fun copy(
        paths: List<String>,
        targetDir: String,
        onProgress: (CopyProgress) -> Unit = {},
    ): List<FileOpResult>

    suspend fun delete(paths: List<String>): List<FileOpResult>

    /** Creates [name] inside [parentDir] (for the "New folder" picker row). [FileOpResult.targetPath] is the new dir. */
    suspend fun createDirectory(parentDir: String, name: String): FileOpResult
}

/** Tells MediaStore about changed paths. Abstracted so [FileSystemOperations] runs in JVM tests. */
fun interface MediaScanner {
    fun scan(paths: List<String>)
}

@Singleton
class AndroidMediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaScanner {
    override fun scan(paths: List<String>) {
        if (paths.isEmpty()) return
        // Scanning a path that no longer exists removes its MediaStore row.
        MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
    }
}

class FileSystemOperations(
    private val scanner: MediaScanner,
    private val io: CoroutineDispatcher,
) : FileOperations {

    @Inject
    constructor(scanner: MediaScanner) : this(scanner, Dispatchers.IO)

    override suspend fun rename(path: String, newName: String): FileOpResult = withContext(io) {
        val source = File(path)
        val name = newName.trim()
        val result = when {
            !isValidName(name) -> FileOpResult.fail(path, FileOpError.INVALID_NAME)
            !source.isFile -> FileOpResult.fail(path, FileOpError.NOT_FOUND)
            name == source.name -> FileOpResult.ok(path, path)
            else -> {
                val target = File(source.parentFile, name)
                val caseOnly = name.equals(source.name, ignoreCase = true)
                when {
                    target.exists() && !caseOnly -> FileOpResult.fail(path, FileOpError.TARGET_EXISTS)
                    caseOnly -> renameViaTemp(source, target)
                    source.renameTo(target) -> FileOpResult.ok(path, target.path)
                    else -> FileOpResult.fail(path, FileOpError.IO)
                }
            }
        }
        scanResults(listOf(result))
        result
    }

    /** Case-only renames on case-insensitive storage (FUSE /sdcard): go through a temp name. */
    private fun renameViaTemp(source: File, target: File): FileOpResult {
        val temp = File(source.parentFile, ".rx-rename-" + System.nanoTime())
        if (!source.renameTo(temp)) return FileOpResult.fail(source.path, FileOpError.IO)
        return if (temp.renameTo(target)) {
            FileOpResult.ok(source.path, target.path)
        } else {
            temp.renameTo(source)
            FileOpResult.fail(source.path, FileOpError.IO)
        }
    }

    override suspend fun move(
        paths: List<String>,
        targetDir: String,
        onProgress: (CopyProgress) -> Unit,
    ): List<FileOpResult> = transfer(paths, targetDir, onProgress, move = true)

    override suspend fun copy(
        paths: List<String>,
        targetDir: String,
        onProgress: (CopyProgress) -> Unit,
    ): List<FileOpResult> = transfer(paths, targetDir, onProgress, move = false)

    private suspend fun transfer(
        paths: List<String>,
        targetDir: String,
        onProgress: (CopyProgress) -> Unit,
        move: Boolean,
    ): List<FileOpResult> = withContext(io) {
        val dir = File(targetDir)
        if (!dir.isDirectory && !dir.mkdirs()) {
            return@withContext paths.map { FileOpResult.fail(it, FileOpError.TARGET_DIR_UNAVAILABLE) }
        }
        val sources = paths.map(::File)
        val totalBytes = sources.sumOf { if (it.isFile) it.length() else 0L }
        var copied = 0L
        val results = ArrayList<FileOpResult>(paths.size)
        var cancelled = false
        for ((index, source) in sources.withIndex()) {
            val path = paths[index]
            if (!currentCoroutineContext().isActiveSafe()) {
                cancelled = true
                break
            }
            if (!source.isFile) {
                results += FileOpResult.fail(path, FileOpError.NOT_FOUND)
                continue
            }
            val sameDir = source.parentFile?.absoluteFile == dir.absoluteFile
            if (move && sameDir) {
                results += FileOpResult.ok(path, source.path) // Nothing to do.
                copied += source.length()
                continue
            }
            val target = freeTarget(dir, source.name)
            val length = source.length()
            // A move within one volume is a cheap rename; across volumes (SD card) it's copy + delete.
            if (move && source.renameTo(target)) {
                copied += length
                onProgress(CopyProgress(index, paths.size, copied, totalBytes))
                results += FileOpResult.ok(path, target.path)
                continue
            }
            val base = copied
            val outcome = copyFile(source, target) { bytes ->
                onProgress(CopyProgress(index, paths.size, base + bytes, totalBytes))
            }
            copied = base + length
            results += when (outcome) {
                CopyOutcome.DONE -> {
                    if (move) target.setLastModified(source.lastModified())
                    if (move && !source.delete()) {
                        // Couldn't remove the original: undo so we don't leave a duplicate.
                        target.delete()
                        FileOpResult.fail(path, FileOpError.IO)
                    } else {
                        FileOpResult.ok(path, target.path)
                    }
                }
                CopyOutcome.CANCELLED -> {
                    cancelled = true
                    break
                }
                CopyOutcome.FAILED -> FileOpResult.fail(path, FileOpError.IO)
            }
        }
        scanResults(results, includeSource = move)
        if (cancelled) throw CancellationException("File operation cancelled")
        results
    }

    private enum class CopyOutcome { DONE, CANCELLED, FAILED }

    private suspend fun copyFile(source: File, target: File, onBytes: (Long) -> Unit): CopyOutcome {
        val context = currentCoroutineContext()
        return try {
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        context.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        total += read
                        onBytes(total)
                    }
                    output.fd.sync()
                }
            }
            CopyOutcome.DONE
        } catch (e: CancellationException) {
            target.delete()
            CopyOutcome.CANCELLED
        } catch (e: IOException) {
            target.delete()
            CopyOutcome.FAILED
        } catch (e: SecurityException) {
            target.delete()
            CopyOutcome.FAILED
        }
    }

    override suspend fun delete(paths: List<String>): List<FileOpResult> = withContext(io) {
        val results = paths.map { path ->
            val file = File(path)
            when {
                !file.isFile -> FileOpResult.fail(path, FileOpError.NOT_FOUND)
                file.delete() -> FileOpResult.ok(path, null)
                else -> FileOpResult.fail(path, FileOpError.IO)
            }
        }
        scanner.scan(results.filter { it.isSuccess }.map { it.sourcePath })
        results
    }

    override suspend fun createDirectory(parentDir: String, name: String): FileOpResult = withContext(io) {
        val trimmed = name.trim()
        val dir = File(parentDir, trimmed)
        when {
            !isValidName(trimmed) -> FileOpResult.fail(dir.path, FileOpError.INVALID_NAME)
            dir.exists() -> if (dir.isDirectory) {
                FileOpResult.ok(dir.path, dir.path)
            } else {
                FileOpResult.fail(dir.path, FileOpError.TARGET_EXISTS)
            }
            dir.mkdirs() -> FileOpResult.ok(dir.path, dir.path)
            else -> FileOpResult.fail(dir.path, FileOpError.IO)
        }
    }

    private fun scanResults(results: List<FileOpResult>, includeSource: Boolean = true) {
        val paths = ArrayList<String>()
        for (r in results) {
            if (!r.isSuccess) continue
            if (includeSource && r.sourcePath != r.targetPath) paths += r.sourcePath
            r.targetPath?.let { paths += it }
        }
        scanner.scan(paths.distinct())
    }

    private fun kotlin.coroutines.CoroutineContext.isActiveSafe(): Boolean =
        this[kotlinx.coroutines.Job]?.isActive ?: true

    companion object {
        private const val BUFFER_SIZE = 1 shl 20

        fun isValidName(name: String): Boolean =
            name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\u0000' !in name &&
                name.length <= 255

        /** `name`, or `name (1).ext`, `name (2).ext`, ... — the first that doesn't exist in [dir]. */
        fun freeTarget(dir: File, name: String): File {
            val first = File(dir, name)
            if (!first.exists()) return first
            val dot = name.lastIndexOf('.')
            val base = if (dot > 0) name.substring(0, dot) else name
            val ext = if (dot > 0) name.substring(dot) else ""
            var n = 1
            while (true) {
                val candidate = File(dir, "$base ($n)$ext")
                if (!candidate.exists()) return candidate
                n++
            }
        }
    }
}
