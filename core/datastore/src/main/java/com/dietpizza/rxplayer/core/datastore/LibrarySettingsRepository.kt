package com.dietpizza.rxplayer.core.datastore

import com.dietpizza.rxplayer.core.model.AlbumSort
import com.dietpizza.rxplayer.core.model.FolderField
import com.dietpizza.rxplayer.core.model.VideoField
import com.dietpizza.rxplayer.core.model.LibrarySettings
import com.dietpizza.rxplayer.core.model.MediaSort
import com.dietpizza.rxplayer.core.model.VideoSort
import com.dietpizza.rxplayer.core.model.VideoViewMode
import kotlinx.coroutines.flow.Flow

/** Library preferences (plan §8 "Library"). Bound in [com.dietpizza.rxplayer.core.datastore.di.LibrarySettingsModule]. */
interface LibrarySettingsRepository {
    val settings: Flow<LibrarySettings>

    suspend fun setVideoViewMode(mode: VideoViewMode)

    suspend fun setVideoSort(sort: VideoSort)

    suspend fun setShowHidden(show: Boolean)

    /** [LibrarySettings.GRID_COLUMNS_AUTO] or 2..6. */
    suspend fun setGalleryGridColumns(columns: Int)

    suspend fun setAlbumSort(sort: AlbumSort)

    suspend fun setMediaSort(sort: MediaSort)

    suspend fun setVideoFolderSort(sort: AlbumSort)

    suspend fun setFolderViewMode(mode: VideoViewMode)

    suspend fun setFolderFields(fields: Set<FolderField>)

    suspend fun setVideoFields(fields: Set<VideoField>)

    suspend fun setAlbumsPinned(paths: Collection<String>, pinned: Boolean)

    suspend fun setVideoFoldersPinned(paths: Collection<String>, pinned: Boolean)

    /** A folder was renamed: keep its pins (Gallery and Videos), and those of folders inside it. */
    suspend fun movePins(oldPath: String, newPath: String)
}
