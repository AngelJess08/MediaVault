package com.mediavault.downloader.ytdlp

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

enum class YtDlpManagerState {
    DOWNLOADING_BINARY, READY, UPDATING, ERROR
}

@Singleton
class YtDlpManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow(YtDlpManagerState.READY)
    val state: Flow<YtDlpManagerState> get() = _state

    private val binaryName = "yt-dlp"

    suspend fun downloadBinary() = withContext(Dispatchers.IO) {
        _state.value = YtDlpManagerState.DOWNLOADING_BINARY
        try {
            val url = URL("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val assets = json.getJSONArray("assets")
            
            // Simplified architecture detection for this example
            val archStr = System.getProperty("os.arch") ?: ""
            val assetName = when {
                archStr.contains("aarch64") || archStr.contains("arm64") -> "yt-dlp_linux_aarch64"
                archStr.contains("arm") -> "yt-dlp_linux_armv7l"
                else -> "yt-dlp_linux"
            }

            var downloadUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name") == assetName) {
                    downloadUrl = asset.getString("browser_download_url")
                    break
                }
            }

            if (downloadUrl.isNotEmpty()) {
                val binUrl = URL(downloadUrl)
                val binConnection = binUrl.openConnection() as HttpURLConnection
                val file = File(context.filesDir, binaryName)
                binConnection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                makeExecutable(file)
                _state.value = YtDlpManagerState.READY
            } else {
                _state.value = YtDlpManagerState.ERROR
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _state.value = YtDlpManagerState.ERROR
        }
    }

    suspend fun checkForUpdate(): Boolean = withContext(Dispatchers.IO) {
        // Implementation for update checking
        return@withContext false
    }

    fun getBinaryPath(): String {
        return File(context.filesDir, binaryName).absolutePath
    }

    fun isBinaryAvailable(): Boolean {
        val file = File(context.filesDir, binaryName)
        return file.exists() && file.canExecute()
    }

    private fun makeExecutable(file: File) {
        try {
            Runtime.getRuntime().exec("chmod 755 ${file.absolutePath}").waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
