package com.dietpizza.rxplayer.core.data.di

import com.dietpizza.rxplayer.core.data.LocalVideoRepository
import com.dietpizza.rxplayer.core.data.PlaybackStateRepository
import com.dietpizza.rxplayer.core.data.RoomPlaybackStateRepository
import com.dietpizza.rxplayer.core.data.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindVideoRepository(impl: LocalVideoRepository): VideoRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackStateRepository(impl: RoomPlaybackStateRepository): PlaybackStateRepository
}
