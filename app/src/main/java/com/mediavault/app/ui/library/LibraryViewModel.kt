package com.mediavault.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediavault.downloader.model.Platform
import com.mediavault.storage.db.dao.DownloadDao
import com.mediavault.storage.db.entity.DownloadEntity
import com.mediavault.storage.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryFilter { ALL, VIDEOS, AUDIOS, UPSCALED, FAVORITES, TRASH }

data class LibraryUiState(
    val selectedFilter: LibraryFilter = LibraryFilter.ALL,
    val searchQuery: String = "",
    val isGridView: Boolean = false,
    val selectedPlatformFilter: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val downloadDao: DownloadDao,
    private val downloadRepository: DownloadRepository,
    private val coreDownloaderRepo: com.mediavault.downloader.repository.DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    val downloads: StateFlow<List<DownloadEntity>> = combine(
        downloadDao.getAllFlow(),
        _uiState
    ) { allItems, state ->
        val list = when (state.selectedFilter) {
            LibraryFilter.ALL -> allItems.filter { !it.inTrash }
            LibraryFilter.VIDEOS -> allItems.filter { it.type == "VIDEO" && !it.inTrash }
            LibraryFilter.AUDIOS -> allItems.filter { it.type == "AUDIO" && !it.inTrash }
            LibraryFilter.UPSCALED -> allItems.filter { it.filePath.contains("Upscaled") && !it.inTrash }
            LibraryFilter.FAVORITES -> allItems.filter { it.isFavorite && !it.inTrash }
            LibraryFilter.TRASH -> downloadDao.getInTrash()
        }

        list.filter { item ->
            val matchesQuery = state.searchQuery.isBlank() || item.title.contains(state.searchQuery, ignoreCase = true)
            val matchesPlatform = state.selectedPlatformFilter == null || item.platform.equals(state.selectedPlatformFilter, ignoreCase = true)
            matchesQuery && matchesPlatform
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: LibraryFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setPlatformFilter(platform: String?) {
        _uiState.update { it.copy(selectedPlatformFilter = platform) }
    }

    fun toggleViewMode() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun reDownload(item: DownloadEntity) {
        viewModelScope.launch {
            coreDownloaderRepo.enqueueDownload(
                url = item.url,
                title = item.title,
                platform = try { Platform.valueOf(item.platform) } catch (e: Exception) { Platform.GENERIC },
                formatId = item.format,
                quality = item.videoResolution ?: "1080p",
                audioFormat = if (item.type == "AUDIO") "mp3" else null
            )
        }
    }

    fun toggleFavorite(item: DownloadEntity) {
        viewModelScope.launch {
            downloadDao.markAsFavorite(item.id, !item.isFavorite)
        }
    }

    fun moveToTrash(id: Long) {
        viewModelScope.launch {
            downloadRepository.moveToTrash(id)
        }
    }

    fun restoreFromTrash(id: Long) {
        viewModelScope.launch {
            downloadRepository.restoreFromTrash(id)
        }
    }

    fun permanentDelete(id: Long) {
        viewModelScope.launch {
            downloadDao.permanentDelete(id)
        }
    }
}
