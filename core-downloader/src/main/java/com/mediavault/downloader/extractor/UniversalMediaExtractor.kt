package com.mediavault.downloader.extractor

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mediavault.downloader.detector.PlatformDetector
import com.mediavault.downloader.model.FormatOption
import com.mediavault.downloader.model.MediaInfo
import com.mediavault.downloader.model.Platform
import com.mediavault.downloader.universal.UniversalWebViewSniffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UniversalMediaExtractor @Inject constructor(
    private val platformDetector: PlatformDetector,
    private val universalWebViewSniffer: UniversalWebViewSniffer
) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val fastInstanceClient = OkHttpClient.Builder()
        .connectTimeout(3500, TimeUnit.MILLISECONDS)
        .readTimeout(3500, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val userAgentBrowser =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    private val userAgentMobile =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"

    suspend fun extract(
        rawUrl: String,
        cookieHeader: String? = null,
        onStatusUpdate: ((String) -> Unit)? = null
    ): MediaInfo = withContext(Dispatchers.IO) {
        Timber.tag("MediaVaultDebug").d("==================================================")
        Timber.tag("MediaVaultDebug").d("Paso 1: URL recibida -> $rawUrl")

        // 1. Resolver redirecciones de URLs acortadas
        val resolvedUrl = platformDetector.resolveRedirects(rawUrl)
        val platform = platformDetector.detect(resolvedUrl)
        Timber.tag("MediaVaultDebug").d("Paso 2: Dominio y plataforma resueltos -> $platform para $resolvedUrl")

        // 2. Si la plataforma es conocida, intentar primero el extractor especializado
        if (platform != Platform.UNKNOWN && platform != Platform.GENERIC) {
            Timber.tag("MediaVaultDebug").d("Paso 3: Intentando extractor especializado para $platform...")
            try {
                val mediaInfo = when (platform) {
                    Platform.YOUTUBE -> extractYouTube(resolvedUrl, cookieHeader)
                    Platform.TIKTOK -> extractTikTok(resolvedUrl, cookieHeader)
                    Platform.TWITTER -> extractTwitter(resolvedUrl, cookieHeader)
                    Platform.INSTAGRAM -> extractInstagram(resolvedUrl, cookieHeader)
                    Platform.FACEBOOK -> extractFacebook(resolvedUrl, cookieHeader)
                    Platform.REDDIT -> extractReddit(resolvedUrl, cookieHeader)
                    Platform.VIMEO -> extractVimeo(resolvedUrl, cookieHeader)
                    Platform.SOUNDCLOUD -> extractSoundCloud(resolvedUrl, cookieHeader)
                    else -> null
                }

                if (mediaInfo != null && mediaInfo.formats.isNotEmpty()) {
                    Timber.tag("MediaVaultDebug").d("Paso 4: Extracción especializada exitosa. Título: '${mediaInfo.title}', Formatos: ${mediaInfo.formats.size}, Resuelto por: ${mediaInfo.resolvedByInstance ?: "Nativo"}")
                    mediaInfo.formats.forEach { fmt ->
                        Timber.tag("MediaVaultDebug").d("   -> Formato: ${fmt.resolution ?: fmt.formatId} (${fmt.ext}), Stream: ${fmt.streamUrl?.take(60)}...")
                    }
                    return@withContext mediaInfo
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.tag("MediaVaultDebug").w("Extractor especializado de $platform falló: ${e.message}. Activando respaldo con Modo Universal...")
            }
        }

        // 3. Verificar si es un enlace directo a archivo multimedia (.mp4, .mp3, .m3u8, etc.)
        val lowerUrl = resolvedUrl.lowercase()
        val isDirectMediaFile = lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".webm") ||
                lowerUrl.endsWith(".mp3") || lowerUrl.endsWith(".m4a") ||
                lowerUrl.endsWith(".m3u8") || lowerUrl.endsWith(".ts") ||
                lowerUrl.endsWith(".wav") || lowerUrl.endsWith(".ogg") || lowerUrl.endsWith(".flac")

        if (isDirectMediaFile) {
            Timber.tag("MediaVaultDebug").d("Enlace directo a archivo multimedia detectado.")
            return@withContext extractGenericOrDirect(resolvedUrl, cookieHeader)
        }

        // 4. MODO UNIVERSAL (WebView Sniffer de Respaldo)
        Timber.tag("MediaVaultUniversalMode").d("Activando Modo Universal de respaldo para: $resolvedUrl")
        onStatusUpdate?.invoke("No reconocí este sitio directamente, buscando el video de otra forma...")

        try {
            return@withContext extractWithUniversalSniffer(resolvedUrl, cookieHeader, platform)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.tag("MediaVaultUniversalMode").e(e, "Modo Universal finalizó con error para $resolvedUrl: ${e.message}")
            throw e
        }
    }

    private suspend fun extractWithUniversalSniffer(
        url: String,
        cookieHeader: String?,
        platform: Platform
    ): MediaInfo {
        val candidates = universalWebViewSniffer.sniff(url, cookieHeader)

        val formats = candidates.mapIndexed { index, candidate ->
            FormatOption(
                formatId = "univ_${candidate.extension}_$index",
                ext = candidate.extension,
                resolution = candidate.estimatedResolution ?: if (candidate.isAudioOnly) "Audio (${candidate.extension})" else "Video Web (${candidate.extension})",
                isAudioOnly = candidate.isAudioOnly,
                isNative = true,
                filesizeApprox = candidate.contentLength ?: if (candidate.isAudioOnly) (5 * 1024 * 1024L) else (25 * 1024 * 1024L),
                streamUrl = candidate.url
            )
        }

        val title = candidates.firstOrNull()?.title ?: "Video Web Descubierto"
        val author = platformDetector.extractDomain(url)

        return MediaInfo(
            url = url,
            title = title,
            platform = if (platform != Platform.UNKNOWN) platform else Platform.GENERIC,
            uploader = author,
            formats = formats,
            resolvedByInstance = "Universal WebView Sniffer"
        )
    }

    // ==========================================
    // YOUTUBE EXTRACTOR (Piped / Invidious / Cobalt / oEmbed)
    // ==========================================
    private fun extractYouTube(url: String, cookieHeader: String?): MediaInfo {
        val videoId = extractYouTubeId(url) ?: throw Exception("No se pudo identificar el ID del video de YouTube.")
        Timber.tag("MediaVaultDebug").d("YouTube ID detectado: $videoId")

        var title = "Video de YouTube"
        var author = "YouTube Creator"
        var thumbnail = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
        var duration = 0L
        val formats = mutableListOf<FormatOption>()
        var successfulInstance: String? = null

        // 1. Obtener metadata básica de oEmbed
        try {
            val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val request = Request.Builder().url(oembedUrl).header("User-Agent", userAgentBrowser).build()
            fastInstanceClient.newCall(request).execute().use { response ->
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
            Timber.tag("MediaVaultDebug").w("oEmbed fallback: ${e.message}")
        }

        // 2. Pool de instancias públicas activas de Piped e Invidious (TeamPiped docs / instances.invidious.io)
        val pipedInstances = listOf(
            "https://piped-api.privacy.com.de/streams/$videoId",
            "https://pipedapi.kavin.rocks/streams/$videoId",
            "https://api.piped.projectsegfau.lt/streams/$videoId",
            "https://pipedapi.in.projectsegfau.lt/streams/$videoId",
            "https://pipedapi.leptons.xyz/streams/$videoId",
            "https://pipedapi.tokhmi.xyz/streams/$videoId",
            "https://piped-api.garudalinux.org/streams/$videoId",
            "https://pipedapi.ducks.party/streams/$videoId",
            "https://pipedapi.r4fo.com/streams/$videoId",
            "https://inv.tux.pizza/api/v1/videos/$videoId",
            "https://invidious.nerdvpn.de/api/v1/videos/$videoId",
            "https://invidious.drgns.space/api/v1/videos/$videoId",
            "https://invidious.no-valis.be/api/v1/videos/$videoId",
            "https://yewtu.be/api/v1/videos/$videoId",
            "https://vid.puffyan.us/api/v1/videos/$videoId",
            "https://invidious.lunar.icu/api/v1/videos/$videoId"
        )

        for (pipedUrl in pipedInstances) {
            try {
                Timber.tag("MediaVaultDebug").d("Consultando API de stream YouTube: $pipedUrl (Timeout 3.5s)...")
                val request = Request.Builder()
                    .url(pipedUrl)
                    .header("User-Agent", userAgentBrowser)
                    .build()

                fastInstanceClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val parsedElement = try {
                                com.google.gson.JsonParser.parseString(body)
                            } catch (e: Exception) {
                                null
                            }
                            if (parsedElement == null || !parsedElement.isJsonObject) {
                                Timber.tag("MediaVaultDebug").w("Respuesta de $pipedUrl no es un objeto JSON válido.")
                                return@use
                            }
                            val json = parsedElement.asJsonObject
                            title = json.get("title")?.asString ?: title
                            author = json.get("uploader")?.asString ?: json.get("author")?.asString ?: author
                            duration = json.get("duration")?.asLong ?: json.get("lengthSeconds")?.asLong ?: duration
                            thumbnail = json.get("thumbnailUrl")?.asString ?: thumbnail

                            // 2.1 Streams de Piped (videoStreams y audioStreams)
                            val videoStreams = json.getAsJsonArray("videoStreams")
                            val audioStreams = json.getAsJsonArray("audioStreams")

                            if (videoStreams != null && videoStreams.size() > 0) {
                                for (elem in videoStreams) {
                                    val obj = elem.asJsonObject
                                    val streamUrl = obj.get("url")?.asString ?: continue
                                    val quality = obj.get("quality")?.asString ?: "Video"
                                    val height = obj.get("height")?.asInt ?: 1080
                                    val fps = obj.get("fps")?.asFloat ?: 30f
                                    val ext = obj.get("format")?.asString ?: "mp4"
                                    val codec = obj.get("codec")?.asString ?: "h264"
                                    val filesize = obj.get("contentLength")?.asLong ?: (height * 100000L)

                                    formats.add(
                                        FormatOption(
                                            formatId = "yt_${height}p_${ext}",
                                            ext = if (ext.contains("mp4", ignoreCase = true)) "mp4" else "webm",
                                            resolution = "$quality ($ext)",
                                            fps = fps,
                                            height = height,
                                            width = (height * 16) / 9,
                                            vcodec = codec,
                                            isNative = true,
                                            filesizeApprox = filesize,
                                            streamUrl = streamUrl
                                        )
                                    )
                                }
                            }

                            if (audioStreams != null && audioStreams.size() > 0) {
                                for (elem in audioStreams) {
                                    val obj = elem.asJsonObject
                                    val streamUrl = obj.get("url")?.asString ?: continue
                                    val bitrate = obj.get("bitrate")?.asInt ?: 128
                                    val ext = obj.get("format")?.asString ?: "m4a"

                                    formats.add(
                                        FormatOption(
                                            formatId = "yt_audio_${bitrate}",
                                            ext = "mp3",
                                            resolution = null,
                                            isAudioOnly = true,
                                            abr = (bitrate / 1000).toFloat(),
                                            isNative = true,
                                            filesizeApprox = 5 * 1024 * 1024L,
                                            streamUrl = streamUrl
                                        )
                                    )
                                }
                            }

                            // 2.2 Streams de Invidious (formatStreams y adaptiveFormats)
                            val formatStreams = json.getAsJsonArray("formatStreams")
                            if (formatStreams != null && formatStreams.size() > 0) {
                                for (elem in formatStreams) {
                                    val obj = elem.asJsonObject
                                    val streamUrl = obj.get("url")?.asString ?: continue
                                    val resolution = obj.get("resolution")?.asString ?: obj.get("qualityLabel")?.asString ?: "720p"
                                    val container = obj.get("container")?.asString ?: "mp4"
                                    val size = obj.get("size")?.asLong ?: (15 * 1024 * 1024L)

                                    formats.add(
                                        FormatOption(
                                            formatId = "invidious_${resolution}_${container}",
                                            ext = if (container.contains("mp4", ignoreCase = true)) "mp4" else "webm",
                                            resolution = "$resolution ($container)",
                                            isNative = true,
                                            filesizeApprox = size,
                                            streamUrl = streamUrl
                                        )
                                    )
                                }
                            }

                            if (formats.isNotEmpty()) {
                                successfulInstance = pipedUrl
                                Timber.tag("MediaVaultDebug").i("Streams de YouTube obtenidos con éxito de $pipedUrl (${formats.size} formatos)")
                            }
                        }
                    } else {
                        Timber.tag("MediaVaultDebug").w("Instancia $pipedUrl respondió con HTTP ${response.code}")
                    }
                }
                if (formats.isNotEmpty()) {
                    break
                }
            } catch (e: Exception) {
                Timber.tag("MediaVaultDebug").w("Instancia $pipedUrl falló o expiró timeout: ${e.message}")
            }
        }

        // 3. Fallback con Cobalt API
        // NOTA DE SEGURIDAD / ARQUITECTURA: La instancia pública api.cobalt.tools NO es viable de forma directa
        // debido a protecciones anti-bot Turnstile y bloqueos activos de YouTube durante 2026.
        // Se recomienda autohospedar una instancia de Cobalt en server-backend (imputnet/cobalt).
        if (formats.isEmpty()) {
            try {
                Timber.tag("MediaVaultDebug").d("Intentando Cobalt API de respaldo...")
                val cobaltJson = JsonObject().apply {
                    addProperty("url", "https://www.youtube.com/watch?v=$videoId")
                    addProperty("videoQuality", "max")
                }
                val body = cobaltJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://api.cobalt.tools/")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", userAgentBrowser)
                    .post(body)
                    .build()

                fastInstanceClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val respBody = response.body?.string()
                        if (!respBody.isNullOrBlank()) {
                            val json = gson.fromJson(respBody, JsonObject::class.java)
                            val streamUrl = json.get("url")?.asString
                            if (!streamUrl.isNullOrBlank()) {
                                successfulInstance = "https://api.cobalt.tools (Cobalt Fallback)"
                                formats.add(
                                    FormatOption(
                                        formatId = "yt_cobalt_max",
                                        ext = "mp4",
                                        resolution = "Máxima Calidad (Original)",
                                        isNative = true,
                                        filesizeApprox = 35 * 1024 * 1024L,
                                        streamUrl = streamUrl
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag("MediaVaultDebug").w("Cobalt API pública no disponible: ${e.message}")
            }
        }

        if (formats.isEmpty()) {
            throw Exception("No se pudieron resolver los streams directos de YouTube a través de las instancias activas. Se activará Modo Universal.")
        }

        return MediaInfo(
            url = url,
            title = title,
            thumbnailUrl = thumbnail,
            duration = duration,
            platform = Platform.YOUTUBE,
            uploader = author,
            formats = formats.sortedByDescending { it.height ?: 0 },
            resolvedByInstance = successfulInstance
        )
    }

    // ==========================================
    // TIKTOK EXTRACTOR (TikWM API)
    // ==========================================
    private fun extractTikTok(url: String, cookieHeader: String?): MediaInfo {
        Timber.tag("MediaVaultDebug").d("Extrayendo video de TikTok vía TikWM...")
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        val apiUrl = "https://www.tikwm.com/api/?url=$encodedUrl"

        val request = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", userAgentBrowser)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Error al conectar con la API de TikTok (HTTP ${response.code})")

            val body = response.body?.string() ?: throw Exception("Respuesta vacía de TikTok")
            val json = gson.fromJson(body, JsonObject::class.java)
            val code = json.get("code")?.asInt ?: -1

            if (code != 0) {
                val msg = json.get("msg")?.asString ?: "Video no encontrado o privado"
                throw Exception("TikTok API: $msg")
            }

            val data = json.getAsJsonObject("data") ?: throw Exception("Formato inválido de TikTok")
            val title = data.get("title")?.asString ?: "TikTok Video"
            val author = data.getAsJsonObject("author")?.get("nickname")?.asString ?: "TikTok User"
            val duration = data.get("duration")?.asLong ?: 0L
            val thumbnail = data.get("cover")?.asString

            val playUrl = data.get("play")?.asString
            val hdPlayUrl = data.get("hdplay")?.asString
            val musicUrl = data.get("music")?.asString

            val formats = mutableListOf<FormatOption>()

            if (!hdPlayUrl.isNullOrBlank()) {
                val fullHdUrl = if (hdPlayUrl.startsWith("http")) hdPlayUrl else "https://www.tikwm.com$hdPlayUrl"
                formats.add(
                    FormatOption(
                        formatId = "tt_hd_nowm",
                        ext = "mp4",
                        resolution = "HD Sin Marca de Agua (1080p)",
                        height = 1080,
                        isNative = true,
                        filesizeApprox = data.get("hd_size")?.asLong ?: (20 * 1024 * 1024L),
                        streamUrl = fullHdUrl
                    )
                )
            }

            if (!playUrl.isNullOrBlank()) {
                val fullPlayUrl = if (playUrl.startsWith("http")) playUrl else "https://www.tikwm.com$playUrl"
                formats.add(
                    FormatOption(
                        formatId = "tt_sd_nowm",
                        ext = "mp4",
                        resolution = "SD Sin Marca de Agua (720p)",
                        height = 720,
                        isNative = true,
                        filesizeApprox = data.get("size")?.asLong ?: (10 * 1024 * 1024L),
                        streamUrl = fullPlayUrl
                    )
                )
            }

            if (!musicUrl.isNullOrBlank()) {
                val fullMusicUrl = if (musicUrl.startsWith("http")) musicUrl else "https://www.tikwm.com$musicUrl"
                formats.add(
                    FormatOption(
                        formatId = "tt_audio",
                        ext = "mp3",
                        resolution = null,
                        isAudioOnly = true,
                        isNative = true,
                        filesizeApprox = 3 * 1024 * 1024L,
                        streamUrl = fullMusicUrl
                    )
                )
            }

            if (formats.isEmpty()) {
                throw Exception("No se encontraron URLs de descarga para este video de TikTok.")
            }

            return MediaInfo(
                url = url,
                title = title,
                thumbnailUrl = thumbnail,
                duration = duration,
                platform = Platform.TIKTOK,
                uploader = author,
                formats = formats
            )
        }
    }

    // ==========================================
    // TWITTER / X EXTRACTOR (VxTwitter / Syndication)
    // ==========================================
    private fun extractTwitter(url: String, cookieHeader: String?): MediaInfo {
        Timber.tag("MediaVaultDebug").d("Extrayendo video de Twitter/X...")
        val tweetId = extractTwitterId(url) ?: throw Exception("ID de tweet no encontrado.")

        // 1. Intentar vía API de VxTwitter
        try {
            val vxUrl = "https://api.vxtwitter.com/Twitter/status/$tweetId"
            val request = Request.Builder().url(vxUrl).header("User-Agent", userAgentBrowser).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = gson.fromJson(body, JsonObject::class.java)
                        val text = json.get("text")?.asString ?: "Tweet de X ($tweetId)"
                        val author = json.get("user_name")?.asString ?: "Usuario de X"
                        val mediaUrl = json.get("media_url")?.asString
                        val mediaType = json.get("media_type")?.asString

                        val formats = mutableListOf<FormatOption>()
                        if (!mediaUrl.isNullOrBlank() && (mediaType == "video" || mediaUrl.contains(".mp4") || mediaUrl.contains(".m3u8"))) {
                            formats.add(
                                FormatOption(
                                    formatId = "tw_vx_hd",
                                    ext = if (mediaUrl.contains(".m3u8")) "m3u8" else "mp4",
                                    resolution = "HD Calidad Original",
                                    isNative = true,
                                    filesizeApprox = 15 * 1024 * 1024L,
                                    streamUrl = mediaUrl
                                )
                            )

                            return MediaInfo(
                                url = url,
                                title = text.take(60),
                                thumbnailUrl = json.get("media_thumb_url")?.asString,
                                platform = Platform.TWITTER,
                                uploader = author,
                                formats = formats
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("MediaVaultDebug").w("VxTwitter fallback: ${e.message}")
        }

        // 2. Intentar vía Syndication API
        try {
            val syndicationUrl = "https://cdn.syndication.twimg.com/tweet-result?id=$tweetId&lang=en"
            val request = Request.Builder().url(syndicationUrl).header("User-Agent", userAgentBrowser).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = gson.fromJson(body, JsonObject::class.java)
                        val text = json.get("text")?.asString ?: "Tweet Video"
                        val user = json.getAsJsonObject("user")?.get("name")?.asString ?: "Twitter"
                        val mediaEntities = json.getAsJsonArray("mediaEntities")

                        val formats = mutableListOf<FormatOption>()
                        if (mediaEntities != null && mediaEntities.size() > 0) {
                            for (elem in mediaEntities) {
                                val obj = elem.asJsonObject
                                val videoInfo = obj.getAsJsonObject("video_info")
                                val variants = videoInfo?.getAsJsonArray("variants")
                                if (variants != null) {
                                    for (v in variants) {
                                        val vObj = v.asJsonObject
                                        val streamUrl = vObj.get("url")?.asString ?: continue
                                        val contentType = vObj.get("content_type")?.asString ?: ""
                                        val bitrate = vObj.get("bitrate")?.asLong ?: 0L

                                        if (contentType == "video/mp4") {
                                            formats.add(
                                                FormatOption(
                                                    formatId = "tw_mp4_${bitrate}",
                                                    ext = "mp4",
                                                    resolution = if (bitrate > 1000000) "1080p / 720p HD" else "480p / 360p SD",
                                                    isNative = true,
                                                    filesizeApprox = 10 * 1024 * 1024L,
                                                    streamUrl = streamUrl
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (formats.isNotEmpty()) {
                            return MediaInfo(
                                url = url,
                                title = text.take(60),
                                platform = Platform.TWITTER,
                                uploader = user,
                                formats = formats.sortedByDescending { it.filesizeApprox ?: 0L }
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("MediaVaultDebug").w("Syndication fallback: ${e.message}")
        }

        throw Exception("No se pudo extraer el video de Twitter/X. El tweet podría ser privado o no contener video.")
    }

    // ==========================================
    // INSTAGRAM EXTRACTOR (Cobalt API / Scraping)
    // ==========================================
    private fun extractInstagram(url: String, cookieHeader: String?): MediaInfo {
        Timber.tag("MediaVaultDebug").d("Extrayendo video de Instagram...")

        // 1. Intentar con Cobalt API
        try {
            val cobaltJson = JsonObject().apply {
                addProperty("url", url)
                addProperty("videoQuality", "max")
            }
            val body = cobaltJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://api.cobalt.tools/")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgentBrowser)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respBody = response.body?.string()
                    if (!respBody.isNullOrBlank()) {
                        val json = gson.fromJson(respBody, JsonObject::class.java)
                        val streamUrl = json.get("url")?.asString
                        if (!streamUrl.isNullOrBlank()) {
                            val formats = listOf(
                                FormatOption(
                                    formatId = "ig_cobalt_hd",
                                    ext = "mp4",
                                    resolution = "HD Calidad Original (Instagram)",
                                    isNative = true,
                                    filesizeApprox = 20 * 1024 * 1024L,
                                    streamUrl = streamUrl
                                )
                            )
                            return MediaInfo(
                                url = url,
                                title = "Instagram Reel / Post",
                                platform = Platform.INSTAGRAM,
                                uploader = "Instagram Creator",
                                formats = formats
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("MediaVaultDebug").w("Cobalt API fallo para Instagram: ${e.message}")
        }

        // 2. Scraping directo con cookies
        val cleanUrl = if (url.contains("?")) url.substring(0, url.indexOf("?")) else url
        val target = if (!cleanUrl.endsWith("/")) "$cleanUrl/" else cleanUrl
        val reqBuilder = Request.Builder()
            .url("${target}?__a=1&__d=dis")
            .header("User-Agent", userAgentBrowser)

        if (!cookieHeader.isNullOrBlank()) {
            reqBuilder.header("Cookie", cookieHeader)
        }

        try {
            client.newCall(reqBuilder.build()).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && !body.isNullOrBlank()) {
                    val pattern = Pattern.compile("\"video_url\":\"(https:[^\"\\\\]+)\"")
                    val matcher = pattern.matcher(body)
                    if (matcher.find()) {
                        val stream = matcher.group(1)?.replace("\\u0026", "&")
                        if (!stream.isNullOrBlank()) {
                            val formats = listOf(
                                FormatOption(
                                    formatId = "ig_scraped_hd",
                                    ext = "mp4",
                                    resolution = "HD Calidad Original",
                                    isNative = true,
                                    filesizeApprox = 15 * 1024 * 1024L,
                                    streamUrl = stream
                                )
                            )
                            return MediaInfo(
                                url = url,
                                title = "Instagram Video",
                                platform = Platform.INSTAGRAM,
                                uploader = "Instagram",
                                formats = formats
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("MediaVaultDebug").w("Scraping Instagram fallo: ${e.message}")
        }

        throw Exception("Instagram requiere inicio de sesión (Cookies) o la cuenta es privada.")
    }

    // ==========================================
    // FACEBOOK EXTRACTOR
    // ==========================================
    private fun extractFacebook(url: String, cookieHeader: String?): MediaInfo {
        Timber.tag("MediaVaultDebug").d("Extrayendo video de Facebook...")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgentBrowser)
            .apply {
                if (!cookieHeader.isNullOrBlank()) header("Cookie", cookieHeader)
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Error al conectar con Facebook (HTTP ${response.code})")

            val html = response.body?.string() ?: throw Exception("Respuesta vacía de Facebook")

            val hdPattern = Pattern.compile("hd_src_no_ratelimit:\"(https:[^\"]+)\"|hd_src:\"(https:[^\"]+)\"")
            val sdPattern = Pattern.compile("sd_src_no_ratelimit:\"(https:[^\"]+)\"|sd_src:\"(https:[^\"]+)\"")

            val hdMatcher = hdPattern.matcher(html)
            val sdMatcher = sdPattern.matcher(html)

            val hdUrl = if (hdMatcher.find()) (hdMatcher.group(1) ?: hdMatcher.group(2)) else null
            val sdUrl = if (sdMatcher.find()) (sdMatcher.group(1) ?: sdMatcher.group(2)) else null

            val formats = mutableListOf<FormatOption>()
            if (!hdUrl.isNullOrBlank()) {
                formats.add(
                    FormatOption(
                        formatId = "fb_hd",
                        ext = "mp4",
                        resolution = "HD (1080p / 720p)",
                        height = 1080,
                        isNative = true,
                        filesizeApprox = 25 * 1024 * 1024L,
                        streamUrl = hdUrl.replace("\\/", "/")
                    )
                )
            }

            if (!sdUrl.isNullOrBlank()) {
                formats.add(
                    FormatOption(
                        formatId = "fb_sd",
                        ext = "mp4",
                        resolution = "SD (480p / 360p)",
                        height = 480,
                        isNative = true,
                        filesizeApprox = 10 * 1024 * 1024L,
                        streamUrl = sdUrl.replace("\\/", "/")
                    )
                )
            }

            if (formats.isEmpty()) {
                throw Exception("No se pudo extraer el enlace de video de Facebook. Podría ser un grupo privado.")
            }

            return MediaInfo(
                url = url,
                title = "Facebook Video",
                platform = Platform.FACEBOOK,
                uploader = "Facebook User",
                formats = formats
            )
        }
    }

    // ==========================================
    // REDDIT EXTRACTOR (.json endpoint)
    // ==========================================
    private fun extractReddit(url: String, cookieHeader: String?): MediaInfo {
        Timber.tag("MediaVaultDebug").d("Extrayendo video de Reddit...")
        val cleanUrl = if (url.contains("?")) url.substring(0, url.indexOf("?")) else url
        val jsonUrl = if (cleanUrl.endsWith("/")) "${cleanUrl.dropLast(1)}.json" else "$cleanUrl.json"

        val request = Request.Builder()
            .url(jsonUrl)
            .header("User-Agent", userAgentBrowser)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Error al consultar post de Reddit (HTTP ${response.code})")

            val body = response.body?.string() ?: throw Exception("Respuesta vacía de Reddit")
            val array = gson.fromJson(body, com.google.gson.JsonArray::class.java)

            val postData = array[0].asJsonObject
                .getAsJsonObject("data")
                .getAsJsonArray("children")[0].asJsonObject
                .getAsJsonObject("data")

            val title = postData.get("title")?.asString ?: "Reddit Video"
            val author = postData.get("author")?.asString ?: "Reddit User"
            val subreddit = postData.get("subreddit_name_prefixed")?.asString ?: "r/reddit"
            val thumbnail = postData.get("thumbnail")?.asString
            val duration = 0L

            val media = postData.getAsJsonObject("media")
            val redditVideo = media?.getAsJsonObject("reddit_video")
                ?: postData.getAsJsonObject("secure_media")?.getAsJsonObject("reddit_video")
                ?: throw Exception("Este post de Reddit no contiene un video nativo alojado en Reddit.")

            val fallbackUrl = redditVideo.get("fallback_url")?.asString
                ?: throw Exception("No se encontró URL de stream en el post de Reddit.")
            val height = redditVideo.get("height")?.asInt ?: 720
            val hlsUrl = redditVideo.get("hls_url")?.asString

            val formats = mutableListOf<FormatOption>()

            formats.add(
                FormatOption(
                    formatId = "rd_${height}p",
                    ext = "mp4",
                    resolution = "${height}p HD (Reddit)",
                    height = height,
                    isNative = true,
                    filesizeApprox = 15 * 1024 * 1024L,
                    streamUrl = fallbackUrl
                )
            )

            if (!hlsUrl.isNullOrBlank()) {
                formats.add(
                    FormatOption(
                        formatId = "rd_hls",
                        ext = "m3u8",
                        resolution = "HLS Stream Completo (Audio+Video)",
                        height = height,
                        isNative = true,
                        filesizeApprox = 20 * 1024 * 1024L,
                        streamUrl = hlsUrl
                    )
                )
            }

            formats.add(
                FormatOption(
                    formatId = "rd_audio",
                    ext = "mp3",
                    resolution = null,
                    isAudioOnly = true,
                    isNative = true,
                    filesizeApprox = 4 * 1024 * 1024L,
                    streamUrl = fallbackUrl
                )
            )

            return MediaInfo(
                url = url,
                title = title,
                thumbnailUrl = thumbnail,
                duration = duration,
                platform = Platform.REDDIT,
                uploader = "$author ($subreddit)",
                formats = formats
            )
        }
    }

    // ==========================================
    // VIMEO EXTRACTOR (/config API)
    // ==========================================
    private fun extractVimeo(url: String, cookieHeader: String?): MediaInfo {
        Timber.tag("MediaVaultDebug").d("Extrayendo video de Vimeo...")
        val pattern = Pattern.compile("vimeo\\.com/(?:channels/(?:\\w+/)?|groups/[^/]+/videos/|album/\\d+/video/|video/|)(\\d+)")
        val matcher = pattern.matcher(url)
        val videoId = if (matcher.find()) matcher.group(1) else throw Exception("ID de Vimeo no encontrado.")

        val configUrl = "https://player.vimeo.com/video/$videoId/config"
        val request = Request.Builder()
            .url(configUrl)
            .header("User-Agent", userAgentBrowser)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Error al consultar video de Vimeo (HTTP ${response.code})")

            val body = response.body?.string() ?: throw Exception("Respuesta vacía de Vimeo")
            val json = gson.fromJson(body, JsonObject::class.java)

            val videoObj = json.getAsJsonObject("video")
            val title = videoObj?.get("title")?.asString ?: "Vimeo Video ($videoId)"
            val author = videoObj?.getAsJsonObject("owner")?.get("name")?.asString ?: "Vimeo Creator"
            val duration = videoObj?.get("duration")?.asLong ?: 0L
            val thumbnail = json.getAsJsonObject("video")?.getAsJsonObject("thumbs")?.get("base")?.asString

            val files = json.getAsJsonObject("request")?.getAsJsonObject("files")
            val progressive = files?.getAsJsonArray("progressive")

            val formats = mutableListOf<FormatOption>()
            if (progressive != null && progressive.size() > 0) {
                for (item in progressive) {
                    val obj = item.asJsonObject
                    val streamUrl = obj.get("url")?.asString ?: continue
                    val quality = obj.get("quality")?.asString ?: "HD"
                    val height = when (quality) {
                        "1080p" -> 1080
                        "720p" -> 720
                        "540p" -> 540
                        "360p" -> 360
                        else -> 720
                    }

                    formats.add(
                        FormatOption(
                            formatId = "vm_$quality",
                            ext = "mp4",
                            resolution = "$quality (Vimeo)",
                            height = height,
                            isNative = true,
                            filesizeApprox = 25 * 1024 * 1024L,
                            streamUrl = streamUrl
                        )
                    )
                }
            }

            if (formats.isEmpty()) {
                throw Exception("No se encontraron streams MP4 progresivos disponibles en este video de Vimeo.")
            }

            return MediaInfo(
                url = url,
                title = title,
                thumbnailUrl = thumbnail,
                duration = duration,
                platform = Platform.VIMEO,
                uploader = author,
                formats = formats.sortedByDescending { it.height ?: 0 }
            )
        }
    }

    // ==========================================
    // SOUNDCLOUD EXTRACTOR
    // ==========================================
    private fun extractSoundCloud(url: String, cookieHeader: String?): MediaInfo {
        Timber.tag("MediaVaultDebug").d("Extrayendo track de SoundCloud...")
        val formats = listOf(
            FormatOption(
                formatId = "sc_hq",
                ext = "mp3",
                resolution = null,
                abr = 320f,
                isAudioOnly = true,
                isNative = true,
                filesizeApprox = 8 * 1024 * 1024L,
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

    // ==========================================
    // GENERIC / DIRECT STREAM
    // ==========================================
    private fun extractGenericOrDirect(url: String, cookieHeader: String?): MediaInfo {
        Timber.tag("MediaVaultDebug").d("Extrayendo enlace directo / genérico: $url")
        val lower = url.lowercase()
        val isAudio = lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".ogg") || lower.endsWith(".flac") || lower.endsWith(".wav")
        val isHls = lower.contains(".m3u8")

        val formats = listOf(
            FormatOption(
                formatId = if (isAudio) "direct_audio" else if (isHls) "direct_hls_stream" else "direct_video",
                ext = if (isAudio) "mp3" else if (isHls) "m3u8" else "mp4",
                resolution = if (isAudio) null else if (isHls) "HLS Live/VOD Stream (.m3u8)" else "Enlace Directo",
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

    private fun extractYouTubeId(url: String): String? {
        val pattern = "(?<=watch\\?v=|/videos/|embed/|youtu.be/|/v/|/e/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%E2%80%8C%E2%80%8B2F|youtu.be%2F|%2Fv%2F|shorts/)[^#&?\\n/]*"
        val compiled = Pattern.compile(pattern)
        val matcher = compiled.matcher(url)
        return if (matcher.find()) matcher.group() else null
    }

    private fun extractTwitterId(url: String): String? {
        val pattern = "(?:twitter\\.com|x\\.com|vxtwitter\\.com|fxtwitter\\.com)/[^/]+/status/([0-9]+)"
        val compiled = Pattern.compile(pattern)
        val matcher = compiled.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }
}
