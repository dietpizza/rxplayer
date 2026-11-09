package com.dietpizza.rxplayer.core.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * CONTRACT. Lets other modules (`:core:netfs` for SMB/FTP/SFTP/WebDAV) plug custom URI schemes
 * into playback without the player depending on them.
 *
 * Contribute with `@Binds @IntoSet`. `PlaybackController` must route every MediaItem whose URI
 * scheme is in [schemes] through [createDataSourceFactory]; everything else goes to the default
 * (file/content/http) data source.
 */
@UnstableApi
interface SchemeDataSourceFactory {
    /** Lower-case URI schemes handled, e.g. `setOf("smb")`. */
    val schemes: Set<String>

    fun createDataSourceFactory(): DataSource.Factory
}

/** Declares the (possibly empty) set so the graph builds even with no contributors. */
@Module
@InstallIn(SingletonComponent::class)
@UnstableApi
abstract class SchemeDataSourceModule {
    @Multibinds
    abstract fun schemeDataSourceFactories(): Set<SchemeDataSourceFactory>
}
