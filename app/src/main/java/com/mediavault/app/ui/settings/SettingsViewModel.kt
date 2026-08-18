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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
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

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

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

    fun saveCookies(platform: String, urlOrDomain: String, cookieString: String) {
        viewModelScope.launch {
            val rootDomain = try {
                val uri = URI(if (!urlOrDomain.startsWith("http")) "https://$urlOrDomain" else urlOrDomain)
                uri.host?.removePrefix("www.")?.removePrefix("m.") ?: urlOrDomain
            } catch (e: Exception) {
                urlOrDomain
            }

            val entity = CookieEntity(
                platform = platform.uppercase(),
                domain = rootDomain,
                cookieString = cookieString,
                updatedAt = System.currentTimeMillis()
            )
            cookieDao.insert(entity)
            Timber.tag("MediaVaultDebug").d("Cookies guardadas para $platform ($rootDomain)")
        }
    }

    fun testCookieSession(platform: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val cookiesList = cookieDao.getAllByPlatform(platform.uppercase())
            if (cookiesList.isEmpty()) {
                withContext(Dispatchers.Main) {
                    onResult(false, "No hay cookies guardadas para $platform")
                }
                return@launch
            }

            val combinedCookies = cookiesList.map { it.cookieString }.filter { it.isNotBlank() }.joinToString("; ")
            val testUrl = when (platform.uppercase()) {
                "TWITTER", "X" -> "https://x.com/home"
                "INSTAGRAM" -> "https://www.instagram.com/"
                "FACEBOOK" -> "https://m.facebook.com/"
                "YOUTUBE" -> "https://www.youtube.com/"
                else -> "https://${cookiesList.first().domain}"
            }

            Timber.tag("MediaVaultDebug").d("Probando sesión de $platform en $testUrl...")

            try {
                val req = Request.Builder()
                    .url(testUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                    .header("Cookie", combinedCookies)
                    .build()

                httpClient.newCall(req).execute().use { response ->
                    val isSuccess = response.isSuccessful && response.code < 400
                    val body = response.body?.string() ?: ""
                    val isRedirectToLogin = response.request.url.toString().contains("login", ignoreCase = true) ||
                            body.contains("login_form", ignoreCase = true) ||
                            body.contains("Acceder", ignoreCase = true)

                    val isValid = isSuccess && !isRedirectToLogin
                    val msg = if (isValid) {
                        "¡Sesión activa y válida para $platform (HTTP ${response.code})!"
                    } else {
                        "Las cookies están expiradas o incompletas (Redirige a login)."
                    }

                    Timber.tag("MediaVaultDebug").d("Resultado prueba de sesión: $msg")
                    withContext(Dispatchers.Main) {
                        onResult(isValid, msg)
                    }
                }
            } catch (e: Exception) {
                Timber.tag("MediaVaultDebug").e(e, "Error al probar sesión de $platform")
                withContext(Dispatchers.Main) {
                    onResult(false, "Error de red al conectar: ${e.message}")
                }
            }
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

    fun updateIsBrowserModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateIsBrowserModeEnabled(enabled)
        }
    }
}
