package com.mediavault.downloader.queue

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Assume these models exist in a common module
data class QueueItem(val id: String, val url: String, val status: DownloadStatus)
enum class DownloadStatus { PENDING, DOWNLOADING, PAUSED, COMPLETED, ERROR, CANCELLED }

@Singleton
class DownloadQueue @Inject constructor() {

    private val _activeDownloads = MutableStateFlow<List<QueueItem>>(emptyList())
    val activeDownloads: Flow<List<QueueItem>> = _activeDownloads

    private val _pendingDownloads = MutableStateFlow<List<QueueItem>>(emptyList())
    val pendingDownloads: Flow<List<QueueItem>> = _pendingDownloads

    private val _currentDownload = MutableStateFlow<QueueItem?>(null)
    val currentDownload: StateFlow<QueueItem?> = _currentDownload

    private val allItems = mutableListOf<QueueItem>()

    fun addItem(item: QueueItem) {
        allItems.add(item)
        updateFlows()
    }

    fun removeItem(id: String) {
        allItems.removeAll { it.id == id }
        updateFlows()
    }

    fun pauseItem(id: String) {
        updateItemStatus(id, DownloadStatus.PAUSED)
    }

    fun resumeItem(id: String) {
        updateItemStatus(id, DownloadStatus.PENDING)
    }

    fun cancelItem(id: String) {
        updateItemStatus(id, DownloadStatus.CANCELLED)
    }

    fun reorderItems() {
        // Implementation for reordering
        updateFlows()
    }

    private fun updateItemStatus(id: String, status: DownloadStatus) {
        val index = allItems.indexOfFirst { it.id == id }
        if (index != -1) {
            allItems[index] = allItems[index].copy(status = status)
            updateFlows()
        }
    }

    private fun updateFlows() {
        _activeDownloads.value = allItems.filter { it.status == DownloadStatus.DOWNLOADING }
        _pendingDownloads.value = allItems.filter { it.status == DownloadStatus.PENDING }
        _currentDownload.value = _activeDownloads.value.firstOrNull()
    }
}
