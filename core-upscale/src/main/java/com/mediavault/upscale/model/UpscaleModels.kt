package com.mediavault.upscale.model

enum class UpscaleProvider { REPLICATE, FAL_AI, CUSTOM }

data class UpscaleConfig(
    val provider: UpscaleProvider,
    val apiKey: String,
    val customEndpoint: String? = null
)

data class UpscaleRequest(
    val videoUrl: String?,        // URL del video si el backend soporta URL directa
    val videoBase64: String?,     // Base64 si hay que subir el archivo
    val targetResolution: String, // "2x", "4x", "1920x1080", etc.
    val targetFps: Int?,
    val model: String = "real-esrgan"
)

data class UpscaleJobStatus(
    val jobId: String,
    val status: JobStatus,
    val progress: Float,          // 0.0 a 1.0
    val estimatedSeconds: Int?,
    val estimatedCost: Double?,
    val resultUrl: String?,
    val errorMessage: String?
)

enum class JobStatus { QUEUED, PROCESSING, SUCCEEDED, FAILED, CANCELLED }

sealed class UpscaleResult {
    data class Success(val outputUrl: String, val localPath: String?) : UpscaleResult()
    data class Error(val message: String, val code: Int?) : UpscaleResult()
}
