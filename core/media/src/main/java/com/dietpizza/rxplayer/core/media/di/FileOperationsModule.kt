package com.dietpizza.rxplayer.core.media.di

import com.dietpizza.rxplayer.core.media.AndroidMediaScanner
import com.dietpizza.rxplayer.core.media.FileOperations
import com.dietpizza.rxplayer.core.media.FileSystemOperations
import com.dietpizza.rxplayer.core.media.MediaScanner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FileOperationsModule {
    @Binds
    @Singleton
    abstract fun bindMediaScanner(impl: AndroidMediaScanner): MediaScanner

    @Binds
    abstract fun bindFileOperations(impl: FileSystemOperations): FileOperations
}
