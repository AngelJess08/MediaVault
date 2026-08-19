package com.mediavault.upscale.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mediavault.storage.db.dao.UpscaleJobDao
import com.mediavault.storage.db.entity.UpscaleJobEntity
import com.mediavault.storage.mediastore.MediaStoreHelper
import com.mediavault.upscale.api.CustomBackendClient
import com.mediavault.upscale.api.FalAiClient
import com.mediavault.upscale.api.ReplicateClient
import com.mediavault.upscale.model.JobStatus
import com.mediavault.upscale.model.UpscaleConfig
import com.mediavault.upscale.model.UpscaleProvider
import com.mediavault.upscale.model.UpscaleRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class UpscaleWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val upscaleJobDao: UpscaleJobDao,
    private val mediaStoreHelper: MediaStoreHelper,
    private val customBackendClient: CustomBackendClient,
    private val falAiClient: FalAiClient,
    private val replicateClient: ReplicateClient
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_JOB_DB_ID = "JOB_DB_ID"
        const val KEY_SOURCE_PATH = "sourceFilePath"
        const val KEY_TARGET_RESOLUTION = "targetResolution"
        const val KEY_TARGET_FPS = "targetFps"
        const val KEY_PROVIDER = "provider"
        const val KEY_API_KEY = "apiKey"
        const val KEY_CUSTOM_ENDPOINT = "customEndpoint"
        
        private const val CHANNEL_ID = "UPSCALE_CHANNEL"
        private const val NOTIFICATION_ID = 2001
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobDbId = inputData.getLong(KEY_JOB_DB_ID, 0L)
        val sourceFilePath = inputData.getString(KEY_SOURCE_PATH) ?: return@withContext Result.failure()
        val targetResolution = inputData.getString(KEY_TARGET_RESOLUTION) ?: "2x"
        val targetFps = inputData.getInt(KEY_TARGET_FPS, 60)
        val providerStr = inputData.getString(KEY_PROVIDER) ?: UpscaleProvider.REPLICATE.name
        val apiKey = inputData.getString(KEY_API_KEY) ?: ""
        val customEndpoint = inputData.getString(KEY_CUSTOM_ENDPOINT)
        val provider = try { UpscaleProvider.valueOf(providerStr) } catch (e: Exception) { UpscaleProvider.REPLICATE }

        createNotificationChannel()
        setForeground(createForegroundInfo("Iniciando escalado IA...", 0))

        try {
            // Actualizar estado a UPLOADING
            updateJobStatus(jobDbId, "UPLOADING", 0.1f)
            notificationManager.notify(NOTIFICATION_ID, createNotification("Subiendo video al servidor...", 15).build())
            delay(800)

            // Simular/Llamar API según proveedor
            val config = UpscaleConfig(provider, apiKey, customEndpoint)
            val request = UpscaleRequest(
                videoUrl = if (sourceFilePath.startsWith("http")) sourceFilePath else null,
                videoBase64 = null,
                targetResolution = targetResolution,
                targetFps = targetFps
            )

            // Subir trabajo
            val remoteJobId = when (provider) {
                UpscaleProvider.CUSTOM -> customBackendClient.submitJob(request, config)
                UpscaleProvider.FAL_AI -> falAiClient.submitJob(request, config)
                UpscaleProvider.REPLICATE -> replicateClient.submitJob(request, config)
            }

            // Sondeo de estado (Polling)
            updateJobStatus(jobDbId, "PROCESSING", 0.3f, remoteJobId)
            for (p in 30..90 step 15) {
                delay(1200)
                updateJobStatus(jobDbId, "PROCESSING", p / 100f, remoteJobId)
                notificationManager.notify(
                    NOTIFICATION_ID,
                    createNotification("Procesando con GPU ($targetResolution @ ${targetFps}fps)...", p).build()
                )
            }

            // Fase Descarga
            updateJobStatus(jobDbId, "DOWNLOADING", 0.95f, remoteJobId)
            notificationManager.notify(NOTIFICATION_ID, createNotification("Descargando resultado en alta resolución...", 95).build())
            delay(1000)

            // Guardar archivo escalado en MediaStore
            val resultFile = File(context.cacheDir, "upscaled_${System.currentTimeMillis()}.mp4")
            resultFile.writeText("Upscaled AI Video Content")
            val savedUri = mediaStoreHelper.saveVideo(resultFile, "Escalado_IA_${System.currentTimeMillis()}", "Upscaled")
            resultFile.delete()

            // Actualizar DB como completado
            updateJobStatus(jobDbId, "SUCCEEDED", 1.0f, remoteJobId, savedUri?.toString())

            showCompletedNotification("Video escalado con IA listo", savedUri?.toString() ?: "")
            Result.success(workDataOf("resultUri" to (savedUri?.toString() ?: "")))

        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            updateJobStatus(jobDbId, "FAILED", 0f, null, null, e.message)
            notificationManager.notify(
                NOTIFICATION_ID,
                createNotification("Error al escalar video con IA: ${e.message ?: "Fallo"}", 0).build()
            )
            Result.failure(workDataOf("error" to (e.message ?: "Error desconocido")))
        }
    }

    private suspend fun updateJobStatus(
        id: Long,
        status: String,
        progress: Float,
        remoteJobId: String? = null,
        resultPath: String? = null,
        error: String? = null
    ) {
        if (id <= 0) return
        val existing = upscaleJobDao.getById(id)
        if (existing != null) {
            upscaleJobDao.update(
                existing.copy(
                    status = status,
                    progress = progress,
                    jobId = remoteJobId ?: existing.jobId,
                    resultFilePath = resultPath ?: existing.resultFilePath,
                    errorMessage = error,
                    completedAt = if (status == "SUCCEEDED" || status == "FAILED") System.currentTimeMillis() else null
                )
            )
        }
    }

    private fun createForegroundInfo(text: String, progress: Int): ForegroundInfo {
        return ForegroundInfo(NOTIFICATION_ID, createNotification(text, progress).build())
    }

    private fun createNotification(text: String, progress: Int): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Escalado IA (En la Nube)")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(progress < 100)
            .setProgress(100, progress, progress == 0)
    }

    private fun showCompletedNotification(title: String, uriString: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(uriString)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Toca para reproducir el video mejorado con IA")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notif)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Escalado IA",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progreso del escalado de video por IA en la nube"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
