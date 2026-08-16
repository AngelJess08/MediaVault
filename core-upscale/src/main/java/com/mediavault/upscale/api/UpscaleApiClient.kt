package com.mediavault.upscale.api

import com.mediavault.upscale.model.UpscaleJobStatus
import com.mediavault.upscale.model.UpscaleRequest
import kotlinx.coroutines.flow.Flow

interface UpscaleApiClient {
    suspend fun submitJob(request: UpscaleRequest): Result<String> // retorna jobId
    suspend fun getJobStatus(jobId: String): Result<UpscaleJobStatus>
    suspend fun cancelJob(jobId: String): Result<Unit>
    fun observeJobStatus(jobId: String, pollIntervalMs: Long = 5000): Flow<UpscaleJobStatus>
}
