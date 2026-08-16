package com.mediavault.upscale.api

import com.mediavault.upscale.model.JobStatus
import com.mediavault.upscale.model.UpscaleConfig
import com.mediavault.upscale.model.UpscaleJobStatus
import com.mediavault.upscale.model.UpscaleRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomBackendClient @Inject constructor() : UpscaleApiClient {

    override suspend fun submitJob(request: UpscaleRequest): Result<String> {
        return Result.success("custom_job_${System.currentTimeMillis()}")
    }

    suspend fun submitJob(request: UpscaleRequest, config: UpscaleConfig): String {
        return "custom_job_${System.currentTimeMillis()}"
    }

    override suspend fun getJobStatus(jobId: String): Result<UpscaleJobStatus> {
        return Result.success(
            UpscaleJobStatus(
                jobId = jobId,
                status = JobStatus.PROCESSING,
                progress = 0.7f,
                estimatedSeconds = 30,
                estimatedCost = 0.0,
                resultUrl = null,
                errorMessage = null
            )
        )
    }

    override suspend fun cancelJob(jobId: String): Result<Unit> {
        return Result.success(Unit)
    }

    override fun observeJobStatus(jobId: String, pollIntervalMs: Long): Flow<UpscaleJobStatus> = flow {
        while (true) {
            val result = getJobStatus(jobId)
            if (result.isSuccess) {
                val status = result.getOrNull()!!
                emit(status)
                if (status.status in listOf(JobStatus.SUCCEEDED, JobStatus.FAILED, JobStatus.CANCELLED)) break
            } else {
                emit(UpscaleJobStatus(jobId, JobStatus.FAILED, 0f, null, null, null, result.exceptionOrNull()?.message))
                break
            }
            delay(pollIntervalMs)
        }
    }
}
