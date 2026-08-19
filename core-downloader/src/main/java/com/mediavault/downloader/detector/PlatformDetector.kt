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
     * Sigue todas las redirecciones HTTP (3xx) y redirecciones cliente en HTML (meta-refresh o window.location/location.replace)
     * para obtener la URL final real de destino antes de detectar y extraer la plataforma.
     */
    open suspend fun resolveRedirects(url: String, depth: Int = 0): String = withContext(Dispatchers.IO) {
        if (depth > 5) return@withContext url // Evitar loops infinitos
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return@withContext trimmed
        }

        Timber.tag("MediaVaultDebug").d("Resolviendo redirecciones (nivel $depth) para: $trimmed")
        var currentResolvedUrl = trimmed

        // 1. Redirecciones HTTP vía HEAD / GET
        try {
            val request = Request.Builder()
                .url(trimmed)
                .header("User-Agent", userAgent)
                .head()
                .build()

            httpClient.newCall(request).execute().use { response ->
                currentResolvedUrl = response.request.url.toString()
                Timber.tag("MediaVaultDebug").d("URL resuelta vía HEAD: $currentResolvedUrl (HTTP ${response.code})")
            }
        } catch (e: Exception) {
            try {
                val getRequest = Request.Builder()
                    .url(trimmed)
                    .header("User-Agent", userAgent)
                    .get()
                    .build()

                httpClient.newCall(getRequest).execute().use { response ->
                    currentResolvedUrl = response.request.url.toString()
                    Timber.tag("MediaVaultDebug").d("URL resuelta vía GET: $currentResolvedUrl (HTTP ${response.code})")
                }
            } catch (e2: Exception) {
                Timber.tag("MediaVaultDebug").w("No se pudieron resolver redirecciones HTTP de $trimmed: ${e2.message}")
            }
        }

        // 2. Si la plataforma aún no es reconocida o el dominio no cambió, inspeccionar el HTML en busca de redirección JS o <meta http-equiv="refresh">
        val initialPlatform = detect(currentResolvedUrl)
        if (initialPlatform == Platform.UNKNOWN || initialPlatform == Platform.GENERIC) {
            try {
                Timber.tag("MediaVaultDebug").d("Inspeccionando HTML de $currentResolvedUrl en busca de redirecciones cliente (Meta-Refresh / JavaScript)...")
                val htmlRequest = Request.Builder()
                    .url(currentResolvedUrl)
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .get()
                    .build()

                httpClient.newCall(htmlRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val byteStream = response.body?.byteStream()
                        if (byteStream != null) {
                            val buffer = ByteArray(64 * 1024) // Primeros 64KB
                            val bytesRead = byteStream.read(buffer)
                            if (bytesRead > 0) {
                                val htmlSnippet = String(buffer, 0, bytesRead, Charsets.UTF_8)
                                val clientRedirectUrl = extractClientRedirect(htmlSnippet, currentResolvedUrl)
                                if (!clientRedirectUrl.isNullOrBlank() && !clientRedirectUrl.equals(currentResolvedUrl, ignoreCase = true)) {
                                    Timber.tag("MediaVaultDebug").i("¡Redirección cliente/JS detectada hacia!: $clientRedirectUrl")
                                    return@withContext resolveRedirects(clientRedirectUrl, depth + 1)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag("MediaVaultDebug").w("Error al inspeccionar HTML de redirección cliente para $currentResolvedUrl: ${e.message}")
            }
        }

        return@withContext currentResolvedUrl
    }

    /**
     * Extrae redirecciones de tipo:
     * - <meta http-equiv="refresh" content="...url=https://...">
     * - window.location = "https://..." o window.location.href = "https://..."
     * - window.location.replace("https://...") o location.replace("https://...")
     */
    fun extractClientRedirect(html: String, baseUrl: String): String? {
        val metaPattern = java.util.regex.Pattern.compile("<meta[^>]*http-equiv\\s*=\\s*[\"']?refresh[\"']?[^>]*content\\s*=\\s*[\"'][^\"']*url=([^\"'>\\s]+)[\"']?", java.util.regex.Pattern.CASE_INSENSITIVE)
        val metaMatcher = metaPattern.matcher(html)
        if (metaMatcher.find()) {
            val raw = metaMatcher.group(1)?.trim()?.removePrefix("'")?.removePrefix("\"")
            if (!raw.isNullOrBlank()) {
                return resolveRelativeUrl(raw, baseUrl)
            }
        }

        val jsPattern = java.util.regex.Pattern.compile("(?:window\\.)?location(?:\\.href|\\.replace)?\\s*(?:=|\\()\\s*[\"']([^\"']+)[\"']\\s*\\)?", java.util.regex.Pattern.CASE_INSENSITIVE)
        val jsMatcher = jsPattern.matcher(html)
        if (jsMatcher.find()) {
            val raw = jsMatcher.group(1)?.trim()
            if (!raw.isNullOrBlank() && (raw.startsWith("http://") || raw.startsWith("https://") || raw.startsWith("/"))) {
                return resolveRelativeUrl(raw, baseUrl)
            }
        }

        val canonicalPattern = java.util.regex.Pattern.compile("<link[^>]*rel\\s*=\\s*[\"']canonical[\"'][^>]*href\\s*=\\s*[\"']([^\"']+)[\"']", java.util.regex.Pattern.CASE_INSENSITIVE)
        val canonicalMatcher = canonicalPattern.matcher(html)
        if (canonicalMatcher.find()) {
            val raw = canonicalMatcher.group(1)?.trim()
            if (!raw.isNullOrBlank() && !raw.equals(baseUrl, ignoreCase = true)) {
                return resolveRelativeUrl(raw, baseUrl)
            }
        }

        return null
    }

    private fun resolveRelativeUrl(url: String, baseUrl: String): String {
        return try {
            val baseUri = URI(baseUrl)
            baseUri.resolve(url).toString()
        } catch (e: Exception) {
            url
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
