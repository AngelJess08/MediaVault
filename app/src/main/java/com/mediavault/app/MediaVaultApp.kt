package com.mediavault.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class MediaVaultApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        com.mediavault.app.util.GlobalCrashHandler.install(this)
        Timber.plant(Timber.DebugTree())
        Timber.tag("MediaVaultDownload").d("MediaVault Application inicializada correctamente con Hilt, WorkManager y GlobalCrashHandler")
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
}
