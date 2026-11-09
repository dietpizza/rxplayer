package com.dietpizza.rxplayer.core.data.di

import com.dietpizza.rxplayer.core.data.AlbumsRepository
import com.dietpizza.rxplayer.core.data.DefaultMediaFileService
import com.dietpizza.rxplayer.core.data.FavouritesRepository
import com.dietpizza.rxplayer.core.data.HistoryRepository
import com.dietpizza.rxplayer.core.data.LocalVideoIndex
import com.dietpizza.rxplayer.core.data.LocalVideoRepository
import com.dietpizza.rxplayer.core.data.MediaFileService
import com.dietpizza.rxplayer.core.data.MediaKeyStore
import com.dietpizza.rxplayer.core.data.PlaylistsRepository
import com.dietpizza.rxplayer.core.data.RoomAlbumsRepository
import com.dietpizza.rxplayer.core.data.RoomFavouritesRepository
import com.dietpizza.rxplayer.core.data.RoomHistoryRepository
import com.dietpizza.rxplayer.core.data.RoomMediaKeyStore
import com.dietpizza.rxplayer.core.data.RoomPlaylistsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** M5 data features: favourites, custom albums, playlists, history, file operations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryDataModule {
    @Binds
    @Singleton
    abstract fun bindLocalVideoIndex(impl: LocalVideoRepository): LocalVideoIndex

    @Binds
    @Singleton
    abstract fun bindFavouritesRepository(impl: RoomFavouritesRepository): FavouritesRepository

    @Binds
    @Singleton
    abstract fun bindAlbumsRepository(impl: RoomAlbumsRepository): AlbumsRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistsRepository(impl: RoomPlaylistsRepository): PlaylistsRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(impl: RoomHistoryRepository): HistoryRepository

    @Binds
    abstract fun bindMediaKeyStore(impl: RoomMediaKeyStore): MediaKeyStore

    @Binds
    @Singleton
    abstract fun bindMediaFileService(impl: DefaultMediaFileService): MediaFileService
}
