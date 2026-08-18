package com.mediavault.downloader.repository

import android.content.Context
import androidx.work.*
import com.mediavault.downloader.detector.PlatformDetector
import com.mediavault.downloader.extractor.UniversalMediaExtractor
import com.mediavault.downloader.model.MediaInfo
import com.mediavault.downloader.model.Platform
import com.mediavault.downloader.worker.DownloadWorker
import com.mediavault.storage.db.dao.CookieDao
import com.mediavault.storage.db.dao.DownloadDao
import com.mediavault.storage.db.dao.QueueDao
import com.mediavault.storage.db.entity.DownloadEntity
import com.mediavault.storage.db.entity.QueueItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val universalMediaExtractor: UniversalMediaExtractor,
    private val platformDetector: PlatformDetector,
    private val queueDao: QueueDao,
    private val downloadDao: DownloadDao,
    private val cookieDao: CookieDao
) {
    val activeQueueFlow: Flow<List<QueueItemEntity>> = queueDao.getAllQueued()
    val historyFlow: Flow<List<DownloadEntity>> = downloadDao.getAllFlow()

    suspend fun fetchMediaInfo(url: String, onStatusUpdate: ((String) -> Unit)? = null): MediaInfo {
        val resolvedUrl = platformDetector.resolveRedirects(url)
        val platform = platformDetector.detect(resolvedUrl)

        // Combinar todas las cookies de los subdominios de la plataforma
        val cookiesList = cookieDao.getAllByPlatform(platform.name)
        val cookieHeader = if (cookiesList.isNotEmpty()) {
            cookiesList.map { it.cookieString }.filter { it.isNotBlank() }.joinToString("; ")
        } else null

        Timber.tag("MediaVaultDebug").d("fetchMediaInfo para $resolvedUrl con plataforma $platform (Cookies activas: ${cookiesList.size})")
        return universalMediaExtractor.extract(resolvedUrl, cookieHeader, onStatusUpdate)
    }

    suspend fun checkDuplicate(url: String): DownloadEntity? {
        return downloadDao.getByUrl(url)
    }

    suspend fun enqueueDownload(
        url: String,
        title: String,
        platform: Platform,
        formatId: String,
        quality: String,
        audioFormat: String? = null,
        audioBitrate: String? = null,
        trimStart: Long? = null,
        trimEnd: Long? = null,
        burnSubtitles: Boolean = false,
        subtitleLang: String? = null,
        downloadThumbnailOnly: Boolean = false,
        scheduledDelayMinutes: Long = 0,
        wifiOnly: Boolean = false,
        speedLimitKbps: Int = 0
    ): Long {
        Timber.tag("MediaVaultDebug").d("Encolando descarga: '$title' ($formatId) - Programada en $scheduledDelayMinutes mins")

        val queueItem = QueueItemEntity(
            url = url,
            title = title,
            platform = platform.name,
            selectedFormat = formatId,
            selectedQuality = quality,
            audioFormat = audioFormat,
            audioBitrate = audioBitrate,
            scheduledAt = if (scheduledDelayMinutes > 0) System.currentTimeMillis() + (scheduledDelayMinutes * 60 * 1000) else null,
            priority = 1,
            status = "PENDING",
            progress = 0,
            speed = 0,
            eta = 0,
            errorMessage = null,
            createdAt = System.currentTimeMillis(),
            downloadStart = null,
            trimStart = trimStart,
            trimEnd = trimEnd,
            burnSubtitles = burnSubtitles,
            subtitleLang = subtitleLang,
            downloadThumbnailOnly = downloadThumbnailOnly
        )
        val id = queueDao.insert(queueItem)

        val isAudio = audioFormat != null && !audioFormat.equals("none", ignoreCase = true)

        val workData = Data.Builder()
            .putLong(DownloadWorker.KEY_QUEUE_ITEM_ID, id)
            .putString(DownloadWorker.KEY_URL, url)
            .putString(DownloadWorker.KEY_TITLE, title)
            .putString(DownloadWorker.KEY_PLATFORM, platform.name)
            .putString(DownloadWorker.KEY_FORMAT_ID, formatId)
            .putBoolean(DownloadWorker.KEY_AUDIO_ONLY, isAudio)
            .putString(DownloadWorker.KEY_AUDIO_FORMAT, audioFormat)
            .putString(DownloadWorker.KEY_AUDIO_BITRATE, audioBitrate)
            .putBoolean(DownloadWorker.KEY_BURN_SUBTITLES, burnSubtitles)
            .putString(DownloadWorker.KEY_SUBTITLE_LANG, subtitleLang)
            .putBoolean(DownloadWorker.KEY_EMBED_METADATA, true)
            .putBoolean(DownloadWorker.KEY_EMBED_THUMBNAIL, true)
            .putBoolean(DownloadWorker.KEY_WIFI_ONLY, wifiOnly)
            .putInt(DownloadWorker.KEY_SPEED_LIMIT_KBPS, speedLimitKbps)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workData)
            .setConstraints(constraints)
            .apply {
                if (scheduledDelayMinutes > 0) {
                    setInitialDelay(scheduledDelayMinutes, TimeUnit.MINUTES)
                }
            }
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "download_$id",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        return id
    }

    suspend fun pauseDownload(id: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("download_$id")
        queueDao.updateStatus(id, "PAUSED")
    }

    suspend fun cancelDownload(id: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("download_$id")
        queueDao.updateStatus(id, "CANCELLED")
    }

    suspend fun retryDownload(item: QueueItemEntity) {
        enqueueDownload(
            url = item.url,
            title = item.title,
            platform = try { Platform.valueOf(item.platform) } catch (e: Exception) { Platform.GENERIC },
            formatId = item.selectedFormat,
            quality = item.selectedQuality,
            audioFormat = item.audioFormat,
            audioBitrate = item.audioBitrate,
            trimStart = item.trimStart,
            trimEnd = item.trimEnd,
            burnSubtitles = item.burnSubtitles,
            subtitleLang = item.subtitleLang,
            downloadThumbnailOnly = item.downloadThumbnailOnly
        )
    }
}
