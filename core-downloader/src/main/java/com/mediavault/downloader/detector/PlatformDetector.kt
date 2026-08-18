package com.mediavault.downloader.detector

import com.mediavault.downloader.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class PlatformDetector @Inject constructor() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    /**
     * Sigue todas las redirecciones HTTP (acortadores bit.ly, t.co, vt.tiktok.com, etc.)
     * para obtener la URL final de destino antes de detectar y extraer.
     */
    open suspend fun resolveRedirects(url: String): String = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return@withContext trimmed
        }

        Timber.tag("MediaVaultDebug").d("Resolviendo redirecciones para: $trimmed")
        try {
            val request = Request.Builder()
                .url(trimmed)
                .header("User-Agent", userAgent)
                .head()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                Timber.tag("MediaVaultDebug").d("URL final resuelta: $finalUrl (Código HTTP: ${response.code})")
                return@withContext finalUrl
            }
        } catch (e: Exception) {
            try {
                val getRequest = Request.Builder()
                    .url(trimmed)
                    .header("User-Agent", userAgent)
                    .get()
                    .build()

                httpClient.newCall(getRequest).execute().use { response ->
                    val finalUrl = response.request.url.toString()
                    Timber.tag("MediaVaultDebug").d("URL final resuelta vía GET: $finalUrl")
                    return@withContext finalUrl
                }
            } catch (e2: Exception) {
                Timber.tag("MediaVaultDebug").w("No se pudieron resolver redirecciones de $trimmed: ${e2.message}")
                return@withContext trimmed
            }
        }
    }

    open fun detect(url: String): Platform {
        val lowerUrl = url.lowercase().trim()
        val domain = extractDomain(lowerUrl)

        val platform = when {
            domain.contains("youtube.com") || domain.contains("youtu.be") || domain.contains("youtube-nocookie.com") -> Platform.YOUTUBE
            domain.contains("instagram.com") || domain.contains("instagr.am") -> Platform.INSTAGRAM
            domain.contains("facebook.com") || domain.contains("fb.watch") || domain.contains("fb.com") || domain.contains("fb.me") -> Platform.FACEBOOK
            domain.contains("twitter.com") || domain.contains("x.com") || domain.contains("t.co") || domain.contains("vxtwitter.com") || domain.contains("fxtwitter.com") -> Platform.TWITTER
            domain.contains("tiktok.com") || domain.contains("douyin.com") -> Platform.TIKTOK
            domain.contains("reddit.com") || domain.contains("redd.it") -> Platform.REDDIT
            domain.contains("twitch.tv") -> Platform.TWITCH
            domain.contains("vimeo.com") -> Platform.VIMEO
            domain.contains("soundcloud.com") -> Platform.SOUNDCLOUD
            domain.contains("dailymotion.com") || domain.contains("dai.ly") -> Platform.DAILYMOTION
            domain.contains("bilibili.com") || domain.contains("b23.tv") -> Platform.BILIBILI
            domain.contains("pinterest.com") || domain.contains("pin.it") -> Platform.PINTEREST
            domain.contains("linkedin.com") || domain.contains("lnkd.in") -> Platform.LINKEDIN
            domain.contains("snapchat.com") -> Platform.SNAPCHAT
            domain.contains("threads.net") -> Platform.INSTAGRAM
            lowerUrl.startsWith("http://") || lowerUrl.startsWith("https://") -> Platform.GENERIC
            else -> Platform.UNKNOWN
        }

        Timber.tag("MediaVaultDebug").d("Dominio '$domain' detectado como plataforma: $platform")
        return platform
    }

    fun extractDomain(url: String): String {
        return try {
            val uri = URI(if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url)
            uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.")?.removePrefix("mobile.") ?: url
        } catch (e: Exception) {
            url
        }
    }

    fun isPlaylistUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return (lowerUrl.contains("youtube.com/playlist") || lowerUrl.contains("&list="))
    }

    fun normalizeUrl(url: String): String {
        return try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: ""
            val query = uri.query

            if (host.contains("youtube.com") || host.contains("youtu.be")) {
                if (query != null) {
                    val params = query.split("&").filter { it.startsWith("v=") || it.startsWith("list=") }
                    val newQuery = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
                    "${uri.scheme}://${uri.host}${uri.path}$newQuery"
                } else {
                    url
                }
            } else {
                val index = url.indexOf("?")
                if (index != -1 && (url.contains("utm_") || url.contains("fbclid") || url.contains("igshid") || url.contains("tracking"))) {
                    url.substring(0, index)
                } else {
                    url.trim()
                }
            }
        } catch (e: Exception) {
            url.trim()
        }
    }
}
