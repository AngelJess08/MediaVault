package com.mediavault.downloader.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.mediavault.downloader.extractor.UniversalMediaExtractor
import com.mediavault.downloader.model.FormatOption
import com.mediavault.downloader.model.MediaInfo
import com.mediavault.downloader.security.FileSafetyValidator
import com.mediavault.storage.db.dao.CookieDao
import com.mediavault.storage.db.dao.DownloadDao
import com.mediavault.storage.db.dao.QueueDao
import com.mediavault.storage.db.entity.DownloadEntity
import com.mediavault.storage.mediastore.MediaStoreHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val universalMediaExtractor: UniversalMediaExtractor,
    private val queueDao: QueueDao,
    private val downloadDao: DownloadDao,
    private val cookieDao: CookieDao,
    private val mediaStoreHelper: MediaStoreHelper,
    private val fileSafetyValidator: FileSafetyValidator
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "MediaVaultDebug"
        const val CHANNEL_ID = "mediavault_download_channel"
        const val NOTIFICATION_ID_BASE = 1000

        const val KEY_QUEUE_ITEM_ID = "KEY_QUEUE_ITEM_ID"
        const val KEY_URL = "KEY_URL"
        const val KEY_TITLE = "KEY_TITLE"
        const val KEY_PLATFORM = "KEY_PLATFORM"
        const val KEY_FORMAT_ID = "KEY_FORMAT_ID"
        const val KEY_AUDIO_ONLY = "KEY_AUDIO_ONLY"
        const val KEY_AUDIO_FORMAT = "KEY_AUDIO_FORMAT"
        const val KEY_AUDIO_BITRATE = "KEY_AUDIO_BITRATE"
        const val KEY_BURN_SUBTITLES = "KEY_BURN_SUBTITLES"
        const val KEY_SUBTITLE_LANG = "KEY_SUBTITLE_LANG"
        const val KEY_EMBED_METADATA = "KEY_EMBED_METADATA"
        const val KEY_EMBED_THUMBNAIL = "KEY_EMBED_THUMBNAIL"
        const val KEY_WIFI_ONLY = "KEY_WIFI_ONLY"
        const val KEY_SPEED_LIMIT_KBPS = "KEY_SPEED_LIMIT_KBPS"
        const val KEY_OUTPUT_PATH = "KEY_OUTPUT_PATH"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val queueItemId = inputData.getLong(KEY_QUEUE_ITEM_ID, 0L)
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Descarga MediaVault"
        val platform = inputData.getString(KEY_PLATFORM) ?: "GENERIC"
        val formatId = inputData.getString(KEY_FORMAT_ID) ?: "best"
        val isAudioOnly = inputData.getBoolean(KEY_AUDIO_ONLY, false)
        val isWifiOnly = inputData.getBoolean(KEY_WIFI_ONLY, false)
        val ext = if (isAudioOnly) "mp3" else "mp4"
        val outputPath = inputData.getString(KEY_OUTPUT_PATH) ?: "${context.cacheDir.absolutePath}/mv_${System.currentTimeMillis()}.$ext"

        val notificationId = (NOTIFICATION_ID_BASE + (queueItemId.toInt() % 10000)).coerceAtLeast(1)

        try {
            Timber.tag(TAG).d("==================================================")
            Timber.tag(TAG).d("Iniciando DownloadWorker para: $title ($url)")
            Timber.tag(TAG).d("Paso 1: Formato=$formatId, isAudioOnly=$isAudioOnly, isWifiOnly=$isWifiOnly")

            // 1. Verificación de conectividad Wi-Fi
            if (isWifiOnly && !isWifiConnected()) {
                Timber.tag(TAG).w("Descarga pausada: Requiere Wi-Fi y el dispositivo está en red móvil")
                if (queueItemId > 0) {
                    queueDao.updateStatus(queueItemId, "PAUSADO_WIFI")
                }
                return@withContext Result.retry()
            }

            // 2. Verificación de espacio en disco (Feature 10)
            val usableSpace = context.cacheDir.usableSpace
            if (usableSpace < 50 * 1024 * 1024L) {
                val errorMsg = "Espacio insuficiente en disco (${usableSpace / (1024 * 1024)} MB disponibles, mínimo 50 MB requeridos)."
                Timber.tag(TAG).e(errorMsg)
                if (queueItemId > 0) {
                    queueDao.updateError(queueItemId, errorMsg)
                }
                throw Exception(errorMsg)
            }

            if (queueItemId > 0) {
                queueDao.updateStatus(queueItemId, "EXTRACTING")
            }
            setForeground(createForegroundInfo(notificationId, "Extrayendo stream: $title", 0))

            // 3. Obtener cookies combinadas para la plataforma
            val cookieEntities = cookieDao.getAllByPlatform(platform)
            val cookieHeader = if (cookieEntities.isNotEmpty()) {
                cookieEntities.map { it.cookieString }.filter { it.isNotBlank() }.joinToString("; ")
            } else null

            if (!cookieHeader.isNullOrBlank()) {
                Timber.tag(TAG).d("Paso 2: Inyectando cookies para plataforma $platform (${cookieEntities.size} registros)")
            } else {
                Timber.tag(TAG).d("Paso 2: Sin cookies para $platform")
            }

            // 4. Extraer URL de stream real
            Timber.tag(TAG).d("Paso 3: Extrayendo información y stream...")
            var mediaInfo = universalMediaExtractor.extract(url, cookieHeader)
            var selectedFormat = mediaInfo.formats.find { it.formatId == formatId }
                ?: mediaInfo.formats.firstOrNull { if (isAudioOnly) it.isAudioOnly else !it.isAudioOnly }
                ?: mediaInfo.formats.firstOrNull()

            var streamUrl = selectedFormat?.streamUrl ?: url
            Timber.tag(TAG).d("Paso 4: Stream seleccionado: ${selectedFormat?.resolution ?: formatId} -> $streamUrl")

            // Validación de seguridad: rechazar ejecutables y archivos peligrosos
            if (fileSafetyValidator.isForbiddenExtension(selectedFormat?.ext) ||
                fileSafetyValidator.isForbiddenUrlOrFilename(streamUrl)
            ) {
                val secError = "Descarga bloqueada por seguridad: extensión o contenido potencialmente peligroso/ejecutable."
                Timber.tag(TAG).e(secError)
                if (queueItemId > 0) queueDao.updateError(queueItemId, secError)
                throw SecurityException(secError)
            }

            if (queueItemId > 0) {
                queueDao.updateStatus(queueItemId, "DOWNLOADING")
            }

            val targetFile = File(outputPath)
            targetFile.parentFile?.mkdirs()

            // 5. Descarga de stream (HLS o HTTP Directo)
            val isHlsStream = streamUrl.contains(".m3u8") || (selectedFormat?.ext == "m3u8")

            if (isHlsStream) {
                downloadHlsStream(
                    m3u8Url = streamUrl,
                    targetFile = targetFile,
                    cookieHeader = cookieHeader,
                    refererUrl = url,
                    title = title,
                    notificationId = notificationId,
                    queueItemId = queueItemId
                )
            } else {
                // Descarga HTTP con auto-refresco de stream expirado (Feature 2)
                var downloadSuccess = false
                var attempts = 0

                while (!downloadSuccess && attempts < 2) {
                    attempts++
                    try {
                        Timber.tag(TAG).d("Paso 5 (Intento $attempts): Conectando a $streamUrl...")
                        val requestBuilder = Request.Builder()
                            .url(streamUrl)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                            .header("Accept", "*/*")
                            .header("Referer", url)

                        if (!cookieHeader.isNullOrBlank()) {
                            requestBuilder.header("Cookie", cookieHeader)
                        }

                        httpClient.newCall(requestBuilder.build()).execute().use { response ->
                            if (!response.isSuccessful) {
                                val code = response.code
                                Timber.tag(TAG).e("Error HTTP $code al descargar stream")
                                if ((code == 403 || code == 410 || code == 401) && attempts == 1) {
                                    Timber.tag(TAG).w("Stream posiblemente expirado o no autorizado (HTTP $code). Refrescando extracción...")
                                    mediaInfo = universalMediaExtractor.extract(url, cookieHeader)
                                    selectedFormat = mediaInfo.formats.find { it.formatId == formatId }
                                        ?: mediaInfo.formats.firstOrNull()
                                    streamUrl = selectedFormat?.streamUrl ?: url
                                    return@use // Reintentar con nueva URL
                                }
                                if (code == 401 || code == 403) {
                                    throw Exception("Login requerido o contenido privado (HTTP $code)")
                                }
                                throw Exception("Servidor respondió con código HTTP $code")
                            }

                            val body = response.body ?: throw Exception("Cuerpo de respuesta HTTP vacío")
                            val contentLength = body.contentLength()
                            val totalBytes = if (contentLength > 0) contentLength else (selectedFormat?.filesizeApprox ?: (20 * 1024 * 1024L))
                            Timber.tag(TAG).d("Paso 6: Descargando ${totalBytes / (1024 * 1024)} MB hacia $outputPath...")

                            val inputStream = body.byteStream()
                            val outputStream = FileOutputStream(targetFile)
                            val buffer = ByteArray(32 * 1024)
                            var bytesRead: Int
                            var totalDownloaded = 0L
                            var lastProgressUpdate = System.currentTimeMillis()
                            var lastBytesDownloaded = 0L

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalDownloaded += bytesRead

                                val now = System.currentTimeMillis()
                                if (now - lastProgressUpdate > 500) {
                                    val percent = if (totalBytes > 0) ((totalDownloaded.toFloat() / totalBytes) * 100f).coerceIn(0f, 99f) else 50f
                                    val progressInt = percent.toInt()
                                    val speedBytesPerSec = ((totalDownloaded - lastBytesDownloaded) * 1000L) / (now - lastProgressUpdate).coerceAtLeast(1)
                                    val speedText = String.format("%.1f MB/s", speedBytesPerSec / (1024.0 * 1024.0))
                                    val remainingBytes = (totalBytes - totalDownloaded).coerceAtLeast(0)
                                    val etaSec = if (speedBytesPerSec > 0) remainingBytes / speedBytesPerSec else 0L

                                    Timber.tag(TAG).d("Progreso: $progressInt% | $speedText | ETA: ${etaSec}s")

                                    notificationManager.notify(
                                        notificationId,
                                        createNotification("Descargando $title...", "$progressInt% - $speedText", progressInt).build()
                                    )

                                    try {
                                        context.sendBroadcast(Intent("com.mediavault.app.ACTION_UPDATE_WIDGET_PROGRESS").apply {
                                            putExtra("EXTRA_STATUS", "$title ($progressInt% - $speedText)")
                                            putExtra("EXTRA_PROGRESS", progressInt)
                                        })
                                    } catch (e: Exception) {}

                                    if (queueItemId > 0) {
                                        queueDao.updateProgress(queueItemId, progressInt, speedBytesPerSec, etaSec)
                                    }

                                    lastProgressUpdate = now
                                    lastBytesDownloaded = totalDownloaded
                                }
                            }

                            outputStream.flush()
                            outputStream.close()
                            inputStream.close()
                            downloadSuccess = true
                            Timber.tag(TAG).d("Paso 6: Descarga de bytes finalizada con éxito (${targetFile.length()} bytes)")
                        }
                    } catch (e: Exception) {
                        if (attempts >= 2) throw e
                        Timber.tag(TAG).w("Error en intento $attempts: ${e.message}. Reintentando con refresco de stream...")
                    }
                }
            }

            if (queueItemId > 0) {
                queueDao.updateStatus(queueItemId, "SAVING")
            }

            // Actualizar widget al completar
            try {
                context.sendBroadcast(Intent("com.mediavault.app.ACTION_UPDATE_WIDGET_PROGRESS").apply {
                    putExtra("EXTRA_STATUS", "Descarga completada: $title")
                    putExtra("EXTRA_PROGRESS", 100)
                })
            } catch (e: Exception) {}

            // Paso 7: Validación de Magic Bytes antes de guardar en Scoped Storage
            if (targetFile.exists() && targetFile.length() > 0) {
                val isBinaryValid = fileSafetyValidator.validateMediaMagicBytes(targetFile)
                if (!isBinaryValid) {
                    Timber.tag(TAG).w("Aviso de seguridad: Los magic bytes del archivo descargado no coinciden con un encabezado multimedia estándar.")
                }
            }

            // Paso 7: Guardar en Scoped Storage (MediaStore)
            Timber.tag(TAG).d("Paso 7: Guardando archivo en Scoped Storage (MediaStore)...")
            val finalUri = if (isAudioOnly) {
                mediaStoreHelper.saveAudio(targetFile, title, null)
            } else {
                mediaStoreHelper.saveVideo(targetFile, title, null)
            }

            val finalFileSize = targetFile.length().coerceAtLeast(1024L)
            targetFile.delete()

            Timber.tag(TAG).d("Paso 7: Archivo guardado exitosamente en URI: $finalUri")

            // Paso 8: Persistir en Room DB
            if (queueItemId > 0) {
                queueDao.updateStatus(queueItemId, "COMPLETED")
            }

            val downloadEntity = DownloadEntity(
                url = url,
                title = title,
                platform = platform,
                filePath = finalUri?.toString() ?: "",
                thumbnailPath = mediaInfo.thumbnailUrl,
                fileSize = finalFileSize,
                downloadedAt = System.currentTimeMillis(),
                format = selectedFormat?.resolution ?: formatId,
                type = if (isAudioOnly) "AUDIO" else "VIDEO",
                status = "COMPLETED",
                duration = mediaInfo.duration.coerceAtLeast(30L),
                author = mediaInfo.uploader,
                videoResolution = selectedFormat?.resolution,
                resolvedByInstance = mediaInfo.resolvedByInstance
            )
            downloadDao.insert(downloadEntity)

            Timber.tag(TAG).d("Paso 8: Registro persistido en base de datos Room. ¡Descarga completada!")
            Timber.tag(TAG).d("==================================================")

            showCompletedNotification(notificationId, title, finalUri?.toString() ?: "")
            Result.success()

        } catch (e: Exception) {
            val errorMsg = e.message ?: "Error desconocido durante la descarga"
            Timber.tag(TAG).e(e, "Error crítico en DownloadWorker para $url: $errorMsg")

            if (queueItemId > 0) {
                queueDao.updateError(queueItemId, errorMsg)
            }

            showFailedNotification(notificationId, title, errorMsg)

            if (runAttemptCount < 3) {
                Timber.tag(TAG).d("Reintentando Worker (intento ${runAttemptCount + 1}/3)...")
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun isWifiConnected(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Descargas MediaVault",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progreso de descargas en curso"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(
        title: String,
        content: String,
        progress: Int
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    private fun createForegroundInfo(
        notificationId: Int,
        content: String,
        progress: Int
    ): ForegroundInfo {
        val notification = createNotification("MediaVault", content, progress).build()
        return ForegroundInfo(notificationId, notification)
    }

    private fun showCompletedNotification(notificationId: Int, title: String, fileUri: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            if (fileUri.isNotBlank()) {
                putExtra(Intent.EXTRA_STREAM, Uri.parse(fileUri))
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val sharePendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 1,
            Intent.createChooser(shareIntent, "Compartir descarga"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Descarga completada")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_share, "Compartir", sharePendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun showFailedNotification(notificationId: Int, title: String, error: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Descarga fallida")
            .setContentText("$title: $error")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private suspend fun downloadHlsStream(
        m3u8Url: String,
        targetFile: File,
        cookieHeader: String?,
        refererUrl: String,
        title: String,
        notificationId: Int,
        queueItemId: Long
    ) {
        Timber.tag(TAG).d("Iniciando descarga de stream HLS (.m3u8): $m3u8Url")
        val req = Request.Builder()
            .url(m3u8Url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            .header("Referer", refererUrl)
            .apply { if (!cookieHeader.isNullOrBlank()) header("Cookie", cookieHeader) }
            .build()

        val playlistContent = httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Error al obtener playlist HLS (HTTP ${resp.code})")
            resp.body?.string() ?: throw Exception("Playlist HLS vacía")
        }

        var mediaPlaylistUrl = m3u8Url
        var mediaPlaylistContent = playlistContent

        // Si es master playlist, buscar el stream con mayor resolución o variante
        if (playlistContent.contains("#EXT-X-STREAM-INF")) {
            val lines = playlistContent.lines()
            var bestVariantUrl: String? = null
            for (i in lines.indices) {
                if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                    for (j in (i + 1) until lines.size) {
                        val candidateLine = lines[j].trim()
                        if (candidateLine.isNotEmpty() && !candidateLine.startsWith("#")) {
                            bestVariantUrl = candidateLine
                            break
                        }
                    }
                }
            }

            if (!bestVariantUrl.isNullOrBlank()) {
                mediaPlaylistUrl = resolveUrl(m3u8Url, bestVariantUrl)
                Timber.tag(TAG).d("Variante HLS de mayor calidad seleccionada: $mediaPlaylistUrl")
                val variantReq = Request.Builder()
                    .url(mediaPlaylistUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                    .header("Referer", refererUrl)
                    .apply { if (!cookieHeader.isNullOrBlank()) header("Cookie", cookieHeader) }
                    .build()

                mediaPlaylistContent = httpClient.newCall(variantReq).execute().use { resp ->
                    resp.body?.string() ?: mediaPlaylistContent
                }
            }
        }

        // Extraer segmentos TS
        val segmentUrls = mutableListOf<String>()
        for (line in mediaPlaylistContent.lines()) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                segmentUrls.add(resolveUrl(mediaPlaylistUrl, trimmed))
            }
        }

        if (segmentUrls.isEmpty()) {
            throw Exception("No se encontraron segmentos .ts en la playlist HLS.")
        }

        Timber.tag(TAG).d("Total de segmentos HLS a descargar: ${segmentUrls.size}")
        val outputStream = FileOutputStream(targetFile)
        val buffer = ByteArray(32 * 1024)
        var lastUpdate = System.currentTimeMillis()

        for ((index, segUrl) in segmentUrls.withIndex()) {
            val segReq = Request.Builder()
                .url(segUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Referer", mediaPlaylistUrl)
                .apply { if (!cookieHeader.isNullOrBlank()) header("Cookie", cookieHeader) }
                .build()

            httpClient.newCall(segReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val stream = resp.body?.byteStream()
                    if (stream != null) {
                        var read: Int
                        while (stream.read(buffer).also { read = it } != -1) {
                            outputStream.write(buffer, 0, read)
                        }
                        stream.close()
                    }
                }
            }

            val progressInt = (((index + 1).toFloat() / segmentUrls.size) * 100f).toInt().coerceIn(0, 99)
            val now = System.currentTimeMillis()
            if (now - lastUpdate > 600 || index == segmentUrls.lastIndex) {
                Timber.tag(TAG).d("Descarga HLS: $progressInt% (${index + 1}/${segmentUrls.size} segmentos)")
                notificationManager.notify(
                    notificationId,
                    createNotification("Descargando $title...", "$progressInt% (HLS)", progressInt).build()
                )
                if (queueItemId > 0) {
                    queueDao.updateProgress(queueItemId, progressInt, 0L, 0L)
                }
                lastUpdate = now
            }
        }

        outputStream.flush()
        outputStream.close()
        Timber.tag(TAG).d("Stream HLS descargado y concatenado exitosamente (${targetFile.length()} bytes)")
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        return try {
            if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
                relativeUrl
            } else {
                val base = java.net.URI(baseUrl)
                base.resolve(relativeUrl).toString()
            }
        } catch (e: Exception) {
            relativeUrl
        }
    }
}
