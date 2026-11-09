package com.dietpizza.rxplayer.core.data

import com.dietpizza.rxplayer.core.media.FileOpResult
import com.dietpizza.rxplayer.core.media.FileOperations
import com.dietpizza.rxplayer.core.model.CopyProgress
import com.dietpizza.rxplayer.core.model.FileOpError
import com.dietpizza.rxplayer.core.model.MediaKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class DefaultMediaFileService @Inject constructor(
    private val files: FileOperations,
    private val keys: MediaKeyStore,
) : MediaFileService {

    /** One file operation at a time, so key rewrites can't interleave. */
    private val lock = Mutex()

    override suspend fun rename(key: MediaKey, newName: String): MediaFileResult = lock.withLock {
        val path = key.filePath ?: return@withLock MediaFileResult(key, null, FileOpError.UNSUPPORTED)
        withContext(NonCancellable) {
            val result = files.rename(path, newName)
            keys.rewrite(keyRewrites(listOf(result)))
            result.toMediaResult(key)
        }
    }

    override suspend fun move(
        keys: List<MediaKey>,
        targetDir: String,
        onProgress: (CopyProgress) -> Unit,
    ): List<MediaFileResult> = lock.withLock {
        withContext(NonCancellable) {
            forLocal(keys) { paths ->
                files.move(paths, targetDir, onProgress).also { this@DefaultMediaFileService.keys.rewrite(keyRewrites(it)) }
            }
        }
    }

    override suspend fun copy(
        keys: List<MediaKey>,
        targetDir: String,
        onProgress: (CopyProgress) -> Unit,
    ): List<MediaFileResult> = lock.withLock {
        // A copy is a new file: it starts with no playback state, favourites or memberships.
        forLocal(keys) { paths -> files.copy(paths, targetDir, onProgress) }
    }

    override suspend fun delete(keys: List<MediaKey>): List<MediaFileResult> = lock.withLock {
        withContext(NonCancellable) {
            forLocal(keys) { paths ->
                files.delete(paths).also { results ->
                    // A missing file is as good as deleted: drop its rows too.
                    val gone = results.filter { it.isSuccess || it.error == FileOpError.NOT_FOUND }
                    this@DefaultMediaFileService.keys.remove(gone.map { MediaKey.forFile(it.sourcePath).value })
                }
            }
        }
    }

    override suspend fun createFolder(parentDir: String, name: String): Result<String> {
        val r = files.createDirectory(parentDir, name)
        val path = r.targetPath
        return if (r.isSuccess && path != null) Result.success(path) else Result.failure(FileOpException(r.error ?: FileOpError.IO))
    }

    /** Runs [op] on the local keys' paths and merges UNSUPPORTED results back in input order. */
    private suspend fun forLocal(
        keys: List<MediaKey>,
        op: suspend (List<String>) -> List<FileOpResult>,
    ): List<MediaFileResult> {
        val local = keys.filter { it.isLocalFile }
        val results = if (local.isEmpty()) emptyList() else op(local.map { it.filePath!! })
        val iterator = results.iterator()
        return keys.map { key ->
            if (key.isLocalFile) iterator.next().toMediaResult(key) else MediaFileResult(key, null, FileOpError.UNSUPPORTED)
        }
    }
}

private fun FileOpResult.toMediaResult(key: MediaKey) =
    MediaFileResult(key = key, newKey = targetPath?.let(MediaKey::forFile), error = error)
