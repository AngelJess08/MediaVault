package com.mediavault.downloader

import com.mediavault.downloader.util.UrlValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {

    @Test
    fun `isValidUrl identifies valid supported urls`() {
        assertTrue(UrlValidator.isValidUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(UrlValidator.isValidUrl("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(UrlValidator.isValidUrl("https://twitter.com/user/status/123456789"))
        assertTrue(UrlValidator.isValidUrl("https://x.com/user/status/123456789"))
        assertTrue(UrlValidator.isValidUrl("https://www.instagram.com/p/C123456/"))
        assertTrue(UrlValidator.isValidUrl("https://www.tiktok.com/@user/video/1234567890"))
    }

    @Test
    fun `isValidUrl rejects invalid or unsupported urls`() {
        assertFalse(UrlValidator.isValidUrl("not a url"))
        assertFalse(UrlValidator.isValidUrl("https://unsupported-site.com/video"))
        assertFalse(UrlValidator.isValidUrl(""))
    }

    @Test
    fun `extractUrlFromText finds url in text`() {
        val text = "Check out this video! https://www.youtube.com/watch?v=dQw4w9WgXcQ It's great"
        val extracted = UrlValidator.extractUrlFromText(text)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", extracted)
    }

    @Test
    fun `sanitizeUrl removes tracking parameters`() {
        val original = "https://youtu.be/dQw4w9WgXcQ?si=abcdefg12345"
        val sanitized = UrlValidator.sanitizeUrl(original)
        assertEquals("https://youtu.be/dQw4w9WgXcQ", sanitized)
        
        val multipleParams = "https://www.instagram.com/p/C123/?igshid=xyz&utm_source=share"
        val sanitizedMultiple = UrlValidator.sanitizeUrl(multipleParams)
        assertEquals("https://www.instagram.com/p/C123/", sanitizedMultiple)
        
        // Ensure it keeps important parameters
        val keepImportant = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=123"
        val sanitizedImportant = UrlValidator.sanitizeUrl(keepImportant)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", sanitizedImportant)
    }
}
