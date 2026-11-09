package com.dietpizza.rxplayer.core.datastore.di

import com.dietpizza.rxplayer.core.datastore.DataStoreLibrarySettingsRepository
import com.dietpizza.rxplayer.core.datastore.LibrarySettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LibrarySettingsModule {
    @Binds
    @Singleton
    abstract fun bindLibrarySettingsRepository(impl: DataStoreLibrarySettingsRepository): LibrarySettingsRepository
}
