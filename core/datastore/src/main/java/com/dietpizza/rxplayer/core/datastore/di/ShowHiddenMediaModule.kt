package com.dietpizza.rxplayer.core.datastore.di

import com.dietpizza.rxplayer.core.datastore.LibrarySettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Feeds `LibrarySettings.showHidden` to the MediaStore sources in `:core:media`, which inject it as
 * `@Named("rx.media.showHidden") Flow<Boolean>` (see `SHOW_HIDDEN_MEDIA` there). A string-named
 * binding keeps `:core:media` and `:core:datastore` independent of each other.
 */
@Module
@InstallIn(SingletonComponent::class)
object ShowHiddenMediaModule {
    const val NAME = com.dietpizza.rxplayer.core.model.SHOW_HIDDEN_MEDIA_BINDING

    @Provides
    @Named(NAME)
    fun provideShowHiddenMedia(settings: LibrarySettingsRepository): @JvmSuppressWildcards Flow<Boolean> =
        settings.settings.map { it.showHidden }.distinctUntilChanged()
}
