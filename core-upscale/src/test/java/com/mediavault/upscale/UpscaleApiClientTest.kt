package com.mediavault.upscale

import com.mediavault.upscale.api.ReplicateClient
import com.mediavault.upscale.model.UpscaleRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class UpscaleApiClientTest {

    @Test
    fun testSubmitJob() = runBlocking {
        val client = ReplicateClient()
        val request = UpscaleRequest(
            videoUrl = "http://example.com/video.mp4",
            videoBase64 = null,
            targetResolution = "2x",
            targetFps = 30
        )
        val result = client.submitJob(request)
        assertTrue(result.isSuccess)
    }
}
