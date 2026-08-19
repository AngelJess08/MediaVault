package com.mediavault.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mediavault.downloader.detector.PlatformDetector
import com.mediavault.downloader.repository.DownloadRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class DownloadBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var downloadRepository: DownloadRepository

    @Inject
    lateinit var platformDetector: PlatformDetector

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_ENQUEUE_DOWNLOAD) {
            val url = intent.getStringExtra(EXTRA_URL)
            if (!url.isNullOrBlank()) {
                val quality = intent.getStringExtra(EXTRA_QUALITY) ?: "1080p"
                val format = intent.getStringExtra(EXTRA_FORMAT) ?: "best"
                val audioOnly = intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false)

                Timber.tag("MediaVaultBroadcast").d("Petición de descarga recibida vía Broadcast/Tasker para: $url")

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val platform = platformDetector.detect(url)
                        downloadRepository.enqueueDownload(
                            url = url,
                            title = "Descarga Automatizada (${platform.name})",
                            platform = platform,
                            formatId = format,
                            quality = quality,
                            audioFormat = if (audioOnly) "mp3" else null
                        )
                    } catch (e: Exception) {
                        Timber.tag("MediaVaultBroadcast").e(e, "Error al procesar descarga por broadcast")
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_ENQUEUE_DOWNLOAD = "com.mediavault.app.ACTION_ENQUEUE_DOWNLOAD"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_QUALITY = "extra_quality"
        const val EXTRA_FORMAT = "extra_format"
        const val EXTRA_AUDIO_ONLY = "extra_audio_only"
    }
}
