package com.dietpizza.rxplayer.core.datastore.di

import com.dietpizza.rxplayer.core.datastore.DataStoreSlideshowSettingsRepository
import com.dietpizza.rxplayer.core.datastore.SlideshowSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SlideshowSettingsModule {
    @Binds
    @Singleton
    abstract fun bindSlideshowSettingsRepository(impl: DataStoreSlideshowSettingsRepository): SlideshowSettingsRepository
}
