package com.dietpizza.rxplayer.core.data

import com.dietpizza.rxplayer.core.database.AlbumDao
import com.dietpizza.rxplayer.core.database.FavouriteDao
import com.dietpizza.rxplayer.core.database.FavouriteEntity
import com.dietpizza.rxplayer.core.database.AlbumItemEntity
import com.dietpizza.rxplayer.core.database.MediaKeyDao
import com.dietpizza.rxplayer.core.database.PlaybackStateDao
import com.dietpizza.rxplayer.core.database.PlaylistDao
import com.dietpizza.rxplayer.core.database.PlaylistWithStats
import com.dietpizza.rxplayer.core.model.CustomAlbum
import com.dietpizza.rxplayer.core.model.HistoryEntry
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.Playlist
import com.dietpizza.rxplayer.core.model.PlaylistEntry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Singleton
class RoomFavouritesRepository internal constructor(
    private val dao: FavouriteDao,
    private val clock: () -> Long,
) : FavouritesRepository {

    @Inject
    constructor(dao: FavouriteDao) : this(dao, System::currentTimeMillis)

    override val favourites: Flow<Set<MediaKey>> = dao.observeKeys()
        .distinctUntilChanged()
        .map { keys -> keys.mapTo(LinkedHashSet(keys.size * 2)) { MediaKey(it) } }

    override fun observeIsFavourite(key: MediaKey): Flow<Boolean> =
        dao.observeIsFavourite(key.value).distinctUntilChanged()

    override suspend fun isFavourite(key: MediaKey): Boolean = dao.isFavourite(key.value)

    override suspend fun toggle(key: MediaKey): Boolean = dao.toggle(key.value, clock())

    override suspend fun setFavourite(keys: List<MediaKey>, favourite: Boolean) {
        if (keys.isEmpty()) return
        if (favourite) {
            val now = clock()
            dao.insert(keys.map { FavouriteEntity(it.value, now) })
        } else {
            for (chunk in keys.chunked(500)) dao.delete(chunk.map { it.value })
        }
    }
}

@Singleton
class RoomAlbumsRepository internal constructor(
    private val dao: AlbumDao,
    private val clock: () -> Long,
) : AlbumsRepository {

    @Inject
    constructor(dao: AlbumDao) : this(dao, System::currentTimeMillis)

    override fun albums(): Flow<List<CustomAlbum>> = dao.observeAlbums()
        .distinctUntilChanged()
        .map { rows ->
            rows.map {
                CustomAlbum(
                    id = it.id,
                    name = it.name,
                    itemCount = it.itemCount,
                    coverKey = it.coverKey?.let(::MediaKey),
                    createdAt = it.createdAt,
                    sortIndex = it.sortIndex,
                )
            }
        }

    override fun items(albumId: Long): Flow<List<MediaKey>> =
        dao.observeItemKeys(albumId).distinctUntilChanged().map { keys -> keys.map(::MediaKey) }

    override fun albumsContaining(key: MediaKey): Flow<Set<Long>> =
        dao.observeAlbumIdsContaining(key.value).distinctUntilChanged().map { it.toSet() }

    override suspend fun create(name: String, keys: List<MediaKey>): Long =
        dao.create(name.trim(), clock(), keys.map { it.value }.distinct())

    override suspend fun rename(albumId: Long, name: String) = dao.rename(albumId, name.trim())

    override suspend fun delete(albumId: Long) = dao.deleteAlbum(albumId)

    override suspend fun add(albumId: Long, keys: List<MediaKey>) {
        if (keys.isEmpty()) return
        val now = clock()
        dao.insertItems(keys.map { AlbumItemEntity(albumId, it.value, now) })
    }

    override suspend fun remove(albumId: Long, keys: List<MediaKey>) {
        for (chunk in keys.chunked(500)) dao.removeItems(albumId, chunk.map { it.value })
    }

    override suspend fun reorder(orderedIds: List<Long>) = dao.reorder(orderedIds)

    override suspend fun setCover(albumId: Long, key: MediaKey?) = dao.setCover(albumId, key?.value)
}

@Singleton
class RoomPlaylistsRepository internal constructor(
    private val dao: PlaylistDao,
    private val index: LocalVideoIndex,
    private val clock: () -> Long,
) : PlaylistsRepository {

    @Inject
    constructor(dao: PlaylistDao, index: LocalVideoIndex) : this(dao, index, System::currentTimeMillis)

    override fun playlists(): Flow<List<Playlist>> =
        dao.observePlaylists().distinctUntilChanged().map { rows -> rows.map { it.toModel() } }

    override fun playlist(playlistId: Long): Flow<Playlist?> =
        dao.observePlaylist(playlistId).distinctUntilChanged().map { it?.toModel() }

    override fun entries(playlistId: Long): Flow<List<PlaylistEntry>> =
        combine(dao.observeItemKeys(playlistId).distinctUntilChanged(), index.videosByKey(), ::buildPlaylistEntries)
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override suspend fun create(name: String, keys: List<MediaKey>): Long =
        dao.create(name.trim(), clock(), keys.map { it.value }.distinct())

    override suspend fun rename(playlistId: Long, name: String) = dao.rename(playlistId, name.trim(), clock())

    override suspend fun delete(playlistId: Long) = dao.deletePlaylist(playlistId)

    override suspend fun add(playlistId: Long, keys: List<MediaKey>): Int {
        var added = 0
        dao.editItems(playlistId, clock()) { current ->
            PlaylistOrder.append(current, keys.map { it.value }).also { added = it.size - current.size }
        }
        return added
    }

    override suspend fun remove(playlistId: Long, keys: List<MediaKey>) =
        dao.editItems(playlistId, clock()) { PlaylistOrder.remove(it, keys.map { k -> k.value }) }

    override suspend fun move(playlistId: Long, from: Int, to: Int) =
        dao.editItems(playlistId, clock()) { PlaylistOrder.move(it, from, to) }

    override suspend fun setOrder(playlistId: Long, keys: List<MediaKey>) =
        dao.editItems(playlistId, clock()) { PlaylistOrder.applyOrder(it, keys.map { k -> k.value }) }
}

private fun PlaylistWithStats.toModel() = Playlist(
    id = id,
    name = name,
    itemCount = itemCount,
    coverKey = coverKey?.let(::MediaKey),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

@Singleton
class RoomHistoryRepository @Inject constructor(
    private val dao: PlaybackStateDao,
    private val index: LocalVideoIndex,
) : HistoryRepository {

    override fun history(limit: Int): Flow<List<HistoryEntry>> =
        combine(dao.observeHistory(limit).distinctUntilChanged(), index.videosByKey(), ::buildHistory)
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override suspend fun clear() = dao.clearHistory()

    override suspend fun remove(keys: List<MediaKey>) {
        for (chunk in keys.chunked(500)) dao.removeFromHistory(chunk.map { it.value })
    }
}

/** Cross-table key maintenance; an interface so [DefaultMediaFileService] is testable without Room. */
interface MediaKeyStore {
    /** Rewrites every `from → to` in all tables, in one transaction. */
    suspend fun rewrite(moves: Map<String, String>)

    /** Removes every row for [keys], in one transaction. */
    suspend fun remove(keys: List<String>)
}

class RoomMediaKeyStore @Inject constructor(private val dao: MediaKeyDao) : MediaKeyStore {
    override suspend fun rewrite(moves: Map<String, String>) {
        if (moves.isNotEmpty()) dao.rewriteKeys(moves)
    }

    override suspend fun remove(keys: List<String>) {
        if (keys.isNotEmpty()) dao.deleteKeys(keys)
    }
}
