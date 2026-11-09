package com.dietpizza.rxplayer.core.database.di

import android.content.Context
import androidx.room.Room
import com.dietpizza.rxplayer.core.database.AlbumDao
import com.dietpizza.rxplayer.core.database.FavouriteDao
import com.dietpizza.rxplayer.core.database.MediaKeyDao
import com.dietpizza.rxplayer.core.database.PlaybackStateDao
import com.dietpizza.rxplayer.core.database.PlaylistDao
import com.dietpizza.rxplayer.core.database.RxDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RxDatabase =
        Room.databaseBuilder(context, RxDatabase::class.java, RxDatabase.NAME).build()

    @Provides
    fun providePlaybackStateDao(db: RxDatabase): PlaybackStateDao = db.playbackStateDao()

    @Provides
    fun provideFavouriteDao(db: RxDatabase): FavouriteDao = db.favouriteDao()

    @Provides
    fun provideAlbumDao(db: RxDatabase): AlbumDao = db.albumDao()

    @Provides
    fun providePlaylistDao(db: RxDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideMediaKeyDao(db: RxDatabase): MediaKeyDao = db.mediaKeyDao()
}
