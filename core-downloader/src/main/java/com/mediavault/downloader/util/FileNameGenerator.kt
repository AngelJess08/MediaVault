package com.mediavault.downloader.util

import java.io.File

object FileNameGenerator {

    private val INVALID_CHARS_REGEX = "[\\\\/:*?\"<>|]".toRegex()

    fun generateFileName(title: String, ext: String, quality: String): String {
        val sanitizedTitle = title.replace(INVALID_CHARS_REGEX, "_")
            .replace("\\s+".toRegex(), " ")
            .trim()
        
        val safeTitle = if (sanitizedTitle.length > 100) {
            sanitizedTitle.substring(0, 100).trim()
        } else {
            sanitizedTitle
        }

        val qualitySuffix = if (quality.isNotBlank()) "-$quality" else ""
        return "$safeTitle$qualitySuffix.$ext"
    }
    
    fun getUniqueFile(directory: File, fileName: String): File {
        var file = File(directory, fileName)
        if (!file.exists()) return file

        val nameWithoutExt = file.nameWithoutExtension
        val ext = file.extension
        val dotExt = if (ext.isNotEmpty()) ".$ext" else ""

        var counter = 1
        while (file.exists()) {
            file = File(directory, "$nameWithoutExt ($counter)$dotExt")
            counter++
        }
        return file
    }
}
