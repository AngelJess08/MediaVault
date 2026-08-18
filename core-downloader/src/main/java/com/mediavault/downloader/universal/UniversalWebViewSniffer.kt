package com.mediavault.downloader.universal

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.webkit.*
import com.mediavault.downloader.security.AdAndMalwareFilter
import com.mediavault.downloader.security.FileSafetyValidator
import com.mediavault.downloader.security.SecureWebViewHelper
import com.mediavault.storage.db.dao.CookieDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URI
import java.util.Collections
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Excepción cuando el contenido está protegido por esquemas de DRM comercial.
 */
class DrmProtectedException(message: String) : Exception(message)

/**
 * Excepción cuando el video se sirve exclusivamente mediante Media Source Extensions (MSE / blob:).
 */
class BlobMseUnsupportedException(message: String) : Exception(message)

/**
 * Excepción cuando tras el timeout no se detectó ningún stream multimedia.
 */
class NoMediaFoundException(message: String) : Exception(message)

/**
 * Modo Universal: Carga páginas web en un WebView oculto e intercepta el tráfico de red
 * en tiempo real para descubrir streams de video, audio, HLS (.m3u8) y DASH (.mpd).
 * Incorpora hardening integral de seguridad, aislamiento de sandbox y filtro anti-malware.
 */
@Singleton
class UniversalWebViewSniffer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cookieDao: CookieDao,
    private val adAndMalwareFilter: AdAndMalwareFilter,
    private val fileSafetyValidator: FileSafetyValidator
) {
    private val TAG = "MediaVaultUniversalMode"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val desktopUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    private val mobileUserAgent =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    /**
     * Inspecciona la página web dada en un WebView headless y extrae los candidatos multimedia.
     */
    suspend fun sniff(
        targetUrl: String,
        cookieHeader: String? = null,
        timeoutSeconds: Long = 15
    ): List<SnifferCandidate> = withContext(Dispatchers.Main) {
        Timber.tag(TAG).d("==================================================")
        Timber.tag(TAG).d("Iniciando Modo Universal para: $targetUrl (Timeout: ${timeoutSeconds}s)")

        val candidates = Collections.synchronizedList(mutableListOf<SnifferCandidate>())
        var detectedBlobMse = false
        var detectedDrm = false
        var pageTitle = "Video Web Descubierto"

        var webView: WebView? = null

        try {
            withTimeout(timeoutSeconds * 1000) {
                val completionDeferred = CompletableDeferred<Boolean>()

                webView = createHeadlessWebView(
                    targetUrl = targetUrl,
                    cookieHeader = cookieHeader,
                    onCandidateFound = { candidate ->
                        // Validación de seguridad de archivo antes de aceptar candidato
                        if (fileSafetyValidator.isForbiddenExtension(candidate.extension) ||
                            fileSafetyValidator.isForbiddenUrlOrFilename(candidate.url)
                        ) {
                            Timber.tag(TAG).w("Candidato DESCARTADO por seguridad (archivo ejecutable/malicioso): ${candidate.url}")
                            return@createHeadlessWebView
                        }

                        val isDuplicate = candidates.any { it.url.equals(candidate.url, ignoreCase = true) }
                        if (!isDuplicate) {
                            candidates.add(candidate)
                            Timber.tag(TAG).d("Candidato ACEPTADO (#${candidates.size}): ${candidate.extension} | Res: ${candidate.estimatedResolution} | URL: ${candidate.url.take(80)}...")
                            // Si encontramos al menos 2 formatos o un manifiesto HLS/DASH principal, podemos dar margen de estabilización
                            if (candidate.isHls || candidate.isDash || candidates.size >= 3) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (!completionDeferred.isCompleted) completionDeferred.complete(true)
                                }, 2500)
                            }
                        }
                    },
                    onBlobDetected = {
                        detectedBlobMse = true
                        Timber.tag(TAG).w("Reproductor Media Source / blob: detectado en la página.")
                    },
                    onDrmDetected = {
                        detectedDrm = true
                        Timber.tag(TAG).e("Protección DRM (Widevine/PlayReady) detectada en la página.")
                    },
                    onTitleReceived = { title ->
                        if (!title.isNullOrBlank() && !title.contains("http", ignoreCase = true)) {
                            pageTitle = title
                        }
                    }
                )

                webView?.loadUrl(targetUrl)

                // Esperar a que se estabilicen los candidatos o termine el tiempo
                completionDeferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            Timber.tag(TAG).d("Tiempo de espera del sniffer concluido (${timeoutSeconds}s). Evaluando candidatos recolectados...")
        } catch (e: Exception) {
            Timber.tag(TAG).w("Aviso durante la ejecución del WebView: ${e.message}")
        } finally {
            destroyWebView(webView)
        }

        // Si tenemos candidatos válidos, asignamos título y ordenamos por calidad
        if (candidates.isNotEmpty()) {
            Timber.tag(TAG).d("Modo Universal exitoso: ${candidates.size} candidato(s) encontrado(s).")
            return@withContext candidates.map { it.copy(title = pageTitle) }
                .sortedWith(
                    compareByDescending<SnifferCandidate> { it.isHls || it.isDash }
                        .thenByDescending { it.contentLength ?: 0L }
                        .thenBy { it.isAudioOnly }
                )
        }

        // Manejo honesto y explícito de limitaciones técnicas documentadas
        if (detectedDrm) {
            throw DrmProtectedException("Este contenido está protegido por cifrado digital (DRM Widevine/PlayReady) y no se puede descargar.")
        }

        if (detectedBlobMse) {
            throw BlobMseUnsupportedException("Este sitio usa un reproductor (Media Source / blob:) que fragmenta el video en memoria vía JavaScript y no se puede extraer directamente.")
        }

        throw NoMediaFoundException("No se pudo encontrar un video reproducible en esta página.")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createHeadlessWebView(
        targetUrl: String,
        cookieHeader: String?,
        onCandidateFound: (SnifferCandidate) -> Unit,
        onBlobDetected: () -> Unit,
        onDrmDetected: () -> Unit,
        onTitleReceived: (String?) -> Unit
    ): WebView {
        val webView = WebView(context)

        // 1. Inyección de Cookies si existen
        injectCookies(targetUrl, cookieHeader)

        // 2. Hardening de seguridad integral (Aislamiento de sandbox, anti-popups)
        val settings = webView.settings
        SecureWebViewHelper.applyStrictSecuritySettings(settings, isVisibleBrowser = false)
        settings.userAgentString = mobileUserAgent
        settings.loadsImagesAutomatically = false // Ahorro de recursos y ancho de banda
        settings.blockNetworkImage = true

        val redirectTracker = SecureWebViewHelper.RedirectTracker()

        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                onTitleReceived(title)
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                // Bloquear creación de ventanas emergentes o popups
                Timber.tag(TAG).d("Popup bloqueado en WebView headless.")
                return false
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                result?.cancel()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                result?.cancel()
                return true
            }

            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                result?.cancel()
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false

                // 1. Bloqueo de Intent Hijacking y esquemas no-web en modo headless
                if (!SecureWebViewHelper.isSafeWebScheme(url)) {
                    Timber.tag(TAG).w("Esquema no-web bloqueado en sniffer headless: $url")
                    return true
                }

                // 2. Detección de Redirect-Bombing
                if (redirectTracker.recordAndCheckBombing()) {
                    Timber.tag(TAG).e("Redirect-bombing detectado en sniffer. Deteniendo carga.")
                    view?.stopLoading()
                    return true
                }

                return false
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val reqUrl = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)

                // 1. Bloqueo de redes de anuncios, rastreadores y malware a nivel de red
                if (adAndMalwareFilter.shouldBlock(reqUrl)) {
                    return adAndMalwareFilter.createEmptyResponse()
                }

                // 2. Evaluar la petición de red para captura de streams multimedia
                evaluateNetworkRequest(reqUrl, request.requestHeaders, onCandidateFound, onBlobDetected, onDrmDetected)

                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Timber.tag(TAG).d("Página cargada en WebView. Inyectando script de inspección DOM...")

                // Inyectar supresión anti-intersticiales
                view?.let { SecureWebViewHelper.injectAntiInterstitialScript(it) }

                // Inyección JS para descubrir elementos <video>, <audio> y detectar DRM / blob
                val jsInspector = """
                    (function() {
                        try {
                            // 1. Detectar DRM EME
                            if (navigator.requestMediaKeySystemAccess) {
                                var orig = navigator.requestMediaKeySystemAccess;
                                navigator.requestMediaKeySystemAccess = function() {
                                    console.log('DRM_DETECTED');
                                    return orig.apply(this, arguments);
                                };
                            }

                            // 2. Inspeccionar elementos video y audio en el DOM
                            function scanMedia() {
                                var videos = document.querySelectorAll('video, audio, source');
                                for (var i = 0; i < videos.length; i++) {
                                    var src = videos[i].src || videos[i].currentSrc;
                                    if (src) {
                                        if (src.startsWith('blob:')) {
                                            console.log('BLOB_DETECTED:' + src);
                                        } else if (src.startsWith('http')) {
                                            console.log('MEDIA_SRC_FOUND:' + src);
                                        }
                                    }
                                }
                            }
                            scanMedia();
                            setInterval(scanMedia, 1500);

                            // 3. Forzar reproducción para disparar buffering de video
                            var allVideos = document.getElementsByTagName('video');
                            for (var j = 0; j < allVideos.length; j++) {
                                allVideos[j].muted = true;
                                allVideos[j].play().catch(function(){});
                            }
                        } catch(e) {}
                    })();
                """.trimIndent()

                view?.evaluateJavascript(jsInspector, null)
            }
        }

        return webView
    }

    private fun evaluateNetworkRequest(
        url: String,
        headers: Map<String, String>?,
        onCandidateFound: (SnifferCandidate) -> Unit,
        onBlobDetected: () -> Unit,
        onDrmDetected: () -> Unit
    ) {
        val lowerUrl = url.lowercase()

        // 1. Descartar si coincide con filtro de anuncios/malware o archivos ejecutables
        if (adAndMalwareFilter.shouldBlock(url) || fileSafetyValidator.isForbiddenUrlOrFilename(url)) {
            return
        }

        // 2. Detección de blob: MSE
        if (lowerUrl.startsWith("blob:")) {
            onBlobDetected()
            return
        }

        // 3. Detección de DRM / Licencias
        if (lowerUrl.contains("widevine") || lowerUrl.contains("playready") || lowerUrl.contains("/license/") || lowerUrl.contains("drm/")) {
            onDrmDetected()
            return
        }

        // 4. Manifiestos HLS (.m3u8)
        if (lowerUrl.contains(".m3u8")) {
            Timber.tag(TAG).d("Manifiesto HLS (.m3u8) interceptado: $url")
            onCandidateFound(
                SnifferCandidate(
                    url = url,
                    mimeType = "application/vnd.apple.mpegurl",
                    extension = "m3u8",
                    isHls = true,
                    estimatedResolution = "Auto (Stream HLS Adaptativo)",
                    headers = headers ?: emptyMap()
                )
            )
            return
        }

        // 5. Manifiestos DASH (.mpd)
        if (lowerUrl.contains(".mpd")) {
            Timber.tag(TAG).d("Manifiesto DASH (.mpd) interceptado: $url")
            onCandidateFound(
                SnifferCandidate(
                    url = url,
                    mimeType = "application/dash+xml",
                    extension = "mpd",
                    isDash = true,
                    estimatedResolution = "Auto (Stream DASH)",
                    headers = headers ?: emptyMap()
                )
            )
            return
        }

        // 6. Archivos de video directo (.mp4, .webm, .mkv, .mov, etc.)
        val videoExtensions = listOf(".mp4", ".webm", ".mkv", ".mov", ".m4v", ".flv", ".ts")
        val matchingVideoExt = videoExtensions.find { lowerUrl.contains(it) }

        if (matchingVideoExt != null) {
            val cleanExt = matchingVideoExt.removePrefix(".")
            // Estimar resolución a partir de la URL si viene etiquetada
            val resGuess = estimateResolutionFromUrl(lowerUrl)
            onCandidateFound(
                SnifferCandidate(
                    url = url,
                    mimeType = "video/$cleanExt",
                    extension = cleanExt,
                    isHls = false,
                    isDash = false,
                    isAudioOnly = false,
                    estimatedResolution = resGuess,
                    headers = headers ?: emptyMap()
                )
            )
            return
        }

        // 7. Archivos de audio directo (.mp3, .m4a, .aac, .ogg, .flac, .wav)
        val audioExtensions = listOf(".mp3", ".m4a", ".aac", ".ogg", ".flac", ".wav", ".opus")
        val matchingAudioExt = audioExtensions.find { lowerUrl.contains(it) }

        if (matchingAudioExt != null) {
            val cleanExt = matchingAudioExt.removePrefix(".")
            onCandidateFound(
                SnifferCandidate(
                    url = url,
                    mimeType = "audio/$cleanExt",
                    extension = cleanExt,
                    isHls = false,
                    isDash = false,
                    isAudioOnly = true,
                    estimatedResolution = "Audio ($cleanExt)",
                    headers = headers ?: emptyMap()
                )
            )
            return
        }

        // 8. Headers HTTP y Content-Type
        val acceptHeader = headers?.get("Accept")?.lowercase() ?: ""
        if (acceptHeader.contains("video/") || acceptHeader.contains("audio/")) {
            val isAudio = acceptHeader.contains("audio/")
            onCandidateFound(
                SnifferCandidate(
                    url = url,
                    mimeType = if (isAudio) "audio/mp3" else "video/mp4",
                    extension = if (isAudio) "mp3" else "mp4",
                    isHls = false,
                    isDash = false,
                    isAudioOnly = isAudio,
                    estimatedResolution = if (isAudio) "Audio Web" else "Video Web",
                    headers = headers ?: emptyMap()
                )
            )
        }
    }

    private fun estimateResolutionFromUrl(url: String): String {
        return when {
            url.contains("2160") || url.contains("4k") -> "4K 2160p"
            url.contains("1440") || url.contains("2k") -> "2K 1440p"
            url.contains("1080") || url.contains("fhd") -> "1080p FHD"
            url.contains("720") || url.contains("hd") -> "720p HD"
            url.contains("480") || url.contains("sd") -> "480p SD"
            url.contains("360") -> "360p"
            url.contains("240") -> "240p"
            else -> "Calidad Original"
        }
    }

    private fun injectCookies(targetUrl: String, customCookieHeader: String?) {
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)

            // Inyectar cookie personalizada si fue provista
            if (!customCookieHeader.isNullOrBlank()) {
                val pairs = customCookieHeader.split(";")
                for (pair in pairs) {
                    val trimmed = pair.trim()
                    if (trimmed.isNotEmpty()) {
                        cookieManager.setCookie(targetUrl, trimmed)
                    }
                }
            }

            cookieManager.flush()
        } catch (e: Exception) {
            Timber.tag(TAG).w("No se pudieron inyectar cookies para $targetUrl: ${e.message}")
        }
    }

    private fun destroyWebView(webView: WebView?) {
        if (webView == null) return
        try {
            Timber.tag(TAG).d("Destruyendo instancia de WebView para liberar memoria y batería...")
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        } catch (e: Exception) {
            Timber.tag(TAG).w("Error al destruir WebView: ${e.message}")
        }
    }
}
