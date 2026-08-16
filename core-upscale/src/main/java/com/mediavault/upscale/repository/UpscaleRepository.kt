package com.mediavault.upscale.repository

import android.content.Context
import androidx.work.*
import com.mediavault.storage.db.dao.UpscaleJobDao
import com.mediavault.storage.db.entity.UpscaleJobEntity
import com.mediavault.upscale.model.UpscaleConfig
import com.mediavault.upscale.model.UpscaleProvider
import com.mediavault.upscale.worker.UpscaleWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpscaleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val upscaleJobDao: UpscaleJobDao
) {
    val jobsFlow: Flow<List<UpscaleJobEntity>> = upscaleJobDao.getAllFlow()

    suspend fun submitUpscaleJob(
        sourceDownloadId: Long,
        sourceFilePath: String,
        targetResolution: String,
        targetFps: Int,
        config: UpscaleConfig
    ): Long {
        val estimatedCost = calculateEstimatedCost(config.provider, targetResolution, targetFps)
        val estimatedTime = calculateEstimatedSeconds(targetResolution, targetFps)

        val jobEntity = UpscaleJobEntity(
            sourceDownloadId = sourceDownloadId,
            status = "QUEUED",
            provider = config.provider.name,
            targetResolution = targetResolution,
            targetFps = targetFps,
            jobId = "",
            resultFilePath = null,
            estimatedCost = estimatedCost,
            estimatedTime = estimatedTime,
            progress = 0f,
            createdAt = System.currentTimeMillis()
        )
        val dbId = upscaleJobDao.insert(jobEntity)

        val inputData = Data.Builder()
            .putLong(UpscaleWorker.KEY_JOB_DB_ID, dbId)
            .putString(UpscaleWorker.KEY_SOURCE_PATH, sourceFilePath)
            .putString(UpscaleWorker.KEY_TARGET_RESOLUTION, targetResolution)
            .putInt(UpscaleWorker.KEY_TARGET_FPS, targetFps)
            .putString(UpscaleWorker.KEY_PROVIDER, config.provider.name)
            .putString(UpscaleWorker.KEY_API_KEY, config.apiKey)
            .putString(UpscaleWorker.KEY_CUSTOM_ENDPOINT, config.customEndpoint)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<UpscaleWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "upscale_$dbId",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        return dbId
    }

    suspend fun cancelJob(dbId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("upscale_$dbId")
        val job = upscaleJobDao.getById(dbId)
        if (job != null) {
            upscaleJobDao.update(job.copy(status = "CANCELLED"))
        }
    }

    fun calculateEstimatedCost(provider: UpscaleProvider, resolution: String, fps: Int): Double {
        return when (provider) {
            UpscaleProvider.CUSTOM -> 0.0
            UpscaleProvider.FAL_AI -> if (resolution.contains("4K") || resolution.contains("4x")) 0.12 else 0.05
            UpscaleProvider.REPLICATE -> if (resolution.contains("4K") || resolution.contains("4x")) 0.18 else 0.08
        }
    }

    fun calculateEstimatedSeconds(resolution: String, fps: Int): Long {
        return when {
            resolution.contains("8K") -> 240L
            resolution.contains("4K") || resolution.contains("4x") -> 120L
            else -> 60L
        }
    }
}
