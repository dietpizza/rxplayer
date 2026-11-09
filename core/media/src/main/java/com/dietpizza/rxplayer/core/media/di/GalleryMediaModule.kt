package com.dietpizza.rxplayer.core.media.di

import com.dietpizza.rxplayer.core.media.GalleryMediaSource
import com.dietpizza.rxplayer.core.media.MediaStoreGallerySource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GalleryMediaModule {
    @Binds
    @Singleton
    abstract fun bindGalleryMediaSource(impl: MediaStoreGallerySource): GalleryMediaSource
}
