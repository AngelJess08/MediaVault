package com.mediavault.downloader.security

import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DomainRateLimiter @Inject constructor() {

    private val lastRequestTimes = ConcurrentHashMap<String, Long>()
    private val requestCounts = ConcurrentHashMap<String, Int>()

    private val COOLDOWN_WINDOW_MS = 2000L // 2 segundos entre ráfagas
    private val MAX_REQUESTS_PER_WINDOW = 8 // Máximo 8 peticiones por ventana

    /**
     * Verifica si se debe permitir o limitar la petición hacia un dominio.
     * Retorna true si está dentro de los límites, o false si excede la frecuencia.
     */
    fun checkAndRecord(url: String): Boolean {
        val domain = extractDomain(url)
        val now = System.currentTimeMillis()
        val lastTime = lastRequestTimes[domain] ?: 0L

        if (now - lastTime > COOLDOWN_WINDOW_MS) {
            lastRequestTimes[domain] = now
            requestCounts[domain] = 1
            return true
        }

        val count = (requestCounts[domain] ?: 0) + 1
        requestCounts[domain] = count

        return count <= MAX_REQUESTS_PER_WINDOW
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = URI(if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url)
            uri.host?.lowercase() ?: url
        } catch (e: Exception) {
            url
        }
    }
}
