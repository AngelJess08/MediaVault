package com.mediavault.storage.repository

import com.mediavault.storage.datastore.SettingsDataStore
import com.mediavault.storage.datastore.Settings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    val settingsFlow: Flow<Settings> = settingsDataStore.settingsFlow

    suspend fun updateDownloadPath(path: String) = settingsDataStore.updateDownloadPath(path)
    suspend fun setDarkTheme(isDark: Boolean) = settingsDataStore.updateIsDarkTheme(isDark)
    suspend fun setLanguage(lang: String) = settingsDataStore.updateAppLanguage(lang)
}
