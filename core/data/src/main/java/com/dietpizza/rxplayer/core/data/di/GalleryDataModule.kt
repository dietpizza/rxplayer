package com.dietpizza.rxplayer.core.data.di

import com.dietpizza.rxplayer.core.data.GalleryRepository
import com.dietpizza.rxplayer.core.data.LocalGalleryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GalleryDataModule {
    @Binds
    @Singleton
    abstract fun bindGalleryRepository(impl: LocalGalleryRepository): GalleryRepository
}
