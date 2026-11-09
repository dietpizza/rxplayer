package com.dietpizza.rxplayer.core.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/**
 * Local videos from `MediaStore.Video.Media` on [MediaStore.VOLUME_EXTERNAL] (all external volumes,
 * so SD cards are included). Pending and trashed rows are excluded; `.nomedia` and hidden folders
 * are filtered via [MediaVisibility].
 */
@Singleton
class MediaStoreVideoSource @Inject constructor(
    @ApplicationContext context: Context,
) : LocalVideoSource {

    private val resolver: ContentResolver = context.contentResolver
    private val collection: Uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override val videos: Flow<List<LocalVideo>> = changeSignals()
        // Initial signal passes straight through; observer bursts (a scan touches many rows) settle first.
        .debounce { if (it == Signal.Initial) 0L else DEBOUNCE_MS }
        .mapLatest { queryAll() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)
        // Keep the observer alive briefly across config changes / screen hops; replay the last list.
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 1)

    override suspend fun videoAt(path: String): LocalVideo? = withContext(Dispatchers.IO) {
        query(selection = "${MediaStore.Video.Media.DATA} = ?", selectionArgs = arrayOf(path), visibility = null)
            .firstOrNull()
    }

    private enum class Signal { Initial, Changed }

    private fun changeSignals(): Flow<Signal> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Signal.Changed)
            }
        }
        trySend(Signal.Initial)
        resolver.registerContentObserver(collection, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    private fun queryAll(): List<LocalVideo> =
        query(selection = "${MediaStore.Video.Media.DATA} IS NOT NULL", selectionArgs = null, visibility = MediaVisibility())

    @Suppress("DEPRECATION") // DATA is the only way to get real paths; we hold MANAGE_EXTERNAL_STORAGE.
    private fun query(selection: String, selectionArgs: Array<String>?, visibility: MediaVisibility?): List<LocalVideo> {
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_EXCLUDE)
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_EXCLUDE)
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            if (selectionArgs != null) putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
        }
        val cursor = try {
            resolver.query(collection, PROJECTION, args, null)
        } catch (_: SecurityException) {
            null // Permission not granted yet: behave as an empty library.
        } ?: return emptyList()
        return cursor.use { it.readAll(visibility) }
    }

    @Suppress("DEPRECATION")
    private fun Cursor.readAll(visibility: MediaVisibility?): List<LocalVideo> {
        val idCol = getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val dataCol = getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
        val nameCol = getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val durationCol = getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val sizeCol = getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val widthCol = getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
        val heightCol = getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
        val modifiedCol = getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

        val out = ArrayList<LocalVideo>(count)
        while (moveToNext()) {
            val path = getString(dataCol) ?: continue
            val slash = path.lastIndexOf('/')
            if (slash <= 0) continue
            if (visibility != null && !visibility.isVisible(path)) continue
            val id = getLong(idCol)
            out += LocalVideo(
                id = id,
                contentUri = ContentUris.withAppendedId(collection, id).toString(),
                path = path,
                folderPath = path.substring(0, slash),
                displayName = getString(nameCol) ?: path.substring(slash + 1),
                durationMs = getLong(durationCol),
                sizeBytes = getLong(sizeCol),
                width = getInt(widthCol),
                height = getInt(heightCol),
                dateModifiedMs = getLong(modifiedCol) * 1000L, // DATE_MODIFIED is in seconds
            )
        }
        return out
    }

    private companion object {
        const val DEBOUNCE_MS = 300L

        @Suppress("DEPRECATION")
        val PROJECTION = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_MODIFIED,
        )
    }
}
