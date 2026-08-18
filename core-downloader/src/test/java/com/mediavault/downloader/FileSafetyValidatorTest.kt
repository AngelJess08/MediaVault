package com.mediavault.downloader

import com.mediavault.downloader.security.FileSafetyValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class FileSafetyValidatorTest {

    private lateinit var validator: FileSafetyValidator

    @Before
    fun setup() {
        validator = FileSafetyValidator()
    }

    @Test
    fun isForbiddenExtension_identifiesExecutables() {
        assertTrue(validator.isForbiddenExtension("apk"))
        assertTrue(validator.isForbiddenExtension(".exe"))
        assertTrue(validator.isForbiddenExtension("sh"))
        assertTrue(validator.isForbiddenExtension("bat"))
        assertTrue(validator.isForbiddenExtension("dex"))
        assertTrue(validator.isForbiddenExtension("jar"))
        assertTrue(validator.isForbiddenExtension("msi"))

        assertFalse(validator.isForbiddenExtension("mp4"))
        assertFalse(validator.isForbiddenExtension("mp3"))
        assertFalse(validator.isForbiddenExtension("m3u8"))
        assertFalse(validator.isForbiddenExtension("webm"))
        assertFalse(validator.isForbiddenExtension("m4a"))
    }

    @Test
    fun isForbiddenUrlOrFilename_detectsMaliciousDownloads() {
        assertTrue(validator.isForbiddenUrlOrFilename("https://malicious.com/payload.apk"))
        assertTrue(validator.isForbiddenUrlOrFilename("https://malicious.com/installer.exe?token=123"))
        assertTrue(validator.isForbiddenUrlOrFilename("https://malicious.com/script.sh#run"))

        assertFalse(validator.isForbiddenUrlOrFilename("https://cdn.example.com/video.mp4"))
        assertFalse(validator.isForbiddenUrlOrFilename("https://cdn.example.com/master.m3u8"))
    }

    @Test
    fun validateStreamMagicBytes_mp4Verification() {
        // MP4 magic bytes: 4 bytes length + 'ftyp' (0x66, 0x74, 0x79, 0x70)
        val mp4Header = byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x6D, 0x70, 0x34, 0x32)
        val stream = ByteArrayInputStream(mp4Header)

        assertTrue(validator.validateStreamMagicBytes(stream, "video.mp4"))
    }

    @Test
    fun validateStreamMagicBytes_mkvWebmVerification() {
        // EBML header: 0x1A 0x45 0xDF 0xA3
        val webmHeader = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x93.toByte(), 0x42, 0x82.toByte())
        val stream = ByteArrayInputStream(webmHeader)

        assertTrue(validator.validateStreamMagicBytes(stream, "video.webm"))
    }

    @Test
    fun validateStreamMagicBytes_mp3Id3Verification() {
        // ID3 header: 'ID3' = 0x49 0x44 0x33
        val mp3Header = byteArrayOf(0x49, 0x44, 0x33, 0x03, 0x00, 0x00)
        val stream = ByteArrayInputStream(mp3Header)

        assertTrue(validator.validateStreamMagicBytes(stream, "song.mp3"))
    }

    @Test
    fun validateStreamMagicBytes_rejectsInvalidBinary() {
        // Random executable / DOS MZ header: 'MZ' = 0x4D 0x5A
        val dosHeader = byteArrayOf(0x4D, 0x5A, 0x90.toByte(), 0x00, 0x03, 0x00)
        val stream = ByteArrayInputStream(dosHeader)

        assertFalse(validator.validateStreamMagicBytes(stream, "malware.exe"))
    }
}
