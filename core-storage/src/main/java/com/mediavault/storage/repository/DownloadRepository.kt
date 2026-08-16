package com.mediavault.storage.repository

import com.mediavault.storage.db.dao.DownloadDao
import com.mediavault.storage.db.dao.QueueDao
import com.mediavault.storage.db.entity.DownloadEntity
import com.mediavault.storage.mediastore.MediaStoreHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao,
    private val queueDao: QueueDao,
    private val mediaStoreHelper: MediaStoreHelper
) {
    val libraryFlow: Flow<List<DownloadEntity>> = downloadDao.getAllFlow()

    suspend fun insert(download: DownloadEntity) = downloadDao.insert(download)

    suspend fun delete(download: DownloadEntity) {
        downloadDao.delete(download)
    }

    suspend fun moveToTrash(id: Long) {
        val item = downloadDao.getById(id)
        if(item != null) {
            downloadDao.update(item.copy(inTrash = true))
        }
    }

    suspend fun restoreFromTrash(id: Long) {
        downloadDao.restoreFromTrash(id)
    }

    suspend fun applyFolderRules(download: DownloadEntity, rules: Map<String, Long>) {
        rules.forEach { (pattern, folderId) ->
            if (download.title.contains(pattern, ignoreCase = true)) {
                downloadDao.moveToFolder(download.id, folderId)
                return
            }
        }
    }

    fun exportHistory(file: File) {
        // Run gracefully synchronously or handle Coroutines
    }

    suspend fun exportHistorySuspend(file: File) {
        val items = downloadDao.getAll()
        val json = Gson().toJson(items)
        file.writeText(json)
    }

    suspend fun importHistory(file: File) {
        if (!file.exists()) return
        val json = file.readText()
        val type = object : TypeToken<List<DownloadEntity>>() {}.type
        val items: List<DownloadEntity> = Gson().fromJson(json, type)
        items.forEach { downloadDao.insert(it) }
    }
}
