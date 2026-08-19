package com.mediavault.app

import android.content.Context
import com.mediavault.app.ui.home.HomeViewModel
import com.mediavault.downloader.detector.PlatformDetector
import com.mediavault.downloader.model.FormatOption
import com.mediavault.downloader.model.MediaInfo
import com.mediavault.downloader.model.Platform
import com.mediavault.downloader.repository.DownloadRepository
import com.mediavault.storage.datastore.SettingsDataStore
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelCancellationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context = mockk<Context>(relaxed = true)
    private val downloadRepository = mockk<DownloadRepository>(relaxed = true)
    private val platformDetector = mockk<PlatformDetector>(relaxed = true)
    private val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        coEvery { platformDetector.detect(any()) } returns Platform.YOUTUBE
        coEvery { downloadRepository.checkDuplicate(any()) } returns null

        viewModel = HomeViewModel(
            context = context,
            downloadRepository = downloadRepository,
            platformDetector = platformDetector,
            settingsDataStore = settingsDataStore
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun analyzeUrl_rapidSequentialCalls_cancelsPreviousCleanlyWithoutError() = runTest(testDispatcher) {
        val url1 = "https://youtube.com/shorts/first123"
        val url2 = "https://youtube.com/shorts/second456"

        val mediaInfo2 = MediaInfo(
            url = url2,
            title = "Final Shorts Video",
            platform = Platform.YOUTUBE,
            formats = listOf(
                FormatOption(
                    formatId = "yt_1080p",
                    ext = "mp4",
                    resolution = "1080p",
                    isNative = true,
                    streamUrl = "https://stream.example.com/video.mp4"
                )
            )
        )

        // El primer llamado demora 500ms
        coEvery { downloadRepository.fetchMediaInfo(url1, any()) } coAnswers {
            delay(500)
            throw RuntimeException("Should have been cancelled")
        }

        // El segundo llamado retorna exitosamente
        coEvery { downloadRepository.fetchMediaInfo(url2, any()) } coAnswers {
            delay(100)
            mediaInfo2
        }

        println("=== INICIANDO PRUEBA DE CANCELACIÓN DE CORRUTINAS ===")
        println("Disparando primer análisis (url1)...")
        viewModel.analyzeUrl(url1)

        println("Disparando segundo análisis inmediatamente (url2, cancela el primero)...")
        viewModel.analyzeUrl(url2)

        advanceUntilIdle()

        val finalState = viewModel.uiState.value
        println("Estado final después de ejecutar ambos análisis:")
        println("   - isLoading: ${finalState.isLoading}")
        println("   - errorMessage: ${finalState.errorMessage}")
        println("   - mediaInfo: ${finalState.mediaInfo?.title}")
        println("   - selectedResolution: ${finalState.selectedResolution}")

        // Verificar que NO hay error de cancelación ("StandaloneCoroutine was cancelled")
        assertNull("El errorMessage debe ser nulo y no mostrar error de cancelación", finalState.errorMessage)
        assertNotEquals("StandaloneCoroutine was cancelled", finalState.errorMessage)

        // Verificar que el último análisis es el que prevaleció
        assertNotNull(finalState.mediaInfo)
        assertEquals("Final Shorts Video", finalState.mediaInfo?.title)
        assertEquals("1080p", finalState.selectedResolution)
        println("✅ Prueba exitosa: Cancelación limpia sin error visible para el usuario.")
    }
}
