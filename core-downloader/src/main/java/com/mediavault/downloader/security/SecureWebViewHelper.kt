package com.mediavault.downloader.security

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import timber.log.Timber

/**
 * Utilidades centralizadas para aplicar hardening de seguridad, restricciones de sandbox
 * y mitigación de popups/overlays en cualquier WebView del proyecto.
 */
object SecureWebViewHelper {

    private const val TAG = "MediaVaultSecureWebView"

    /**
     * Aplica la configuración de seguridad estricta al WebSettings de un WebView.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun applyStrictSecuritySettings(settings: WebSettings, isVisibleBrowser: Boolean = false) {
        // 1. Aislamiento estricto de archivos locales (Sandbox)
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false

        // 2. Deshabilitar geolocalización y guardado de credenciales
        settings.setGeolocationEnabled(false)
        settings.savePassword = false
        settings.saveFormData = false

        // 3. Control de reproducción automática (Auto-Mute / User Gesture)
        settings.mediaPlaybackRequiresUserGesture = true

        // 4. Seguridad de contenido mixto (Forzar HTTPS cuando esté disponible)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        // 5. JavaScript y almacenamiento web necesario para aplicaciones modernas
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false) // Bloquea popups agresivos en ventanas nuevas

        if (isVisibleBrowser) {
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
        }
    }

    /**
     * Comprueba si el esquema de la URL es seguro para navegación web directa.
     * Retorna TRUE si es http o https.
     * Retorna FALSE si es un intent://, market:// u otro esquema que requiere confirmación explícita del usuario.
     */
    fun isSafeWebScheme(url: String): Boolean {
        val lower = url.lowercase().trim()
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("about:blank")
    }

    /**
     * Extrae un nombre amigable o descripción del intent para mostrar al usuario en el diálogo de confirmación.
     */
    fun parseIntentDescription(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme ?: "desconocido"
            val host = uri.host ?: uri.path ?: url.take(30)
            when (scheme) {
                "market" -> "Google Play Store"
                "intent" -> {
                    if (url.contains("package=")) {
                        val pkg = url.substringAfter("package=").substringBefore(";")
                        "Aplicación externa ($pkg)"
                    } else "Aplicación del sistema"
                }
                "whatsapp" -> "WhatsApp"
                "tg", "telegram" -> "Telegram"
                "twitter", "x" -> "Twitter / X"
                "fb", "facebook" -> "Facebook"
                "instagram" -> "Instagram"
                "vnd.youtube" -> "YouTube App"
                else -> "Esquema externo: $scheme://$host"
            }
        } catch (e: Exception) {
            "Aplicación externa"
        }
    }

    /**
     * Inyecta script CSS/JS para suprimir banners intersticiales, overlays de clickjacking y popups flotantes.
     */
    fun injectAntiInterstitialScript(webView: WebView) {
        val script = """
            (function() {
                try {
                    // Ocultar overlays de clickjacking y contenedores flotantes comunes de sitios de streaming
                    var css = `
                        div[class*="popup"], div[id*="popup"],
                        div[class*="interstitial"], div[id*="interstitial"],
                        div[class*="banner-ad"], div[id*="banner-ad"],
                        div[style*="z-index: 2147483647"], div[style*="z-index: 999999"],
                        iframe[src*="ad"], iframe[id*="ad"], iframe[class*="ad"],
                        .floating-ad, .ad-overlay, .popunder, .ad-banner {
                            display: none !important;
                            visibility: hidden !important;
                            pointer-events: none !important;
                            opacity: 0 !important;
                        }
                    `;
                    var head = document.head || document.getElementsByTagName('head')[0];
                    if (head) {
                        var style = document.createElement('style');
                        style.type = 'text/css';
                        style.appendChild(document.createTextNode(css));
                        head.appendChild(style);
                    }

                    // Impedir window.open no solicitado
                    window.open = function(url, target, features) {
                        console.log('POPUP_BLOCKED:' + url);
                        return null;
                    };
                } catch(e) {}
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }

    /**
     * Detector de Redirect-Bombing (múltiples redirecciones rápidas encadenadas).
     */
    class RedirectTracker(
        private val maxRedirects: Int = 6,
        private val timeWindowMs: Long = 4000
    ) {
        private val redirectTimestamps = mutableListOf<Long>()

        /**
         * Registra una redirección y verifica si supera el umbral de seguridad.
         * Retorna TRUE si se detectó un ataque de redirección en cadena.
         */
        fun recordAndCheckBombing(): Boolean {
            val now = System.currentTimeMillis()
            redirectTimestamps.add(now)
            // Eliminar timestamps antiguos fuera de la ventana
            redirectTimestamps.removeAll { now - it > timeWindowMs }

            if (redirectTimestamps.size > maxRedirects) {
                Timber.tag(TAG).w("¡Alerta de Redirect-Bombing detectada! (${redirectTimestamps.size} redirects en ${timeWindowMs}ms). Cortando navegación.")
                return true
            }
            return false
        }

        fun reset() {
            redirectTimestamps.clear()
        }
    }
}
