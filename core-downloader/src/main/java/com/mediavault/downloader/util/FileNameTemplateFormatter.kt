package com.mediavault.downloader.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileNameTemplateFormatter {

    /**
     * Formatea un nombre de archivo a partir de una plantilla dada.
     * Tokens disponibles:
     * - {plataforma} / {platform}
     * - {titulo} / {title}
     * - {fecha} / {date}
     * - {calidad} / {quality}
     * - {autor} / {uploader}
     */
    fun formatFileName(
        template: String = "{plataforma}_{fecha}_{titulo}",
        platform: String,
        title: String,
        quality: String? = null,
        uploader: String? = null
    ): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val currentDate = dateFormat.format(Date())

        val sanitizedTitle = sanitizeForFilename(title)
        val sanitizedAuthor = sanitizeForFilename(uploader ?: "MediaVault")
        val sanitizedPlatform = sanitizeForFilename(platform)

        var result = template
            .replace("{plataforma}", sanitizedPlatform, ignoreCase = true)
            .replace("{platform}", sanitizedPlatform, ignoreCase = true)
            .replace("{titulo}", sanitizedTitle, ignoreCase = true)
            .replace("{title}", sanitizedTitle, ignoreCase = true)
            .replace("{fecha}", currentDate, ignoreCase = true)
            .replace("{date}", currentDate, ignoreCase = true)
            .replace("{calidad}", quality ?: "HD", ignoreCase = true)
            .replace("{quality}", quality ?: "HD", ignoreCase = true)
            .replace("{autor}", sanitizedAuthor, ignoreCase = true)
            .replace("{uploader}", sanitizedAuthor, ignoreCase = true)

        if (result.isBlank()) {
            result = "${sanitizedPlatform}_${currentDate}_$sanitizedTitle"
        }

        return result
    }

    fun sanitizeForFilename(input: String): String {
        // Quitar caracteres no permitidos en sistemas de archivos y path traversal (Función 14)
        return input.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_")
            .replace("..", "_")
            .trim()
            .take(80)
    }
}
