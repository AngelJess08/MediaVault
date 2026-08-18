package com.mediavault.storage.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class Settings(
    val downloadPath: String = "",
    val audioPath: String = "",
    val videoPath: String = "",
    val upscaledPath: String = "",
    val wifiOnlyDownload: Boolean = false,
    val downloadSpeedLimit: Int = 0,
    val upscaleBetaEnabled: Boolean = false,
    val upscaleProvider: String = "replicate",
    val upscaleApiKey: String = "",
    val upscaleEndpoint: String = "http://10.0.2.2:8000",
    val falAiApiKey: String = "",
    val isDarkTheme: Boolean = false,
    val isDynamicColor: Boolean = true,
    val appLanguage: String = "es",
    val isBiometricEnabled: Boolean = false,
    val isIncognitoMode: Boolean = false,
    val autoDeleteAfterDays: Int = 0,
    val trashEnabled: Boolean = true,
    val dataSaverMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val weeklyReport: Boolean = false,
    val hasSeenOnboarding: Boolean = false,
    val defaultVideoQuality: String = "1080p",
    val defaultAudioFormat: String = "mp3",
    val defaultAudioBitrate: String = "320k",
    val scheduledDownloadTime: String = "02:00",
    val appIconStyle: String = "default",
    val isBrowserModeEnabled: Boolean = false
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    val settingsFlow: Flow<Settings> = dataStore.data.map { preferences ->
        Settings(
            downloadPath = preferences[DOWNLOAD_PATH] ?: "",
            audioPath = preferences[AUDIO_PATH] ?: "",
            videoPath = preferences[VIDEO_PATH] ?: "",
            upscaledPath = preferences[UPSCALED_PATH] ?: "",
            wifiOnlyDownload = preferences[WIFI_ONLY] ?: false,
            downloadSpeedLimit = preferences[SPEED_LIMIT] ?: 0,
            upscaleBetaEnabled = preferences[UPSCALE_BETA_ENABLED] ?: false,
            upscaleProvider = preferences[UPSCALE_PROVIDER] ?: "replicate",
            upscaleApiKey = preferences[UPSCALE_API_KEY] ?: "",
            upscaleEndpoint = preferences[UPSCALE_ENDPOINT] ?: "http://10.0.2.2:8000",
            falAiApiKey = preferences[FALAI_API_KEY] ?: "",
            isDarkTheme = preferences[DARK_THEME] ?: false,
            isDynamicColor = preferences[DYNAMIC_COLOR] ?: true,
            appLanguage = preferences[APP_LANGUAGE] ?: "es",
            isBiometricEnabled = preferences[BIOMETRIC_ENABLED] ?: false,
            isIncognitoMode = preferences[INCOGNITO_MODE] ?: false,
            autoDeleteAfterDays = preferences[AUTO_DELETE_DAYS] ?: 0,
            trashEnabled = preferences[TRASH_ENABLED] ?: true,
            dataSaverMode = preferences[DATA_SAVER] ?: false,
            notificationsEnabled = preferences[NOTIFICATIONS] ?: true,
            weeklyReport = preferences[WEEKLY_REPORT] ?: false,
            hasSeenOnboarding = preferences[ONBOARDING] ?: false,
            defaultVideoQuality = preferences[DEFAULT_VIDEO_Q] ?: "1080p",
            defaultAudioFormat = preferences[DEFAULT_AUDIO_F] ?: "mp3",
            defaultAudioBitrate = preferences[DEFAULT_AUDIO_B] ?: "320k",
            scheduledDownloadTime = preferences[SCHEDULED_TIME] ?: "02:00",
            appIconStyle = preferences[APP_ICON_STYLE] ?: "default",
            isBrowserModeEnabled = preferences[BROWSER_MODE_ENABLED] ?: false
        )
    }

    suspend fun updateDownloadPath(value: String) = update(DOWNLOAD_PATH, value)
    suspend fun updateWifiOnlyDownload(value: Boolean) = update(WIFI_ONLY, value)
    suspend fun updateDownloadSpeedLimit(value: Int) = update(SPEED_LIMIT, value)
    suspend fun updateUpscaleBetaEnabled(value: Boolean) = update(UPSCALE_BETA_ENABLED, value)
    suspend fun updateUpscaleProvider(value: String) = update(UPSCALE_PROVIDER, value)
    suspend fun updateUpscaleApiKey(value: String) = update(UPSCALE_API_KEY, value)
    suspend fun updateUpscaleEndpoint(value: String) = update(UPSCALE_ENDPOINT, value)
    suspend fun updateFalAiApiKey(value: String) = update(FALAI_API_KEY, value)
    suspend fun updateIsDarkTheme(value: Boolean) = update(DARK_THEME, value)
    suspend fun updateIsDynamicColor(value: Boolean) = update(DYNAMIC_COLOR, value)
    suspend fun updateAppLanguage(value: String) = update(APP_LANGUAGE, value)
    suspend fun updateIsBiometricEnabled(value: Boolean) = update(BIOMETRIC_ENABLED, value)
    suspend fun updateIsIncognitoMode(value: Boolean) = update(INCOGNITO_MODE, value)
    suspend fun updateAutoDeleteDays(value: Int) = update(AUTO_DELETE_DAYS, value)
    suspend fun updateTrashEnabled(value: Boolean) = update(TRASH_ENABLED, value)
    suspend fun updateDataSaverMode(value: Boolean) = update(DATA_SAVER, value)
    suspend fun updateNotificationsEnabled(value: Boolean) = update(NOTIFICATIONS, value)
    suspend fun updateWeeklyReport(value: Boolean) = update(WEEKLY_REPORT, value)
    suspend fun updateHasSeenOnboarding(value: Boolean) = update(ONBOARDING, value)
    suspend fun updateDefaultVideoQuality(value: String) = update(DEFAULT_VIDEO_Q, value)
    suspend fun updateDefaultAudioFormat(value: String) = update(DEFAULT_AUDIO_F, value)
    suspend fun updateDefaultAudioBitrate(value: String) = update(DEFAULT_AUDIO_B, value)
    suspend fun updateScheduledDownloadTime(value: String) = update(SCHEDULED_TIME, value)
    suspend fun updateAppIconStyle(value: String) = update(APP_ICON_STYLE, value)
    suspend fun updateIsBrowserModeEnabled(value: Boolean) = update(BROWSER_MODE_ENABLED, value)
    
    private suspend fun <T> update(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    companion object {
        val DOWNLOAD_PATH = stringPreferencesKey("download_path")
        val AUDIO_PATH = stringPreferencesKey("audio_path")
        val VIDEO_PATH = stringPreferencesKey("video_path")
        val UPSCALED_PATH = stringPreferencesKey("upscaled_path")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val SPEED_LIMIT = intPreferencesKey("speed_limit")
        val UPSCALE_BETA_ENABLED = booleanPreferencesKey("upscale_beta_enabled")
        val UPSCALE_PROVIDER = stringPreferencesKey("upscale_provider")
        val UPSCALE_API_KEY = stringPreferencesKey("upscale_api_key")
        val UPSCALE_ENDPOINT = stringPreferencesKey("upscale_endpoint")
        val FALAI_API_KEY = stringPreferencesKey("falai_api_key")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val INCOGNITO_MODE = booleanPreferencesKey("incognito_mode")
        val AUTO_DELETE_DAYS = intPreferencesKey("auto_delete_days")
        val TRASH_ENABLED = booleanPreferencesKey("trash_enabled")
        val DATA_SAVER = booleanPreferencesKey("data_saver")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val WEEKLY_REPORT = booleanPreferencesKey("weekly_report")
        val ONBOARDING = booleanPreferencesKey("onboarding")
        val DEFAULT_VIDEO_Q = stringPreferencesKey("default_video_quality")
        val DEFAULT_AUDIO_F = stringPreferencesKey("default_audio_format")
        val DEFAULT_AUDIO_B = stringPreferencesKey("default_audio_bitrate")
        val SCHEDULED_TIME = stringPreferencesKey("scheduled_time")
        val APP_ICON_STYLE = stringPreferencesKey("app_icon_style")
        val BROWSER_MODE_ENABLED = booleanPreferencesKey("browser_mode_enabled")
    }
}
