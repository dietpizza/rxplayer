package com.dietpizza.rxplayer.core.data

import com.dietpizza.rxplayer.core.database.PlaybackStateDao
import com.dietpizza.rxplayer.core.database.toModel
import com.dietpizza.rxplayer.core.media.LocalVideoSource
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.PlaybackState
import com.dietpizza.rxplayer.core.model.VideoFolder
import com.dietpizza.rxplayer.core.model.VideoItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

@Singleton
class LocalVideoRepository internal constructor(
    private val source: LocalVideoSource,
    private val dao: PlaybackStateDao,
    scope: CoroutineScope,
) : VideoRepository, LocalVideoIndex {

    @Inject
    constructor(source: LocalVideoSource, dao: PlaybackStateDao) :
        this(source, dao, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    /** Local playback states keyed by MediaKey.value; rebuilt once per Room emission. */
    private val localStates: Flow<Map<String, PlaybackState>> = dao.observeAll()
        .distinctUntilChanged()
        .map { rows ->
            val map = HashMap<String, PlaybackState>(rows.size * 2)
            for (row in rows) if (row.mediaKey.startsWith("file://")) map[row.mediaKey] = row.toModel()
            map
        }

    /** One shared join for all collectors (folders list + any open folder). */
    private val library: Flow<VideoLibrary> =
        combine(source.videos, localStates, ::buildVideoLibrary)
            .flowOn(Dispatchers.Default)
            .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 1)

    override fun folders(): Flow<List<VideoFolder>> =
        library.map { it.folders }.distinctUntilChanged().flowOn(Dispatchers.Default)

    override fun videosIn(folderPath: String): Flow<List<VideoItem>> {
        val path = folderPath.trimEnd('/')
        return library.map { it.videosByFolder[path].orEmpty() }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    /** Same shared join as [folders]; the library only emits when MediaStore or Room changed. */
    override fun videosByKey(): Flow<Map<String, VideoItem>> =
        library.map { it.videosByKey }.flowOn(Dispatchers.Default)

    override suspend fun video(key: MediaKey): VideoItem? {
        val path = key.filePath ?: return null
        val local = source.videoAt(path) ?: return null
        return local.toVideoItem(dao.get(key.value)?.toModel())
    }
}
