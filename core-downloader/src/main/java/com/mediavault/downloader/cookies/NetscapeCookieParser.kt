package com.mediavault.downloader.cookies

import com.mediavault.storage.db.entity.CookieEntity
import java.io.File

object NetscapeCookieParser {

    fun parse(content: String): List<CookieEntity> {
        val lines = content.lines()
        val cookiesByDomain = mutableMapOf<String, MutableList<String>>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") && !trimmed.startsWith("#HttpOnly_")) {
                continue
            }

            val cleanLine = if (trimmed.startsWith("#HttpOnly_")) trimmed.removePrefix("#HttpOnly_") else trimmed
            val parts = cleanLine.split("\t")
            if (parts.size >= 7) {
                val domain = parts[0].trim().removePrefix(".")
                val name = parts[5].trim()
                val value = parts[6].trim()
                val cookiePair = "$name=$value"

                val list = cookiesByDomain.getOrPut(domain) { mutableListOf() }
                list.add(cookiePair)
            }
        }

        val result = mutableListOf<CookieEntity>()
        for ((domain, cookiePairs) in cookiesByDomain) {
            val platform = when {
                domain.contains("twitter") || domain.contains("x.com") -> "TWITTER"
                domain.contains("instagram") -> "INSTAGRAM"
                domain.contains("facebook") -> "FACEBOOK"
                domain.contains("youtube") || domain.contains("google") -> "YOUTUBE"
                domain.contains("tiktok") -> "TIKTOK"
                domain.contains("reddit") -> "REDDIT"
                else -> domain
            }

            val fullCookieString = cookiePairs.joinToString("; ")
            result.add(
                CookieEntity(
                    platform = platform,
                    domain = domain,
                    cookieString = fullCookieString,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        return result
    }

    fun parseFile(file: File): List<CookieEntity> {
        return parse(file.readText())
    }
}
