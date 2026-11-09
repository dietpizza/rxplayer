package com.dietpizza.rxplayer

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RxApplication : Application(), SingletonImageLoader.Factory {

    /** The shared loader from :core:ui (MediaStore thumbnail fetcher + video frame fallback). */
    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    // Any plain AsyncImage without an explicit loader uses the same instance and caches.
    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()
}
