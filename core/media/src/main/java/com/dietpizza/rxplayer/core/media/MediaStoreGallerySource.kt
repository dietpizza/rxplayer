package com.dietpizza.rxplayer.core.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import com.dietpizza.rxplayer.core.model.GalleryItem
import com.dietpizza.rxplayer.core.model.MediaKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

/** Source of local photos + videos for the Gallery. An interface so repositories are testable. */
interface GalleryMediaSource {
    /**
     * Every visible local photo and video (unsorted). Emits once immediately on collection, then
     * re-queries after MediaStore changes (debounced ~300 ms). Shared: concurrent collectors reuse
     * one query/observer. Only emits when the list actually changed.
     */
    val media: Flow<List<GalleryItem>>

    /** Single lookup by absolute path. Does NOT apply the `.nomedia` / hidden-folder filter. */
    suspend fun itemAt(path: String): GalleryItem?
}

/**
 * Photos and videos from `MediaStore.Files` (media types IMAGE and VIDEO) on
 * [MediaStore.VOLUME_EXTERNAL]. Same rules as [MediaStoreVideoSource]: pending and trashed rows are
 * excluded, `.nomedia` and hidden folders are filtered via [MediaVisibility].
 *
 * One flat query with a narrow projection; a 50k-row library reads in a few hundred ms on IO and
 * is never re-read unless MediaStore changes.
 */
@Singleton
class MediaStoreGallerySource @Inject constructor(
    @ApplicationContext context: Context,
) : GalleryMediaSource {

    private val resolver: ContentResolver = context.contentResolver
    private val files: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    private val images: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    private val videos: Uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override val media: Flow<List<GalleryItem>> = changeSignals()
        .debounce { if (it == Signal.Initial) 0L else DEBOUNCE_MS }
        .mapLatest { queryAll() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 1)

    @Suppress("DEPRECATION")
    override suspend fun itemAt(path: String): GalleryItem? = withContext(Dispatchers.IO) {
        query(
            selection = "$MEDIA_TYPE_SELECTION AND ${MediaStore.Files.FileColumns.DATA} = ?",
            selectionArgs = arrayOf(path),
            visibility = null,
        ).firstOrNull()
    }

    private enum class Signal { Initial, Changed }

    private fun changeSignals(): Flow<Signal> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Signal.Changed)
            }
        }
        trySend(Signal.Initial)
        // MediaProvider notifies on the typed collections; observe both rather than relying on
        // the files URI being notified too.
        resolver.registerContentObserver(images, true, observer)
        resolver.registerContentObserver(videos, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    @Suppress("DEPRECATION")
    private fun queryAll(): List<GalleryItem> = query(
        selection = "$MEDIA_TYPE_SELECTION AND ${MediaStore.Files.FileColumns.DATA} IS NOT NULL",
        selectionArgs = null,
        visibility = MediaVisibility(),
    )

    private fun query(selection: String, selectionArgs: Array<String>?, visibility: MediaVisibility?): List<GalleryItem> {
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_EXCLUDE)
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_EXCLUDE)
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            if (selectionArgs != null) putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
        }
        val cursor = try {
            resolver.query(files, PROJECTION, args, null)
        } catch (_: SecurityException) {
            null // Permission not granted yet: behave as an empty library.
        } ?: return emptyList()
        return cursor.use { it.readAll(visibility) }
    }

    @Suppress("DEPRECATION")
    private fun Cursor.readAll(visibility: MediaVisibility?): List<GalleryItem> {
        val idCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val dataCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
        val typeCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val nameCol = getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val mimeCol = getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
        val takenCol = getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
        val modifiedCol = getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
        val sizeCol = getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
        val widthCol = getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
        val heightCol = getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
        val durationCol = getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
        val orientationCol = getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)

        val out = ArrayList<GalleryItem>(count)
        while (moveToNext()) {
            val path = getString(dataCol) ?: continue
            val slash = path.lastIndexOf('/')
            if (slash <= 0) continue
            if (visibility != null && !visibility.isVisible(path)) continue
            val id = getLong(idCol)
            val isVideo = getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val modifiedMs = getLong(modifiedCol) * 1000L // DATE_MODIFIED is in seconds
            val takenMs = if (isNull(takenCol)) 0L else getLong(takenCol) // DATE_TAKEN is in ms
            out += GalleryItem(
                key = MediaKey.forFile(path),
                id = id,
                contentUri = ContentUris.withAppendedId(if (isVideo) videos else images, id).toString(),
                path = path,
                folderPath = path.substring(0, slash),
                displayName = getString(nameCol) ?: path.substring(slash + 1),
                mimeType = getString(mimeCol).orEmpty(),
                isVideo = isVideo,
                dateTakenMs = if (takenMs > 0L) takenMs else modifiedMs,
                dateModifiedMs = modifiedMs,
                sizeBytes = getLong(sizeCol),
                width = getInt(widthCol),
                height = getInt(heightCol),
                durationMs = if (isVideo) getLong(durationCol) else 0L,
                orientation = if (isNull(orientationCol)) 0 else getInt(orientationCol),
            )
        }
        return out
    }

    private companion object {
        const val DEBOUNCE_MS = 300L

        const val MEDIA_TYPE_SELECTION = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (" +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"

        @Suppress("DEPRECATION")
        val PROJECTION = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.ORIENTATION,
        )
    }
}
