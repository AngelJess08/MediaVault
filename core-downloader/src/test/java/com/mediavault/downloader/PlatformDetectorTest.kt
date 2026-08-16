package com.mediavault.downloader

import com.mediavault.downloader.detector.PlatformDetector
import com.mediavault.downloader.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlatformDetectorTest {

    private lateinit var detector: PlatformDetector

    @Before
    fun setup() {
        detector = PlatformDetector()
    }

    @Test
    fun detect_youtubeUrls_returnsYoutube() {
        assertEquals(Platform.YOUTUBE, detector.detect("https://www.youtube.com/watch?v=123"))
        assertEquals(Platform.YOUTUBE, detector.detect("https://youtu.be/123"))
        assertEquals(Platform.YOUTUBE, detector.detect("https://youtube.com/shorts/123"))
    }

    @Test
    fun detect_instagramUrls_returnsInstagram() {
        assertEquals(Platform.INSTAGRAM, detector.detect("https://www.instagram.com/p/123/"))
        assertEquals(Platform.INSTAGRAM, detector.detect("https://instagram.com/reel/123/"))
    }

    @Test
    fun detect_invalidUrls_returnsGeneric() {
        assertEquals(Platform.GENERIC, detector.detect("https://www.example.com/video"))
    }

    @Test
    fun isPlaylistUrl_returnsTrueForPlaylists() {
        assertTrue(detector.isPlaylistUrl("https://www.youtube.com/playlist?list=PL123"))
    }

    @Test
    fun normalizeUrl_removesTrackingParams() {
        val normalized = detector.normalizeUrl("https://www.example.com/video?tracking=123")
        assertEquals("https://www.example.com/video", normalized)
    }
}
