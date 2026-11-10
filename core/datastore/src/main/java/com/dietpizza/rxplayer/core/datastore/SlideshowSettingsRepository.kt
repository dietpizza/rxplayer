package com.dietpizza.rxplayer.core.datastore

import com.dietpizza.rxplayer.core.model.SlideshowOptions
import kotlinx.coroutines.flow.Flow

/**
 * The last-used slideshow options (the sheet opens with them). Bound in
 * [com.dietpizza.rxplayer.core.datastore.di.SlideshowSettingsModule].
 */
interface SlideshowSettingsRepository {
    val options: Flow<SlideshowOptions>

    suspend fun setOptions(options: SlideshowOptions)
}
