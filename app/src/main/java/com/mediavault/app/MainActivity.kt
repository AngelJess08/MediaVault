package com.mediavault.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.mediavault.app.navigation.MainAppNavigation
import com.mediavault.app.ui.theme.MediaVaultTheme
import com.mediavault.downloader.detector.PlatformDetector
import com.mediavault.downloader.repository.DownloadRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.regex.Pattern
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var downloadRepository: DownloadRepository

    @Inject
    lateinit var platformDetector: PlatformDetector

    val sharedUrlsState = mutableStateOf<List<String>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            MediaVaultTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    MainAppNavigation(navController = navController)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                if (!text.isNullOrBlank()) {
                    val extracted = extractUrls(text)
                    if (extracted.isNotEmpty()) {
                        processSharedUrls(extracted)
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val textList = intent.getStringArrayListExtra(Intent.EXTRA_TEXT) ?: emptyList()
                val allUrls = textList.flatMap { extractUrls(it) }
                if (allUrls.isNotEmpty()) {
                    processSharedUrls(allUrls)
                }
            }
        }
    }

    private fun processSharedUrls(urls: List<String>) {
        Timber.tag("MediaVaultDownload").d("Detectadas ${urls.size} URLs compartidas desde otra app")
        lifecycleScope.launch {
            for (u in urls) {
                val platform = platformDetector.detect(u)
                downloadRepository.enqueueDownload(
                    url = u,
                    title = "Compartido: ${platform.name} - ${u.takeLast(10)}",
                    platform = platform,
                    formatId = "best",
                    quality = "1080p"
                )
            }
        }
    }

    private fun extractUrls(text: String): List<String> {
        val list = mutableListOf<String>()
        val urlPattern = Pattern.compile("(https?://[\\w-]+(\\.[\\w-]+)+(/[^\\s]*)?)", Pattern.CASE_INSENSITIVE)
        val matcher = urlPattern.matcher(text)
        while (matcher.find()) {
            list.add(matcher.group())
        }
        return list
    }
}
