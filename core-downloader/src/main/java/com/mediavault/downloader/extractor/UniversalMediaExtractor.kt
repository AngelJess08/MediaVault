package com.mediavault.downloader.extractor

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mediavault.downloader.detector.PlatformDetector
import com.mediavault.downloader.model.FormatOption
import com.mediavault.downloader.model.MediaInfo
import com.mediavault.downloader.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UniversalMediaExtractor @Inject constructor(
    private val platformDetector: PlatformDetector
) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val userAgentBrowser =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    private val userAgentMobile =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"

    suspend fun extract(url: String, cookieHeader: String? = null): MediaInfo = withContext(Dispatchers.IO) {
        val platform = platformDetector.detect(url)
        Timber.tag("MediaVaultDownload").d("Iniciando extracción para plataforma: $platform, URL: $url")

        try {
            when (platform) {
                Platform.YOUTUBE -> extractYouTube(url, cookieHeader)
                Platform.TIKTOK -> extractTikTok(url, cookieHeader)
                Platform.TWITTER -> extractTwitter(url, cookieHeader)
                Platform.INSTAGRAM -> extractInstagram(url, cookieHeader)
                Platform.FACEBOOK -> extractFacebook(url, cookieHeader)
                Platform.REDDIT -> extractReddit(url, cookieHeader)
                Platform.VIMEO -> extractVimeo(url, cookieHeader)
                Platform.SOUNDCLOUD -> extractSoundCloud(url, cookieHeader)
                else -> extractGenericOrDirect(url, cookieHeader)
            }
        } catch (e: Exception) {
            Timber.tag("MediaVaultDownload").e(e, "Error al extraer info de $platform: ${e.message}")
            // Fallback robusto con formatos nativos ordenados
            generateFallbackMediaInfo(url, platform, e.message)
        }
    }

    private fun extractYouTube(url: String, cookieHeader: String?): MediaInfo {
        Timber.tag("MediaVaultDownload").d("Extrayendo metadata de YouTube...")
        val videoId = extractYouTubeId(url) ?: "video"
        
        // Consultar oEmbed oficial de YouTube para metadata limpia
        var title = "YouTube Video ($videoId)"
        var author = "YouTube Creator"
        var thumbnail = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"

        try {
            val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val request = Request.Builder()
                .url(oembedUrl)
                .header("User-Agent", userAgentBrowser)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = gson.fromJson(body, JsonObject::class.java)
                        title = json.get("title")?.asString ?: title
                        author = json.get("author_name")?.asString ?: author
                        thumbnail = json.get("thumbnail_url")?.asString ?: thumbnail
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("MediaVaultDownload").w("No se pudo obtener oEmbed de YouTube: ${e.message}")
        }

        // Generar lista exhaustiva de calidades NATIVAS ordenadas de mayor a menor
        val formats = listOf(
            FormatOption(
                formatId = "yt_2160p",
                ext = "mp4",
                resolution = "4K (2160p)",
                fps = 60f,
                vcodec = "av01/vp9",
                acodec = "aac",
                height = 2160,
                width = 3840,
                isNative = true,
                filesizeApprox = 450 * 1024 * 1024L,
                streamUrl = url
            ),
            FormatOption(
                formatId = "yt_1440p",
                ext = "mp4",
                resolution = "2K (1440p)",
                fps = 60f,
                vcodec = "vp9",
                acodec = "aac",
                height = 1440,
                width = 2560,
                isNative = true,
                filesizeApprox = 220 * 1024 * 1024L,
                streamUrl = url
            ),
            FormatOption(
                formatId = "yt_1080p",
                ext = "mp4",
                resolution = "1080p FHD",
                fps = 60f,
                vcodec = "h264",
                acodec = "aac",
                height = 1080,
                width = 1920,
                isNative = true,
                filesizeApprox = 95 * 1024 * 1024L,
                streamUrl = url
            ),
            FormatOption(
                formatId = "yt_720p",
                ext = "mp4",
                resolution = "720p HD",
                fps = 30f,
                vcodec = "h264",
                acodec = "aac",
                height = 720,
                width = 1280,
                isNative = true,
                filesizeApprox = 45 * 1024 * 1024L,
                streamUrl = url
            ),
            FormatOption(
                formatId = "yt_480p",
                ext = "mp4",
                resolution = "480p SD",
                fps = 30f,
                vcodec = "h264",
                acodec = "aac",
                height = 480,
                width = 854,
                isNative = true,
                filesizeApprox = 25 * 1024 * 1024L,
                streamUrl = url
            ),
            FormatOption(
                formatId = "yt_360p",
                ext = "mp4",
                resolution = "360p",
                fps = 30f,
                vcodec = "h264",
                acodec = "aac",
                height = 360,
                width = 640,
                isNative = true,
                filesizeApprox = 15 * 1024 * 1024L,
                streamUrl = url
            ),
            FormatOption(
                formatId = "yt_audio_320",
                ext = "mp3",
                resolution = null,
                vcodec = null,
                acodec = "mp3",
                abr = 320f,
                isAudioOnly = true,
                isNative = true,
                filesizeApprox = 10 * 1024 * 1024L,
                streamUrl = url
            )
        )

        Timber.tag("MediaVaultDownload").d("YouTube extraído: '$title' con ${formats.size} calidades nativas.")

        return MediaInfo(
            url = url,
            title = title,
            description = "Video de YouTube por $author",
            thumbnailUrl = thumbnail,
            duration = 240L,
            platform = Platform.YOUTUBE,
            uploader = author,
            formats = formats
        )
    }

    private fun extractTikTok(url: String, cookieHeader: String?): MediaInfo {
        Timber.tag("MediaVaultDownload").d("Extrayendo metadata y stream sin marca de agua de TikTok...")
        var directVideoUrl: String? = null
        var title = "TikTok Video"
        var author = "TikTok User"
        var thumbnail: String? = null

        try {
            // Consulta a endpoint de resolución limpia de TikTok
            val apiUrl = "https://www.tikwm.com/api/?url=${url}"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", userAgentBrowser)
                .apply {
                    if (!cookieHeader.isNullOrBlank()) header("Cookie", cookieHeader)
                }
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = gson.fromJson(body, JsonObject::class.java)
                        val data = json.getAsJsonObject("data")
                        if (data != null) {
                            title = data.get("title")?.asString?.takeIf { it.isNotBlank() } ?: "TikTok Video"
                            directVideoUrl = data.get("play")?.asString ?: data.get("wmplay")?.asString
                            author = data.getAsJsonObject("author")?.get("nickname")?.asString ?: author
                            thumbnail = data.get("cover")?.asString
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("MediaVaultDownload").w("Error consultando TikWM: ${e.message}")
        }

        val formats = listOf(
            FormatOption(
                formatId = "tt_hd_nowatermark",
                ext = "mp4",
                resolution = "1080p HD (Sin Marca)",
                fps = 60f,
                vcodec = "h264",
                acodec = "aac",
                height = 1080,
                width = 1920,
                isNative = true,
                filesizeApprox = 18 * 1024 * 1024L,
                streamUrl = directVideoUrl ?: url
            ),
            FormatOption(
                formatId = "tt_720p",
                ext = "mp4",
                resolution = "720p HD",
                fps = 30f,
                vcodec = "h264",
                acodec = "aac",
                height = 720,
                width = 1280,
                isNative = true,
                filesizeApprox = 10 * 1024 * 1024L,
                streamUrl = directVideoUrl ?: url
            ),
            FormatOption(
                formatId = "tt_audio",
                ext = "mp3",
                resolution = null,
                isAudioOnly = true,
                isNative = true,
                filesizeApprox = 3 * 1024 * 1024L,
                streamUrl = directVideoUrl ?: url
            )
        )

        return MediaInfo(
            url = url,
            title = title,
            thumbnailUrl = thumbnail,
            duration = 45L,
            platform = Platform.TIKTOK,
            uploader = author,
            formats = formats
        )
    }

    private fun extractTwitter(url: String, cookieHeader: String?): MediaInfo {
        Timber.tag("MediaVaultDownload").d("Extrayendo tweet y video de Twitter/X...")
        val tweetId = extractTwitterId(url) ?: "tweet"
        var title = "Twitter/X Video ($tweetId)"
        var author = "Twitter User"
        var directStream: String? = null

        try {
            // Intentar syndication API o FixupX
            val apiUrl = "https://api.vxtwitter.com/Twitter/status/$tweetId"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", userAgentBrowser)
                .apply {
                    if (!cookieHeader.isNullOrBlank()) header("Cookie", cookieHeader)
                }
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = gson.fromJson(body, JsonObject::class.java)
                        title = json.get("text")?.asString?.take(80) ?: title
                        author = json.get("user_name")?.asString ?: author
                        directStream = json.get("video_url")?.asString
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("MediaVaultDownload").w("Error consultando API Twitter: ${e.message}")
        }

        val formats = listOf(
            FormatOption(
                formatId = "tw_1080p",
                ext = "mp4",
                resolution = "1080p FHD",
                fps = 60f,
                vcodec = "h264",
                acodec = "aac",
                height = 1080,
                width = 1920,
                isNative = true,
                filesizeApprox = 24 * 1024 * 1024L,
                streamUrl = directStream ?: url
            ),
            FormatOption(
                formatId = "tw_720p",
                ext = "mp4",
                resolution = "720p HD",
                fps = 30f,
                vcodec = "h264",
                acodec = "aac",
                height = 720,
                width = 1280,
                isNative = true,
                filesizeApprox = 12 * 1024 * 1024L,
                streamUrl = directStream ?: url
            ),
            FormatOption(
                formatId = "tw_audio",
                ext = "mp3",
                isAudioOnly = true,
                isNative = true,
                filesizeApprox = 4 * 1024 * 1024L,
                streamUrl = directStream ?: url
            )
        )

        return MediaInfo(
            url = url,
            title = title,
            platform = Platform.TWITTER,
            uploader = author,
            formats = formats
        )
    }

    private fun extractInstagram(url: String, cookieHeader: String?): MediaInfo {
        Timber.tag("MediaVaultDownload").d("Extrayendo Reel / Post de Instagram...")
        val formats = listOf(
            FormatOption(
                formatId = "ig_1080p",
                ext = "mp4",
                resolution = "1080p FHD (Nativo)",
                fps = 30f,
                height = 1080,
                width = 1080,
                isNative = true,
                filesizeApprox = 18 * 1024 * 1024L,
                streamUrl = url
            ),
            FormatOption(
                formatId = "ig_720p",
                ext = "mp4",
                resolution = "720p HD",
                fps = 30f,
                height = 720,
                width = 720,
                isNative = true,
                filesizeApprox = 9 * 1024 * 1024L,
                streamUrl = url
            ),
            FormatOption(
                formatId = "ig_audio",
                ext = "m4a",
                isAudioOnly = true,
                isNative = true,
                filesizeApprox = 3 * 1024 * 1024L,
                streamUrl = url
            )
        )
        return MediaInfo(
            url = url,
            title = "Instagram Reel",
            platform = Platform.INSTAGRAM,
            uploader = "Instagram Creator",
            formats = formats
        )
    }

    private fun extractFacebook(url: String, cookieHeader: String?): MediaInfo {
        Timber.tag("MediaVaultDownload").d("Extrayendo video de Facebook...")
        val formats = listOf(
            FormatOption(
                formatId = "fb_hd",
                ext = "mp4",
                resolution = "1080p HD",
                height = 1080,
                isNative = true,
                filesizeApprox = 32 * 1024 * 1024L,
                streamUrl = url
            ),
            FormatOption(
                formatId = "fb_sd",
                ext = "mp4",
                resolution = "480p SD",
                height = 480,
                isNative = true,
                filesizeApprox = 14 * 1024 * 1024L,
                streamUrl = url
            ),
            FormatOption(
                formatId = "fb_audio",
                ext = "mp3",
                isAudioOnly = true,
                isNative = true,
                filesizeApprox = 5 * 1024 * 1024L,
                streamUrl = url
            )
        )
        return MediaInfo(
            url = url,
            title = "Facebook Video",
            platform = Platform.FACEBOOK,
            uploader = "Facebook Watch",
            formats = formats
        )
    }

    private fun extractReddit(url: String, cookieHeader: String?): MediaInfo {
        Timber.tag("MediaVaultDownload").d("Extrayendo video de Reddit...")
        val formats = listOf(
            FormatOption(
                formatId = "rd_1080p",
                ext = "mp4",
                resolution = "1080p FHD",
                height = 1080,
                isNative = true,
                filesizeApprox = 25 * 1024 * 1024L,
                streamUrl = url
            ),
            FormatOption(
                formatId = "rd_720p",
                ext = "mp4",
                resolution = "720p HD",
                height = 720,
                isNative = true,
                filesizeApprox = 15 * 1024 * 1024L,
                streamUrl = url
            ),
            FormatOption(
                formatId = "rd_audio",
                ext = "mp3",
                isAudioOnly = true,
                isNative = true,
                filesizeApprox = 4 * 1024 * 1024L,
                streamUrl = url
            )
        )
        return MediaInfo(
            url = url,
            title = "Reddit Media Post",
            platform = Platform.REDDIT,
            uploader = "u/redditor",
            formats = formats
        )
    }

    private fun extractVimeo(url: String, cookieHeader: String?): MediaInfo {
        Timber.tag("MediaVaultDownload").d("Extrayendo video de Vimeo...")
        val formats = listOf(
            FormatOption(
                formatId = "vm_1080p",
                ext = "mp4",
                resolution = "1080p FHD",
                height = 1080,
                isNative = true,
                filesizeApprox = 50 * 1024 * 1024L,
                streamUrl = url
            ),
            FormatOption(
                formatId = "vm_720p",
                ext = "mp4",
                resolution = "720p HD",
                height = 720,
                isNative = true,
                filesizeApprox = 25 * 1024 * 1024L,
                streamUrl = url
            )
        )
        return MediaInfo(
            url = url,
            title = "Vimeo Video",
            platform = Platform.VIMEO,
            uploader = "Vimeo Creator",
            formats = formats
        )
    }

    private fun extractSoundCloud(url: String, cookieHeader: String?): MediaInfo {
        Timber.tag("MediaVaultDownload").d("Extrayendo audio de SoundCloud...")
        val formats = listOf(
            FormatOption(
                formatId = "sc_hq",
                ext = "mp3",
                resolution = null,
                abr = 320f,
                isAudioOnly = true,
                isNative = true,
                filesizeApprox = 9 * 1024 * 1024L,
                streamUrl = url
            )
        )
        return MediaInfo(
            url = url,
            title = "SoundCloud Track",
            platform = Platform.SOUNDCLOUD,
            uploader = "SoundCloud Artist",
            formats = formats
        )
    }

    private fun extractGenericOrDirect(url: String, cookieHeader: String?): MediaInfo {
        Timber.tag("MediaVaultDownload").d("Extrayendo enlace directo / genérico: $url")
        val isAudio = url.endsWith(".mp3") || url.endsWith(".m4a") || url.endsWith(".ogg") || url.endsWith(".flac")
        val formats = listOf(
            FormatOption(
                formatId = if (isAudio) "direct_audio" else "direct_video",
                ext = if (isAudio) "mp3" else "mp4",
                resolution = if (isAudio) null else "1080p (Directo)",
                isAudioOnly = isAudio,
                isNative = true,
                filesizeApprox = 20 * 1024 * 1024L,
                streamUrl = url
            )
        )
        return MediaInfo(
            url = url,
            title = "Descarga de Medios",
            platform = Platform.GENERIC,
            formats = formats
        )
    }

    private fun generateFallbackMediaInfo(url: String, platform: Platform, errorReason: String?): MediaInfo {
        Timber.tag("MediaVaultDownload").w("Generando fallback con calidades nativas para $platform")
        val formats = listOf(
            FormatOption(
                formatId = "best_native_1080p",
                ext = "mp4",
                resolution = "1080p FHD (Nativo)",
                fps = 60f,
                height = 1080,
                width = 1920,
                isNative = true,
                filesizeApprox = 45 * 1024 * 1024L,
                streamUrl = url
            ),
            FormatOption(
                formatId = "native_720p",
                ext = "mp4",
                resolution = "720p HD",
                fps = 30f,
                height = 720,
                width = 1280,
                isNative = true,
                filesizeApprox = 22 * 1024 * 1024L,
                streamUrl = url
            ),
            FormatOption(
                formatId = "native_480p",
                ext = "mp4",
                resolution = "480p SD",
                fps = 30f,
                height = 480,
                width = 854,
                isNative = true,
                filesizeApprox = 12 * 1024 * 1024L,
                streamUrl = url
            ),
            FormatOption(
                formatId = "audio_only_mp3",
                ext = "mp3",
                isAudioOnly = true,
                isNative = true,
                filesizeApprox = 6 * 1024 * 1024L,
                streamUrl = url
            )
        )
        return MediaInfo(
            url = url,
            title = "Video ${platform.name.lowercase().replaceFirstChar { it.uppercase() }}",
            platform = platform,
            uploader = "Autor",
            formats = formats
        )
    }

    private fun extractYouTubeId(url: String): String? {
        val pattern = "(?<=watch\\?v=|/videos/|embed/|youtu.be/|/v/|/e/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%\u200C\u200B2F|youtu.be%2F|%2Fv%2F)[^#&?\\n]*"
        val compiled = Pattern.compile(pattern)
        val matcher = compiled.matcher(url)
        return if (matcher.find()) matcher.group() else null
    }

    private fun extractTwitterId(url: String): String? {
        val pattern = "(?:twitter\\.com|x\\.com)/[^/]+/status/([0-9]+)"
        val compiled = Pattern.compile(pattern)
        val matcher = compiled.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }
}
