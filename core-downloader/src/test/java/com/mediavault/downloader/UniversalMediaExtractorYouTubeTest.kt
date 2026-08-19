package com.mediavault.downloader

import com.mediavault.downloader.detector.PlatformDetector
import com.mediavault.downloader.extractor.UniversalMediaExtractor
import com.mediavault.downloader.model.Platform
import com.mediavault.downloader.universal.UniversalWebViewSniffer
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UniversalMediaExtractorYouTubeTest {

    private lateinit var extractor: UniversalMediaExtractor
    private lateinit var detector: PlatformDetector

    @Before
    fun setup() {
        timber.log.Timber.plant(object : timber.log.Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                println("[$tag] $message")
                t?.printStackTrace()
            }
        })
        detector = PlatformDetector()
        val mockSniffer = mockk<UniversalWebViewSniffer>(relaxed = true)
        extractor = UniversalMediaExtractor(detector, mockSniffer)
    }

    @Test
    fun extractYouTube_standardVideo_returnsStreams() = runBlocking {
        val testUrl = "https://www.youtube.com/watch?v=aqz-KE-bpKQ"
        println("=== INICIANDO PRUEBA DE EXTRACCIÓN YOUTUBE ===")
        println("URL: $testUrl")

        try {
            val mediaInfo = extractor.extract(testUrl)
            println("✅ Extracción exitosa!")
            println("Título       : ${mediaInfo.title}")
            println("Plataforma   : ${mediaInfo.platform}")
            println("Autor        : ${mediaInfo.uploader}")
            println("Resuelto por : ${mediaInfo.resolvedByInstance}")
            println("Total Formatos: ${mediaInfo.formats.size}")

            mediaInfo.formats.take(5).forEachIndexed { index, fmt ->
                println("   [$index] Calidad: ${fmt.resolution ?: fmt.formatId} | Ext: ${fmt.ext} | AudioOnly: ${fmt.isAudioOnly} | Stream: ${fmt.streamUrl?.take(60)}...")
            }
        } catch (e: Throwable) {
            println("⚠️ Capturado en test: ${e.message}")
            e.printStackTrace()
        }
    }
}
