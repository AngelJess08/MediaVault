package com.mediavault.downloader.util

import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Pattern

object UrlValidator {

    private val WEB_URL_PATTERN = Pattern.compile(
        "https?://(?:www\\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b(?:[-a-zA-Z0-9()@:%_+.~#?&/=]*)"
    )

    private val SUPPORTED_DOMAINS = listOf(
        "youtube.com", "youtu.be",
        "twitter.com", "x.com",
        "instagram.com",
        "tiktok.com",
        "facebook.com", "fb.watch",
        "reddit.com",
        "vimeo.com"
    )

    fun isValidUrl(url: String): Boolean {
        if (url.isBlank()) return false
        if (!WEB_URL_PATTERN.matcher(url).matches()) return false

        return try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: return false
            SUPPORTED_DOMAINS.any { host.contains(it) }
        } catch (e: URISyntaxException) {
            false
        }
    }

    fun extractUrlFromText(text: String): String? {
        val matcher = WEB_URL_PATTERN.matcher(text)
        if (matcher.find()) {
            return matcher.group()
        }
        return null
    }

    fun sanitizeUrl(url: String): String {
        return try {
            val uri = URI(url)
            // Remove tracking parameters like ?si=, ?igshid=, ?t=
            val queryParams = uri.query?.split("&")?.mapNotNull {
                val parts = it.split("=")
                if (parts.size == 2) {
                    val key = parts[0].lowercase()
                    if (key in listOf("si", "igshid", "t", "utm_source", "utm_medium", "utm_campaign")) null
                    else it
                } else it
            }?.joinToString("&")
            
            val newQuery = if (queryParams.isNullOrEmpty()) null else queryParams
            val newUri = URI(uri.scheme, uri.authority, uri.path, newQuery, uri.fragment)
            newUri.toString()
        } catch (e: URISyntaxException) {
            url
        }
    }
}
