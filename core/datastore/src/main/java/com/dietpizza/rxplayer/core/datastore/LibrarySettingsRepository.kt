package com.dietpizza.rxplayer.core.datastore

import com.dietpizza.rxplayer.core.model.LibrarySettings
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
}
