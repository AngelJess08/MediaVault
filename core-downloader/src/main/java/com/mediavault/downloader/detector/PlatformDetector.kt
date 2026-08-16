package com.mediavault.downloader.detector

import com.mediavault.downloader.model.Platform
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformDetector @Inject constructor() {

    fun detect(url: String): Platform {
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be") -> Platform.YOUTUBE
            lowerUrl.contains("instagram.com") -> Platform.INSTAGRAM
            lowerUrl.contains("facebook.com") || lowerUrl.contains("fb.watch") -> Platform.FACEBOOK
            lowerUrl.contains("twitter.com") || lowerUrl.contains("x.com") -> Platform.TWITTER
            lowerUrl.contains("tiktok.com") || lowerUrl.contains("vm.tiktok.com") -> Platform.TIKTOK
            lowerUrl.contains("reddit.com") || lowerUrl.contains("v.redd.it") -> Platform.REDDIT
            lowerUrl.contains("twitch.tv") || lowerUrl.contains("clips.twitch.tv") -> Platform.TWITCH
            lowerUrl.contains("vimeo.com") -> Platform.VIMEO
            lowerUrl.contains("soundcloud.com") -> Platform.SOUNDCLOUD
            lowerUrl.contains("dailymotion.com") || lowerUrl.contains("dai.ly") -> Platform.DAILYMOTION
            lowerUrl.contains("bilibili.com") -> Platform.BILIBILI
            lowerUrl.contains("pinterest.com") || lowerUrl.contains("pin.it") -> Platform.PINTEREST
            lowerUrl.contains("linkedin.com") -> Platform.LINKEDIN
            lowerUrl.contains("snapchat.com") -> Platform.SNAPCHAT
            lowerUrl.startsWith("http") -> Platform.GENERIC
            else -> Platform.UNKNOWN
        }
    }

    fun isPlaylistUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return (lowerUrl.contains("youtube.com/playlist") || lowerUrl.contains("&list="))
    }

    fun normalizeUrl(url: String): String {
        return try {
            val uri = java.net.URI(url)
            val query = uri.query
            if (query != null && (url.contains("youtube.com/watch") || url.contains("youtu.be"))) {
                val params = query.split("&").filter { it.startsWith("v=") || it.startsWith("list=") }
                val newQuery = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
                "${uri.scheme}://${uri.host}${uri.path}$newQuery"
            } else {
                val index = url.indexOf("?")
                if (index != -1 && !url.contains("youtube.com")) {
                    url.substring(0, index)
                } else {
                    url
                }
            }
        } catch (e: Exception) {
            url
        }
    }
}
