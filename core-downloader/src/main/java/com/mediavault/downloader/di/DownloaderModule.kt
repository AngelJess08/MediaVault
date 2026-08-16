package com.mediavault.downloader.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DownloaderModule {
    // Clases en core-downloader usan constructor injection con @Inject @Singleton
}
