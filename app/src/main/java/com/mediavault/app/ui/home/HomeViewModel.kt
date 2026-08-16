package com.mediavault.app.ui.home

import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediavault.downloader.detector.PlatformDetector
import com.mediavault.downloader.model.*
import com.mediavault.downloader.repository.DownloadRepository
import com.mediavault.downloader.ytdlp.YtDlpManager
import com.mediavault.storage.datastore.SettingsDataStore
import com.mediavault.storage.db.entity.DownloadEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val urlInput: String = "",
    val detectedPlatform: Platform = Platform.UNKNOWN,
    val isLoading: Boolean = false,
    val mediaInfo: MediaInfo? = null,
    val errorMessage: String? = null,
    val selectedFormatId: String = "best_1080p",
    val selectedResolution: String = "1080p",
    val selectedAudioFormat: String = "none",
    val selectedAudioBitrate: String = "320k",
    val isAudioOnly: Boolean = false,
    val isThumbnailOnly: Boolean = false,
    val burnSubtitles: Boolean = false,
    val selectedSubtitleLang: String? = null,
    val trimStartSeconds: Long? = null,
    val trimEndSeconds: Long? = null,
    val scheduledDelayMinutes: Long = 0,
    val wifiOnly: Boolean = false,
    val speedLimitKbps: Int = 0,
    val isIncognito: Boolean = false,
    val isBatchMode: Boolean = false,
    val batchUrlsText: String = "",
    val extractorHealth: String = "OK",
    val showSuccessMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val platformDetector: PlatformDetector,
    private val ytDlpManager: YtDlpManager,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val recentDownloads: StateFlow<List<DownloadEntity>> = downloadRepository.historyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(
                        wifiOnly = settings.wifiOnlyDownload,
                        speedLimitKbps = settings.downloadSpeedLimit,
                        isIncognito = settings.isIncognitoMode
                    )
                }
            }
        }
    }

    fun onUrlChanged(newUrl: String) {
        val detected = platformDetector.detect(newUrl)
        _uiState.update {
            it.copy(
                urlInput = newUrl,
                detectedPlatform = detected,
                errorMessage = null
            )
        }
    }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            if (text.isNotBlank()) {
                onUrlChanged(text)
                analyzeUrl(text)
            }
        }
    }

    fun analyzeUrl(url: String = _uiState.value.urlInput) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val info = downloadRepository.fetchMediaInfo(url)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        mediaInfo = info,
                        detectedPlatform = info.platform,
                        selectedFormatId = info.formats.firstOrNull { f -> !f.isAudioOnly }?.formatId ?: "best",
                        selectedResolution = info.formats.firstOrNull { f -> !f.isAudioOnly }?.resolution ?: "1080p"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Error al obtener información: ${e.message}"
                    )
                }
            }
        }
    }

    fun setSelectedFormat(format: FormatOption) {
        _uiState.update {
            it.copy(
                selectedFormatId = format.formatId,
                selectedResolution = format.resolution ?: it.selectedResolution,
                isAudioOnly = format.isAudioOnly
            )
        }
    }

    fun setSelectedAudio(format: String, bitrate: String) {
        _uiState.update {
            it.copy(
                selectedAudioFormat = format,
                selectedAudioBitrate = bitrate
            )
        }
    }

    fun toggleAudioOnly(enabled: Boolean) {
        _uiState.update { it.copy(isAudioOnly = enabled) }
    }

    fun toggleThumbnailOnly(enabled: Boolean) {
        _uiState.update { it.copy(isThumbnailOnly = enabled) }
    }

    fun toggleBurnSubtitles(enabled: Boolean) {
        _uiState.update { it.copy(burnSubtitles = enabled) }
    }

    fun setSelectedSubtitle(lang: String?) {
        _uiState.update { it.copy(selectedSubtitleLang = lang) }
    }

    fun setTrim(startSec: Long?, endSec: Long?) {
        _uiState.update { it.copy(trimStartSeconds = startSec, trimEndSeconds = endSec) }
    }

    fun setScheduledDelayMinutes(minutes: Long) {
        _uiState.update { it.copy(scheduledDelayMinutes = minutes) }
    }

    fun toggleWifiOnly(enabled: Boolean) {
        _uiState.update { it.copy(wifiOnly = enabled) }
    }

    fun setSpeedLimit(kbps: Int) {
        _uiState.update { it.copy(speedLimitKbps = kbps) }
    }

    fun toggleIncognito(enabled: Boolean) {
        _uiState.update { it.copy(isIncognito = enabled) }
    }

    fun toggleBatchMode(enabled: Boolean) {
        _uiState.update { it.copy(isBatchMode = enabled) }
    }

    fun onBatchUrlsChanged(text: String) {
        _uiState.update { it.copy(batchUrlsText = text) }
    }

    fun startDownload() {
        val state = _uiState.value
        val url = state.urlInput
        if (url.isBlank()) return

        viewModelScope.launch {
            try {
                val title = state.mediaInfo?.title ?: "Descarga ${state.detectedPlatform.name}"
                downloadRepository.enqueueDownload(
                    url = url,
                    title = title,
                    platform = state.detectedPlatform,
                    formatId = state.selectedFormatId,
                    quality = state.selectedResolution,
                    audioFormat = if (state.isAudioOnly) (if (state.selectedAudioFormat == "none") "mp3" else state.selectedAudioFormat) else null,
                    audioBitrate = state.selectedAudioBitrate,
                    trimStart = state.trimStartSeconds,
                    trimEnd = state.trimEndSeconds,
                    burnSubtitles = state.burnSubtitles,
                    subtitleLang = state.selectedSubtitleLang,
                    downloadThumbnailOnly = state.isThumbnailOnly,
                    scheduledDelayMinutes = state.scheduledDelayMinutes,
                    wifiOnly = state.wifiOnly,
                    speedLimitKbps = state.speedLimitKbps
                )
                _uiState.update {
                    it.copy(
                        urlInput = "",
                        mediaInfo = null,
                        showSuccessMessage = "¡Descarga añadida a la cola!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al iniciar descarga: ${e.message}") }
            }
        }
    }

    fun startBatchDownload() {
        val urls = _uiState.value.batchUrlsText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://")) }

        if (urls.isEmpty()) return

        viewModelScope.launch {
            for (u in urls) {
                val platform = platformDetector.detect(u)
                downloadRepository.enqueueDownload(
                    url = u,
                    title = "Lote ${platform.name} - ${u.takeLast(8)}",
                    platform = platform,
                    formatId = "best",
                    quality = "1080p",
                    wifiOnly = _uiState.value.wifiOnly,
                    speedLimitKbps = _uiState.value.speedLimitKbps
                )
            }
            _uiState.update {
                it.copy(
                    batchUrlsText = "",
                    isBatchMode = false,
                    showSuccessMessage = "${urls.size} descargas encoladas exitosamente"
                )
            }
        }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(showSuccessMessage = null) }
    }
}
