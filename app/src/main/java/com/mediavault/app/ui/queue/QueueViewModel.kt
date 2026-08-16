package com.mediavault.app.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediavault.downloader.repository.DownloadRepository
import com.mediavault.storage.db.entity.QueueItemEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    val queueItems: StateFlow<List<QueueItemEntity>> = downloadRepository.activeQueueFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun pauseDownload(id: Long) {
        viewModelScope.launch { downloadRepository.pauseDownload(id) }
    }

    fun cancelDownload(id: Long) {
        viewModelScope.launch { downloadRepository.cancelDownload(id) }
    }

    fun retryDownload(item: QueueItemEntity) {
        viewModelScope.launch { downloadRepository.retryDownload(item) }
    }
}
