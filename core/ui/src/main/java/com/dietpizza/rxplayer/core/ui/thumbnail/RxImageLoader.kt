package com.dietpizza.rxplayer.core.ui.thumbnail

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Builds the app's one shared Coil [ImageLoader] (thumbnails for Videos now, Gallery in M4). */
object RxImageLoader {
    /** Short enough to read as "settling in", not as an animation. Memory-cache hits never fade. */
    private const val CROSSFADE_MS = 120

    fun create(context: Context): ImageLoader {
        val app = context.applicationContext
        return ImageLoader.Builder(app)
            .memoryCache { MemoryCache.Builder().maxSizePercent(app, 0.25).build() }
            .crossfade(CROSSFADE_MS)
            .components {
                add(MediaStoreThumbnailKeyer())
                add(MediaStoreThumbnailFetcher.Factory())
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {
    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader = RxImageLoader.create(context)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ImageLoaderEntryPoint {
    fun imageLoader(): ImageLoader
}

/** Overrides the loader [rememberRxImageLoader] returns (tests, previews). Null = the Hilt singleton. */
val LocalRxImageLoader: ProvidableCompositionLocal<ImageLoader?> = staticCompositionLocalOf { null }

/** The shared [ImageLoader]: [LocalRxImageLoader] if provided, else the Hilt `@Singleton`. */
@Composable
fun rememberRxImageLoader(): ImageLoader {
    LocalRxImageLoader.current?.let { return it }
    val app = LocalContext.current.applicationContext
    return remember(app) {
        EntryPointAccessors.fromApplication(app, ImageLoaderEntryPoint::class.java).imageLoader()
    }
}
