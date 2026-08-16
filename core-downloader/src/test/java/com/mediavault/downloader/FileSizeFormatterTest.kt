package com.mediavault.downloader

import com.mediavault.downloader.util.FileSizeFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class FileSizeFormatterTest {

    @Test
    fun `formatBytes returns correct formatting`() {
        assertEquals("0 B", FileSizeFormatter.formatBytes(0))
        assertEquals("500.0 B", FileSizeFormatter.formatBytes(500))
        assertEquals("1.0 KB", FileSizeFormatter.formatBytes(1024))
        assertEquals("1.5 MB", FileSizeFormatter.formatBytes(1572864))
        assertEquals("1.0 GB", FileSizeFormatter.formatBytes(1073741824))
    }

    @Test
    fun `formatSpeed returns correct formatting`() {
        assertEquals("0 B/s", FileSizeFormatter.formatSpeed(0f))
        assertEquals("1.0 KB/s", FileSizeFormatter.formatSpeed(1024f))
        assertEquals("2.5 MB/s", FileSizeFormatter.formatSpeed(2621440f))
    }

    @Test
    fun `formatEta returns correct formatting`() {
        assertEquals("0s", FileSizeFormatter.formatEta(0))
        assertEquals("30s", FileSizeFormatter.formatEta(30))
        assertEquals("2m 30s", FileSizeFormatter.formatEta(150))
        assertEquals("1h 0m 0s", FileSizeFormatter.formatEta(3600))
        assertEquals("1h 5m 10s", FileSizeFormatter.formatEta(3910))
    }

    @Test
    fun `formatDuration returns correct formatting`() {
        assertEquals("00:00", FileSizeFormatter.formatDuration(0))
        assertEquals("00:30", FileSizeFormatter.formatDuration(30))
        assertEquals("02:30", FileSizeFormatter.formatDuration(150))
        assertEquals("01:00:00", FileSizeFormatter.formatDuration(3600))
        assertEquals("01:05:10", FileSizeFormatter.formatDuration(3910))
    }
}
