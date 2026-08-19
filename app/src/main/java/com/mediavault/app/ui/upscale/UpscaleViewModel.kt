package com.mediavault.app.ui.upscale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediavault.storage.datastore.Settings
import com.mediavault.storage.datastore.SettingsDataStore
import com.mediavault.storage.db.dao.DownloadDao
import com.mediavault.storage.db.entity.DownloadEntity
import com.mediavault.storage.db.entity.UpscaleJobEntity
import com.mediavault.upscale.model.UpscaleConfig
import com.mediavault.upscale.model.UpscaleProvider
import com.mediavault.upscale.repository.UpscaleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpscaleUiState(
    val selectedVideo: DownloadEntity? = null,
    val targetResolution: String = "4x (4K)",
    val targetFps: Int = 60,
    val selectedProvider: UpscaleProvider = UpscaleProvider.REPLICATE,
    val isSubmitting: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class UpscaleViewModel @Inject constructor(
    private val upscaleRepository: UpscaleRepository,
    private val downloadDao: DownloadDao,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())

    val upscaleJobs: StateFlow<List<UpscaleJobEntity>> = upscaleRepository.jobsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableVideos: StateFlow<List<DownloadEntity>> = downloadDao.getAllFlow()
        .map { list -> list.filter { it.type == "VIDEO" && !it.inTrash } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(UpscaleUiState())
    val uiState: StateFlow<UpscaleUiState> = _uiState.asStateFlow()

    fun selectVideo(video: DownloadEntity) {
        _uiState.update { it.copy(selectedVideo = video) }
    }

    fun setTargetResolution(resolution: String) {
        _uiState.update { it.copy(targetResolution = resolution) }
    }

    fun setTargetFps(fps: Int) {
        _uiState.update { it.copy(targetFps = fps) }
    }

    fun setProvider(provider: UpscaleProvider) {
        _uiState.update { it.copy(selectedProvider = provider) }
    }

    fun submitJob() {
        val video = _uiState.value.selectedVideo ?: return
        val currentSettings = settings.value
        val provider = try {
            UpscaleProvider.valueOf(currentSettings.upscaleProvider.uppercase())
        } catch (e: Exception) {
            UpscaleProvider.REPLICATE
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                val config = UpscaleConfig(
                    provider = provider,
                    apiKey = if (provider == UpscaleProvider.REPLICATE) currentSettings.upscaleApiKey else currentSettings.falAiApiKey,
                    customEndpoint = currentSettings.upscaleEndpoint
                )

                upscaleRepository.submitUpscaleJob(
                    sourceDownloadId = video.id,
                    sourceFilePath = video.filePath,
                    targetResolution = _uiState.value.targetResolution,
                    targetFps = _uiState.value.targetFps,
                    config = config
                )

                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        selectedVideo = null,
                        message = "¡Trabajo de escalado enviado a la GPU en la nube!"
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        message = "Error al enviar: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
