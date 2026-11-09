package com.dietpizza.rxplayer.core.data

import com.dietpizza.rxplayer.core.media.GalleryMediaSource
import com.dietpizza.rxplayer.core.model.AlbumSort
import com.dietpizza.rxplayer.core.model.GalleryAlbum
import com.dietpizza.rxplayer.core.model.GalleryItem
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.MediaSort
import com.dietpizza.rxplayer.core.model.VirtualAlbum
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

/**
 * CONTRACT (Gallery, plan §4 #2–4, §5). Local photos + videos from MediaStore, grouped into folder
 * albums and built-in [VirtualAlbum]s. All flows share one upstream query/grouping, re-emit when
 * MediaStore changes, emit only on real changes, and do their work off the main thread.
 */
interface GalleryRepository {
    /**
     * Folders containing photos or videos, in [sort] order (default: most recently modified first).
     * The `DCIM/Camera` folders are not included; they're the pinned [VirtualAlbum.CAMERA].
     */
    fun albums(sort: AlbumSort = AlbumSort.DEFAULT): Flow<List<GalleryAlbum>>

    /** Non-empty virtual albums in pinned-row order: All videos, then Camera. */
    fun virtualAlbums(): Flow<List<GalleryAlbum>>

    /** Items directly inside [albumPath], in [sort] order (default: date taken, newest first). */
    fun items(albumPath: String, sort: MediaSort = MediaSort.DEFAULT): Flow<List<GalleryItem>>

    /** Items of a virtual album, in [sort] order. */
    fun items(album: VirtualAlbum, sort: MediaSort = MediaSort.DEFAULT): Flow<List<GalleryItem>>

    /** One item by key (photo or video), or null if it isn't a local MediaStore item. Unfiltered. */
    suspend fun item(key: MediaKey): GalleryItem?
}

@Singleton
class LocalGalleryRepository internal constructor(
    private val source: GalleryMediaSource,
    scope: CoroutineScope,
) : GalleryRepository {

    @Inject
    constructor(source: GalleryMediaSource) : this(source, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    /** One shared grouping for all collectors (albums grid + any open album + the viewer). */
    private val library: Flow<GalleryLibrary> = source.media
        .map(::buildGalleryLibrary)
        .flowOn(Dispatchers.Default)
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 1)

    override fun albums(sort: AlbumSort): Flow<List<GalleryAlbum>> =
        library.map { sortAlbums(it.albums, sort) }.distinctUntilChanged().flowOn(Dispatchers.Default)

    override fun virtualAlbums(): Flow<List<GalleryAlbum>> =
        library.map { it.virtualAlbums }.distinctUntilChanged().flowOn(Dispatchers.Default)

    override fun items(albumPath: String, sort: MediaSort): Flow<List<GalleryItem>> {
        val path = albumPath.trimEnd('/')
        // Compare the default-ordered slice first, so unrelated library changes skip the sort.
        return library.map { it.itemsByFolder[path].orEmpty() }
            .distinctUntilChanged()
            .map { sortItems(it, sort) }
            .flowOn(Dispatchers.Default)
    }

    override fun items(album: VirtualAlbum, sort: MediaSort): Flow<List<GalleryItem>> =
        library.map { it.items(album) }
            .distinctUntilChanged()
            .map { sortItems(it, sort) }
            .flowOn(Dispatchers.Default)

    override suspend fun item(key: MediaKey): GalleryItem? {
        val path = key.filePath ?: return null
        return source.itemAt(path)
    }
}
