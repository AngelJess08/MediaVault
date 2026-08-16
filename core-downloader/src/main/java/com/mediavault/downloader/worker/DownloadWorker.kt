package com.mediavault.downloader.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.mediavault.downloader.extractor.UniversalMediaExtractor
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
    @Assisted private val params: WorkerParameters,
    private val universalMediaExtractor: UniversalMediaExtractor,
    private val queueDao: QueueDao,
    private val downloadDao: DownloadDao,
    private val cookieDao: CookieDao,
    private val mediaStoreHelper: MediaStoreHelper
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_QUEUE_ITEM_ID = "QUEUE_ITEM_ID"
        const val KEY_URL = "URL"
        const val KEY_TITLE = "TITLE"
        const val KEY_PLATFORM = "PLATFORM"
        const val KEY_FORMAT_ID = "FORMAT_ID"
        const val KEY_OUTPUT_PATH = "OUTPUT_PATH"
        const val KEY_AUDIO_ONLY = "AUDIO_ONLY"
        const val KEY_AUDIO_FORMAT = "AUDIO_FORMAT"
        const val KEY_AUDIO_BITRATE = "AUDIO_BITRATE"
        const val KEY_TRIM_START = "TRIM_START"
        const val KEY_TRIM_END = "TRIM_END"
        const val KEY_BURN_SUBTITLES = "BURN_SUBTITLES"
        const val KEY_SUBTITLE_LANG = "SUBTITLE_LANG"
        const val KEY_EMBED_METADATA = "EMBED_METADATA"
        const val KEY_EMBED_THUMBNAIL = "EMBED_THUMBNAIL"
        const val KEY_WIFI_ONLY = "WIFI_ONLY"
        const val KEY_SPEED_LIMIT_KBPS = "SPEED_LIMIT_KBPS"

        private const val CHANNEL_ID = "DOWNLOAD_CHANNEL"
        private const val NOTIFICATION_ID_BASE = 1000
        private const val TAG = "MediaVaultDownload"
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

        Timber.tag(TAG).d("==================================================")
        Timber.tag(TAG).d("Paso 1: Iniciando Worker para id=$queueItemId, url=$url")
        Timber.tag(TAG).d("Paso 1: Formato=$formatId, isAudioOnly=$isAudioOnly, isWifiOnly=$isWifiOnly")

        try {
            createNotificationChannel()

            if (isWifiOnly && !isWifiConnected()) {
                Timber.tag(TAG).w("Descarga pausada: Requiere Wi-Fi y el dispositivo está en red móvil")
                if (queueItemId > 0) {
                    queueDao.updateStatus(queueItemId, "PAUSADO_WIFI")
                }
                return@withContext Result.retry()
            }

            if (queueItemId > 0) {
                queueDao.updateStatus(queueItemId, "DOWNLOADING")
            }
            setForeground(createForegroundInfo(notificationId, "Iniciando descarga: $title", 0))

            // Paso 2: Obtener cookies si existen para la plataforma / dominio
            val cookieEntity = cookieDao.getByPlatform(platform)
            val cookieHeader = cookieEntity?.cookieString
            if (!cookieHeader.isNullOrBlank()) {
                Timber.tag(TAG).d("Paso 2: Inyectando cookies guardadas para plataforma $platform")
            } else {
                Timber.tag(TAG).d("Paso 2: Sin cookies registradas para $platform, continuando con request estándar")
            }

            // Paso 3: Extraer URL de stream real
            Timber.tag(TAG).d("Paso 3: Extrayendo información y stream con UniversalMediaExtractor...")
            val mediaInfo = universalMediaExtractor.extract(url, cookieHeader)
            val selectedFormat = mediaInfo.formats.find { it.formatId == formatId }
                ?: mediaInfo.formats.firstOrNull { if (isAudioOnly) it.isAudioOnly else !it.isAudioOnly }
                ?: mediaInfo.formats.firstOrNull()

            val streamUrl = selectedFormat?.streamUrl ?: url
            Timber.tag(TAG).d("Paso 4: Stream seleccionado: ${selectedFormat?.resolution ?: formatId} -> $streamUrl")

            // Paso 5: Conectar y descargar bytes vía OkHttp
            val requestBuilder = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .header("Referer", url)

            if (!cookieHeader.isNullOrBlank()) {
                requestBuilder.header("Cookie", cookieHeader)
            }

            val targetFile = File(outputPath)
            targetFile.parentFile?.mkdirs()

            Timber.tag(TAG).d("Paso 5: Conectando vía HTTP streaming a $streamUrl...")
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    Timber.tag(TAG).e("Error HTTP $code al descargar stream")
                    if (code == 401 || code == 403) {
                        throw Exception("Login requerido o contenido privado (HTTP $code)")
                    }
                    throw Exception("Servidor respondió con código HTTP $code")
                }

                val body = response.body ?: throw Exception("Cuerpo de respuesta vacío")
                val contentLength = body.contentLength()
                val totalBytes = if (contentLength > 0) contentLength else (selectedFormat?.filesizeApprox ?: (25 * 1024 * 1024L))
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

                        Timber.tag(TAG).d("Progreso: $progressInt% | $speedText | ETA: ${etaSec}s | Descargados: ${totalDownloaded / (1024 * 1024)}MB")

                        notificationManager.notify(
                            notificationId,
                            createNotification("Descargando $title...", "$progressInt% - $speedText", progressInt).build()
                        )

                        // Enviar broadcast a Widget de escritorio
                        try {
                            context.sendBroadcast(Intent("com.mediavault.app.ACTION_UPDATE_WIDGET_PROGRESS").apply {
                                putExtra("EXTRA_STATUS", "$title ($progressInt% - $speedText)")
                                putExtra("EXTRA_PROGRESS", progressInt)
                            })
                        } catch (e: Exception) {
                            // Ignorar si el widget no está activo
                        }

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
                Timber.tag(TAG).d("Paso 6: Descarga de bytes finalizada con éxito (${targetFile.length()} bytes)")
            }

            // Actualizar widget al completar
            try {
                context.sendBroadcast(Intent("com.mediavault.app.ACTION_UPDATE_WIDGET_PROGRESS").apply {
                    putExtra("EXTRA_STATUS", "Descarga completada: $title")
                    putExtra("EXTRA_PROGRESS", 100)
                })
            } catch (e: Exception) {}

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
                videoResolution = selectedFormat?.resolution
            )
            downloadDao.insert(downloadEntity)

            Timber.tag(TAG).d("Paso 8: Registro persistido en base de datos Room. ¡Descarga completada!")
            Timber.tag(TAG).d("==================================================")

            showCompletedNotification(notificationId, title, finalUri?.toString() ?: "")
            Result.success()

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "ERROR en el flujo de descarga para $url: ${e.message}")
            val errorMessage = e.message ?: "Error de red desconocido"

            val tempFile = File(outputPath)
            if (tempFile.exists()) {
                tempFile.delete()
            }

            if (queueItemId > 0) {
                queueDao.updateStatus(queueItemId, "ERROR: $errorMessage")
            }

            if (runAttemptCount < 2 && !errorMessage.contains("Login requerido")) {
                Timber.tag(TAG).w("Reintentando descarga (intento ${runAttemptCount + 1})...")
                Result.retry()
            } else {
                notificationManager.notify(
                    notificationId,
                    createNotification("Error al descargar $title", errorMessage, 0).build()
                )
                Result.failure()
            }
        }
    }

    private fun isWifiConnected(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun createForegroundInfo(id: Int, text: String, progress: Int): ForegroundInfo {
        val notification = createNotification("Descargando...", text, progress).build()
        return ForegroundInfo(id, notification)
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
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
    }

    private fun showCompletedNotification(id: Int, fileName: String, uriString: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse(uriString)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Acción de compartir directo
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(uriString))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val sharePendingIntent = PendingIntent.getActivity(
            context,
            id + 5000,
            Intent.createChooser(shareIntent, "Compartir descarga"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Descarga completa")
            .setContentText(fileName)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "Abrir", pendingIntent)
            .addAction(android.R.drawable.ic_menu_share, "Compartir", sharePendingIntent)
            .build()

        notificationManager.notify(id, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Descargas MediaVault",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progreso de las descargas activas"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
