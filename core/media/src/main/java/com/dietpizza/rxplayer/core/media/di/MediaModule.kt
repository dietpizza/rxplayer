package com.dietpizza.rxplayer.core.media.di

import com.dietpizza.rxplayer.core.media.LocalVideoSource
import com.dietpizza.rxplayer.core.media.MediaStoreVideoSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {
    @Binds
    @Singleton
    abstract fun bindLocalVideoSource(impl: MediaStoreVideoSource): LocalVideoSource
}
