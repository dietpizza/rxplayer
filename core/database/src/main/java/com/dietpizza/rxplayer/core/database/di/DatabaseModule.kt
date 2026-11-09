package com.dietpizza.rxplayer.core.database.di

import android.content.Context
import androidx.room.Room
import com.dietpizza.rxplayer.core.database.PlaybackStateDao
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
}
