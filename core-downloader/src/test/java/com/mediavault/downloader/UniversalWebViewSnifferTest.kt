package com.mediavault.downloader

import com.mediavault.downloader.universal.BlobMseUnsupportedException
import com.mediavault.downloader.universal.DrmProtectedException
import com.mediavault.downloader.universal.NoMediaFoundException
import com.mediavault.downloader.universal.SnifferCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalWebViewSnifferTest {

    @Test
    fun snifferCandidate_hlsDetection() {
        val candidate = SnifferCandidate(
            url = "https://example.com/live/master.m3u8",
            mimeType = "application/vnd.apple.mpegurl",
            extension = "m3u8",
            isHls = true,
            estimatedResolution = "Auto (Stream HLS Adaptativo)"
        )

        assertTrue(candidate.isHls)
        assertEquals("m3u8", candidate.extension)
        assertEquals("Auto (Stream HLS Adaptativo)", candidate.estimatedResolution)
    }

    @Test
    fun snifferCandidate_mp4VideoDetection() {
        val candidate = SnifferCandidate(
            url = "https://cdn.example.com/video_1080p.mp4",
            mimeType = "video/mp4",
            extension = "mp4",
            isHls = false,
            isAudioOnly = false,
            contentLength = 25 * 1024 * 1024L,
            estimatedResolution = "1080p FHD"
        )

        assertEquals("mp4", candidate.extension)
        assertEquals("1080p FHD", candidate.estimatedResolution)
        assertEquals(26214400L, candidate.contentLength)
    }

    @Test
    fun snifferExceptions_messages() {
        val drmEx = DrmProtectedException("Este contenido está protegido por cifrado digital (DRM Widevine/PlayReady) y no se puede descargar.")
        assertTrue(drmEx.message!!.contains("DRM"))

        val mseEx = BlobMseUnsupportedException("Este sitio usa un reproductor (Media Source / blob:) que fragmenta el video en memoria vía JavaScript y no se puede extraer directamente.")
        assertTrue(mseEx.message!!.contains("blob:"))

        val noMediaEx = NoMediaFoundException("No se pudo encontrar un video reproducible en esta página.")
        assertTrue(noMediaEx.message!!.contains("No se pudo encontrar"))
    }
}
