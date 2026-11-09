package com.dietpizza.rxplayer.core.datastore.di

import com.dietpizza.rxplayer.core.datastore.DataStorePlayerSettingsRepository
import com.dietpizza.rxplayer.core.datastore.PlayerSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerSettingsModule {
    @Binds
    @Singleton
    abstract fun bindPlayerSettingsRepository(
        impl: DataStorePlayerSettingsRepository,
    ): PlayerSettingsRepository
}
