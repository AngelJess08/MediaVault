package com.mediavault.downloader.util

import java.util.Locale

object FileSizeFormatter {

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun formatSpeed(bytesPerSecond: Float): String {
        if (bytesPerSecond <= 0f) return "0 B/s"
        val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
        val digitGroups = (Math.log10(bytesPerSecond.toDouble()) / Math.log10(1024.0)).toInt()
        val index = Math.min(digitGroups, units.size - 1)
        return String.format(Locale.US, "%.1f %s", bytesPerSecond / Math.pow(1024.0, index.toDouble()), units[index])
    }

    fun formatEta(seconds: Int): String {
        if (seconds < 0) return "Desconocido"
        if (seconds == 0) return "0s"
        
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60

        return buildString {
            if (h > 0) append("${h}h ")
            if (m > 0 || h > 0) append("${m}m ")
            append("${s}s")
        }.trim()
    }

    fun formatDuration(seconds: Long): String {
        if (seconds < 0) return "00:00"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60

        return if (h > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }
}
