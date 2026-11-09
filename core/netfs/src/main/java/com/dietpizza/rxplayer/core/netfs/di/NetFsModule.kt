package com.dietpizza.rxplayer.core.netfs.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.room.Room
import com.dietpizza.rxplayer.core.netfs.db.NetFsDatabase
import com.dietpizza.rxplayer.core.netfs.db.NetworkServerDao
import com.dietpizza.rxplayer.core.netfs.playback.NetFsSchemeDataSourceFactory
import com.dietpizza.rxplayer.core.netfs.secret.KeystoreKeyProvider
import com.dietpizza.rxplayer.core.netfs.secret.SecretKeyProvider
import com.dietpizza.rxplayer.core.player.SchemeDataSourceFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@UnstableApi
abstract class NetFsModule {

    @Binds
    abstract fun secretKeyProvider(impl: KeystoreKeyProvider): SecretKeyProvider

    @Binds
    @IntoSet
    abstract fun schemeDataSourceFactory(impl: NetFsSchemeDataSourceFactory): SchemeDataSourceFactory

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): NetFsDatabase =
            Room.databaseBuilder(context, NetFsDatabase::class.java, NetFsDatabase.NAME).build()

        @Provides
        fun serverDao(db: NetFsDatabase): NetworkServerDao = db.serverDao()
    }
}
