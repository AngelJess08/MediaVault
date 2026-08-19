package com.mediavault.downloader.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class DomainSafetyReport(
    val domain: String,
    val isSafe: Boolean,
    val threatType: String? = null,
    val checkedAt: Long = System.currentTimeMillis()
)

@Singleton
class SafeBrowsingVerifier @Inject constructor() {

    private val cache = ConcurrentHashMap<String, DomainSafetyReport>()
    private val CACHE_TTL_MS = 3600_000L // 1 hora de caché local

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun verifyDomainSafety(url: String, apiKey: String? = null): DomainSafetyReport = withContext(Dispatchers.IO) {
        val domain = extractDomain(url)
        val cached = cache[domain]
        if (cached != null && System.currentTimeMillis() - cached.checkedAt < CACHE_TTL_MS) {
            return@withContext cached
        }

        // Si no hay API Key de Google Safe Browsing configurada, verificar contra listas heurísticas locales
        if (apiKey.isNullOrBlank()) {
            val isKnownThreat = isLocallyBlacklisted(domain)
            val report = DomainSafetyReport(
                domain = domain,
                isSafe = !isKnownThreat,
                threatType = if (isKnownThreat) "MALWARE_OR_PHISHING" else null
            )
            cache[domain] = report
            return@withContext report
        }

        // Consulta a Google Safe Browsing API v4
        try {
            val checkUrl = "https://safebrowsing.googleapis.com/v4/threatMatches:find?key=$apiKey"
            val jsonPayload = """
                {
                  "client": {
                    "clientId": "mediavault-android",
                    "clientVersion": "2.0.0"
                  },
                  "threatInfo": {
                    "threatTypes": ["MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"],
                    "platformTypes": ["ANY_PLATFORM"],
                    "threatEntryTypes": ["URL"],
                    "threatEntries": [{"url": "$url"}]
                  }
                }
            """.trimIndent()

            val request = Request.Builder()
                .url(checkUrl)
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val hasMatch = body.contains("threatType")
                val report = DomainSafetyReport(
                    domain = domain,
                    isSafe = !hasMatch,
                    threatType = if (hasMatch) "THREAT_DETECTED_BY_SAFEBROWSING" else null
                )
                cache[domain] = report
                return@withContext report
            }
        } catch (e: Exception) {
            Timber.tag("SafeBrowsing").w("Error al consultar Safe Browsing API: ${e.message}")
            val report = DomainSafetyReport(domain = domain, isSafe = true)
            cache[domain] = report
            return@withContext report
        }
    }

    private fun isLocallyBlacklisted(domain: String): Boolean {
        val lower = domain.lowercase()
        return lower.contains("phishing") || lower.contains("malware") ||
                lower.contains("trojan") || lower.contains("virus-scan") ||
                lower.contains("free-iphone-winner") || lower.contains("alert-security")
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = URI(if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url)
            uri.host?.lowercase()?.removePrefix("www.") ?: url
        } catch (e: Exception) {
            url
        }
    }
}
