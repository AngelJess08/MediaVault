package com.mediavault.app.ui.home

import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediavault.downloader.detector.PlatformDetector
import com.mediavault.downloader.model.FormatOption
import com.mediavault.downloader.model.MediaInfo
import com.mediavault.downloader.model.Platform
import com.mediavault.downloader.repository.DownloadRepository
import com.mediavault.downloader.universal.BlobMseUnsupportedException
import com.mediavault.downloader.universal.DrmProtectedException
import com.mediavault.storage.datastore.SettingsDataStore
import com.mediavault.storage.db.entity.DownloadEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class HomeUiState(
    val urlInput: String = "",
    val detectedPlatform: Platform = Platform.UNKNOWN,
    val isLoading: Boolean = false,
    val isUniversalModeActive: Boolean = false,
    val universalModeMessage: String? = null,
    val mediaInfo: MediaInfo? = null,
    val errorMessage: String? = null,
    val isLoginRequiredError: Boolean = false,
    val isPrivateOrDeletedError: Boolean = false,
    val isBlobMseUnsupported: Boolean = false,
    val isDrmProtected: Boolean = false,
    val selectedFormatId: String = "best",
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
    val showSuccessMessage: String? = null,
    val duplicateExistingDownload: DownloadEntity? = null,
    val detectedClipboardUrl: String? = null,
    val isBrowserModeEnabled: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val platformDetector: PlatformDetector,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null

    val recentDownloads: StateFlow<List<DownloadEntity>> = downloadRepository.historyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(
                        wifiOnly = settings.wifiOnlyDownload,
                        speedLimitKbps = settings.downloadSpeedLimit,
                        isIncognito = settings.isIncognitoMode,
                        isBrowserModeEnabled = settings.isBrowserModeEnabled
                    )
                }
            }
        }
        checkClipboardForVideoUrl()
    }

    fun checkClipboardForVideoUrl() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()?.trim() ?: ""
                if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) {
                    val platform = platformDetector.detect(text)
                    if (platform != Platform.UNKNOWN && text != _uiState.value.urlInput) {
                        _uiState.update { it.copy(detectedClipboardUrl = text) }
                        Timber.tag("MediaVaultDebug").d("Enlace en portapapeles detectado: $text ($platform)")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("MediaVaultDebug").w("No se pudo leer portapapeles: ${e.message}")
        }
    }

    fun applyDetectedClipboardUrl() {
        val detected = _uiState.value.detectedClipboardUrl ?: return
        _uiState.update { it.copy(detectedClipboardUrl = null) }
        onUrlChanged(detected)
        analyzeUrl(detected)
    }

    fun dismissDetectedClipboardUrl() {
        _uiState.update { it.copy(detectedClipboardUrl = null) }
    }

    fun onUrlChanged(newUrl: String) {
        val detected = platformDetector.detect(newUrl)
        _uiState.update {
            it.copy(
                urlInput = newUrl,
                detectedPlatform = detected,
                errorMessage = null,
                isLoginRequiredError = false,
                isPrivateOrDeletedError = false,
                isBlobMseUnsupported = false,
                isDrmProtected = false,
                duplicateExistingDownload = null
            )
        }
    }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()?.trim() ?: ""
            if (text.isNotBlank()) {
                onUrlChanged(text)
                analyzeUrl(text)
            }
        }
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = false,
                isUniversalModeActive = false,
                universalModeMessage = null
            )
        }
        Timber.tag("MediaVaultUniversalMode").d("Análisis cancelado por el usuario.")
    }

    fun analyzeUrl(url: String = _uiState.value.urlInput) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return

        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isUniversalModeActive = false,
                    universalModeMessage = null,
                    errorMessage = null,
                    isLoginRequiredError = false,
                    isPrivateOrDeletedError = false,
                    isBlobMseUnsupported = false,
                    isDrmProtected = false
                )
            }
            Timber.tag("MediaVaultDebug").d("Iniciando análisis de URL: $trimmed")

            try {
                // Verificar duplicados primero
                val existing = downloadRepository.checkDuplicate(trimmed)
                if (existing != null) {
                    Timber.tag("MediaVaultDebug").d("URL duplicada detectada en historial: ${existing.title}")
                    _uiState.update { it.copy(duplicateExistingDownload = existing) }
                }

                val info = downloadRepository.fetchMediaInfo(trimmed) { statusMsg ->
                    _uiState.update {
                        it.copy(
                            isUniversalModeActive = true,
                            universalModeMessage = statusMsg
                        )
                    }
                }

                // AUTO-SELECCIÓN DE CALIDAD NATIVA MÁS ALTA POR DEFECTO
                val videoFormats = info.formats.filter { !it.isAudioOnly }
                val highestNativeFormat = videoFormats.sortedWith(
                    compareByDescending<FormatOption> { it.height ?: 0 }
                        .thenByDescending { it.fps ?: 0f }
                        .thenByDescending { it.filesizeApprox ?: 0L }
                ).firstOrNull() ?: info.formats.firstOrNull()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isUniversalModeActive = false,
                        universalModeMessage = null,
                        mediaInfo = info,
                        detectedPlatform = info.platform,
                        selectedFormatId = highestNativeFormat?.formatId ?: "best",
                        selectedResolution = highestNativeFormat?.resolution ?: "1080p",
                        isAudioOnly = highestNativeFormat?.isAudioOnly ?: false
                    )
                }
                Timber.tag("MediaVaultDebug").d("Calidad nativa más alta preseleccionada: ${highestNativeFormat?.resolution}")

            } catch (e: Exception) {
                val errorMsg = e.message ?: "Error al conectar con la plataforma"
                val isLoginReq = errorMsg.contains("Login requerido", ignoreCase = true) ||
                        errorMsg.contains("401") || errorMsg.contains("403") ||
                        errorMsg.contains("cookies", ignoreCase = true)
                val isDeletedOrPrivate = errorMsg.contains("privad", ignoreCase = true) ||
                        errorMsg.contains("borrad", ignoreCase = true) ||
                        errorMsg.contains("no encontrad", ignoreCase = true) ||
                        errorMsg.contains("404")
                val isBlob = e is BlobMseUnsupportedException || errorMsg.contains("Media Source", ignoreCase = true) || errorMsg.contains("blob:", ignoreCase = true)
                val isDrm = e is DrmProtectedException || errorMsg.contains("DRM", ignoreCase = true) || errorMsg.contains("Widevine", ignoreCase = true)

                Timber.tag("MediaVaultDebug").e(e, "Error al analizar URL: $errorMsg")

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isUniversalModeActive = false,
                        universalModeMessage = null,
                        errorMessage = errorMsg,
                        isLoginRequiredError = isLoginReq,
                        isPrivateOrDeletedError = isDeletedOrPrivate,
                        isBlobMseUnsupported = isBlob,
                        isDrmProtected = isDrm
                    )
                }
            }
        }
    }

    fun reportFailedLink(url: String, error: String) {
        viewModelScope.launch {
            try {
                val reportFile = File(context.cacheDir, "failed_links_report.log")
                val logEntry = "[${System.currentTimeMillis()}] URL: $url | Error: $error\n"
                reportFile.appendText(logEntry)
                Timber.tag("MediaVaultDebug").d("Enlace fallido reportado y guardado localmente: $url")
                _uiState.update { it.copy(showSuccessMessage = "Enlace reportado localmente para depuración.") }
            } catch (e: Exception) {
                Timber.tag("MediaVaultDebug").e(e, "Error al guardar reporte de enlace")
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
                selectedAudioBitrate = bitrate,
                isAudioOnly = true
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

    fun dismissDuplicatePrompt() {
        _uiState.update { it.copy(duplicateExistingDownload = null) }
    }

    fun startDownload(ignoreDuplicate: Boolean = false) {
        val state = _uiState.value
        val url = state.urlInput.trim()
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
                        duplicateExistingDownload = null,
                        showSuccessMessage = if (state.scheduledDelayMinutes > 0) "Descarga programada para dentro de ${state.scheduledDelayMinutes} minutos" else "¡Descarga añadida a la cola!"
                    )
                }
            } catch (e: Exception) {
                Timber.tag("MediaVaultDebug").e(e, "Error al encolar descarga")
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
