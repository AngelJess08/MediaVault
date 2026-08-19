package com.mediavault.downloader.util

import java.util.regex.Pattern

data class MediaChapter(
    val title: String,
    val startSeconds: Long,
    val timeFormatted: String
)

object ChapterDetector {

    /**
     * Parsea marcas de tiempo desde la descripción del video como:
     * 00:00 - Introducción
     * 01:45 Capítulo 1
     * 10:20 - Fin
     */
    fun parseChapters(description: String?): List<MediaChapter> {
        if (description.isNullOrBlank()) return emptyList()

        val chapters = mutableListOf<MediaChapter>()
        // Formato (HH:)?MM:SS seguido del título
        val pattern = Pattern.compile("(?:(\\d{1,2}):)?(\\d{2}):(\\d{2})\\s*[-–—]?\\s*([^\n\r]+)")
        val lines = description.lines()

        for (line in lines) {
            val matcher = pattern.matcher(line.trim())
            if (matcher.find()) {
                val hoursStr = matcher.group(1)
                val minutesStr = matcher.group(2) ?: "00"
                val secondsStr = matcher.group(3) ?: "00"
                val chapterTitle = matcher.group(4)?.trim() ?: "Capítulo"

                val hours = hoursStr?.toLongOrNull() ?: 0L
                val minutes = minutesStr.toLongOrNull() ?: 0L
                val seconds = secondsStr.toLongOrNull() ?: 0L

                val totalSeconds = (hours * 3600) + (minutes * 60) + seconds
                val timeFormatted = if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds) else String.format("%02d:%02d", minutes, seconds)

                chapters.add(
                    MediaChapter(
                        title = chapterTitle,
                        startSeconds = totalSeconds,
                        timeFormatted = timeFormatted
                    )
                )
            }
        }
        return chapters.sortedBy { it.startSeconds }
    }
}
