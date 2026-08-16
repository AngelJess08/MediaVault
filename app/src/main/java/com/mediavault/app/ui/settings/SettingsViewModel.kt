package com.mediavault.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediavault.downloader.cookies.NetscapeCookieParser
import com.mediavault.storage.datastore.Settings
import com.mediavault.storage.datastore.SettingsDataStore
import com.mediavault.storage.db.dao.CookieDao
import com.mediavault.storage.db.dao.DownloadDao
import com.mediavault.storage.db.entity.CookieEntity
import com.mediavault.storage.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val downloadDao: DownloadDao,
    private val cookieDao: CookieDao,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())

    val cookiesList: StateFlow<List<CookieEntity>> = cookieDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateUpscaleBetaEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.updateUpscaleBetaEnabled(enabled) }
    }

    fun updateUpscaleProvider(provider: String) {
        viewModelScope.launch { settingsDataStore.updateUpscaleProvider(provider) }
    }

    fun updateUpscaleApiKey(key: String) {
        viewModelScope.launch { settingsDataStore.updateUpscaleApiKey(key) }
    }

    fun updateUpscaleEndpoint(endpoint: String) {
        viewModelScope.launch { settingsDataStore.updateUpscaleEndpoint(endpoint) }
    }

    fun updateFalAiApiKey(key: String) {
        viewModelScope.launch { settingsDataStore.updateFalAiApiKey(key) }
    }

    fun updateWifiOnly(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.updateWifiOnlyDownload(enabled) }
    }

    fun updateSpeedLimit(limit: Int) {
        viewModelScope.launch { settingsDataStore.updateDownloadSpeedLimit(limit) }
    }

    fun updateDefaultVideoQuality(quality: String) {
        viewModelScope.launch { settingsDataStore.updateDefaultVideoQuality(quality) }
    }

    fun updateDefaultAudioFormat(format: String) {
        viewModelScope.launch { settingsDataStore.updateDefaultAudioFormat(format) }
    }

    fun updateDefaultAudioBitrate(bitrate: String) {
        viewModelScope.launch { settingsDataStore.updateDefaultAudioBitrate(bitrate) }
    }

    fun updateIsDarkTheme(dark: Boolean) {
        viewModelScope.launch { settingsDataStore.updateIsDarkTheme(dark) }
    }

    fun updateIsDynamicColor(dynamic: Boolean) {
        viewModelScope.launch { settingsDataStore.updateIsDynamicColor(dynamic) }
    }

    fun updateAppLanguage(lang: String) {
        viewModelScope.launch { settingsDataStore.updateAppLanguage(lang) }
    }

    fun updateIsBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.updateIsBiometricEnabled(enabled) }
    }

    fun updateIsIncognitoMode(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.updateIsIncognitoMode(enabled) }
    }

    fun updateAutoDeleteDays(days: Int) {
        viewModelScope.launch { settingsDataStore.updateAutoDeleteDays(days) }
    }

    fun updateTrashEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.updateTrashEnabled(enabled) }
    }

    fun updateDataSaverMode(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.updateDataSaverMode(enabled) }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.updateNotificationsEnabled(enabled) }
    }

    fun updateWeeklyReport(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.updateWeeklyReport(enabled) }
    }

    fun updateAppIconStyle(style: String) {
        viewModelScope.launch { settingsDataStore.updateAppIconStyle(style) }
    }

    fun saveCookies(platform: String, domain: String, cookieString: String) {
        viewModelScope.launch {
            val entity = CookieEntity(
                platform = platform.uppercase(),
                domain = domain,
                cookieString = cookieString,
                updatedAt = System.currentTimeMillis()
            )
            cookieDao.insert(entity)
        }
    }

    fun importCookiesText(content: String, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val list = NetscapeCookieParser.parse(content)
            list.forEach { cookieDao.insert(it) }
            onComplete(list.size)
        }
    }

    fun deleteCookie(id: Long) {
        viewModelScope.launch { cookieDao.deleteById(id) }
    }

    fun clearAllCookies() {
        viewModelScope.launch { cookieDao.deleteAll() }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val inTrash = downloadDao.getInTrash()
            inTrash.forEach { downloadDao.permanentDelete(it.id) }
        }
    }

    fun exportHistory(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val backupFile = File(context.cacheDir, "MediaVault_Backup_${System.currentTimeMillis()}.json")
            downloadRepository.exportHistorySuspend(backupFile)
            onComplete(backupFile.absolutePath)
        }
    }

    fun importHistory(filePath: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val backupFile = File(filePath)
            downloadRepository.importHistory(backupFile)
            onComplete()
        }
    }
}
