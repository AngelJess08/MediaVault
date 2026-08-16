package com.mediavault.upscale.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UpscaleModule {
    // Los repositorios y clientes usan @Inject @Singleton directamente
}
