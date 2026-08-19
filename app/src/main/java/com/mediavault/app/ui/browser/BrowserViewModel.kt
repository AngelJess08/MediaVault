package com.mediavault.app.ui.browser

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediavault.downloader.detector.PlatformDetector
import com.mediavault.downloader.model.Platform
import com.mediavault.downloader.repository.DownloadRepository
import com.mediavault.downloader.security.AdAndMalwareFilter
import com.mediavault.downloader.security.FileSafetyValidator
import com.mediavault.downloader.universal.SnifferCandidate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class BrowserUiState(
    val currentUrl: String = "",
    val inputUrl: String = "",
    val pageTitle: String = "Navegador Web Seguro",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isSslSecure: Boolean = true,
    val detectedCandidates: List<SnifferCandidate> = emptyList(),
    val showCandidatesSheet: Boolean = false,
    val pendingExternalIntent: String? = null,
    val redirectBombWarning: Boolean = false,
    val downloadSuccessMessage: String? = null
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val platformDetector: PlatformDetector,
    private val adAndMalwareFilter: AdAndMalwareFilter,
    private val fileSafetyValidator: FileSafetyValidator
) : ViewModel() {

    private val TAG = "MediaVaultBrowser"

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    val blockedAdsCount: StateFlow<Int> = adAndMalwareFilter.blockedRequestsCount

    fun onInputUrlChanged(newText: String) {
        _uiState.update { it.copy(inputUrl = newText) }
    }

    fun submitUrlOrQuery(input: String) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return

        val targetUrl = when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://duckduckgo.com/?q=${java.net.URLEncoder.encode(trimmed, "UTF-8")}"
        }

        _uiState.update {
            it.copy(
                currentUrl = targetUrl,
                inputUrl = targetUrl,
                isLoading = true,
                detectedCandidates = emptyList()
            )
        }
    }

    fun onPageStarted(url: String) {
        val isSsl = url.startsWith("https://", ignoreCase = true)
        _uiState.update {
            it.copy(
                currentUrl = url,
                inputUrl = if (url == "about:blank") "" else url,
                isLoading = true,
                progress = 10,
                isSslSecure = isSsl,
                detectedCandidates = emptyList(),
                redirectBombWarning = false
            )
        }
    }

    fun onPageFinished(url: String, title: String?, canBack: Boolean, canForward: Boolean) {
        _uiState.update {
            it.copy(
                currentUrl = url,
                inputUrl = if (url == "about:blank") "" else url,
                pageTitle = title ?: "Página Web",
                isLoading = false,
                progress = 100,
                canGoBack = canBack,
                canGoForward = canForward
            )
        }
    }

    fun onProgressChanged(progress: Int) {
        _uiState.update { it.copy(progress = progress, isLoading = progress < 100) }
    }

    fun addDiscoveredCandidate(candidate: SnifferCandidate) {
        // Validar que no sea un ejecutable o extensión maliciosa
        if (fileSafetyValidator.isForbiddenExtension(candidate.extension) ||
            fileSafetyValidator.isForbiddenUrlOrFilename(candidate.url)
        ) {
            Timber.tag(TAG).w("Candidato en navegador rechazado por seguridad: ${candidate.url}")
            return
        }

        val currentList = _uiState.value.detectedCandidates
        val isDuplicate = currentList.any { it.url.equals(candidate.url, ignoreCase = true) }

        if (!isDuplicate) {
            val updated = (currentList + candidate).sortedWith(
                compareByDescending<SnifferCandidate> { it.isHls || it.isDash }
                    .thenByDescending { it.contentLength ?: 0L }
                    .thenBy { it.isAudioOnly }
            )
            _uiState.update { it.copy(detectedCandidates = updated) }
            Timber.tag(TAG).d("Stream descubierto en navegador (#${updated.size}): ${candidate.estimatedResolution} | ${candidate.extension}")
        }
    }

    fun toggleCandidatesSheet(show: Boolean) {
        _uiState.update { it.copy(showCandidatesSheet = show) }
    }

    fun promptExternalIntent(intentUrl: String) {
        _uiState.update { it.copy(pendingExternalIntent = intentUrl) }
    }

    fun dismissPendingIntent() {
        _uiState.update { it.copy(pendingExternalIntent = null) }
    }

    fun onRedirectBombingDetected() {
        _uiState.update { it.copy(redirectBombWarning = true, isLoading = false) }
    }

    fun clearRedirectWarning() {
        _uiState.update { it.copy(redirectBombWarning = false) }
    }

    fun enqueueCandidateDownload(candidate: SnifferCandidate, isAudioOnly: Boolean = candidate.isAudioOnly) {
        viewModelScope.launch {
            try {
                val pageUrl = _uiState.value.currentUrl
                val platform = platformDetector.detect(pageUrl)
                val title = if (_uiState.value.pageTitle.isNotBlank() && _uiState.value.pageTitle != "Página Web") {
                    _uiState.value.pageTitle
                } else "Descarga Navegador (${candidate.extension})"

                downloadRepository.enqueueDownload(
                    url = candidate.url,
                    title = title,
                    platform = platform,
                    formatId = candidate.extension,
                    quality = candidate.estimatedResolution ?: "1080p",
                    audioFormat = if (isAudioOnly) "mp3" else null
                )

                _uiState.update {
                    it.copy(
                        showCandidatesSheet = false,
                        downloadSuccessMessage = "¡Video añadido a la cola de descarga!"
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.tag(TAG).e(e, "Error al encolar descarga desde el navegador")
            }
        }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(downloadSuccessMessage = null) }
    }
}
