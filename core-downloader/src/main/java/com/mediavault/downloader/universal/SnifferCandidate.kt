package com.mediavault.downloader.universal

/**
 * Representa un candidato multimedia detectado durante la intercepción de tráfico de red
 * en el Modo Universal (WebView Sniffer).
 */
data class SnifferCandidate(
    val url: String,
    val mimeType: String? = null,
    val extension: String = "mp4",
    val contentLength: Long? = null,
    val isHls: Boolean = false,
    val isDash: Boolean = false,
    val isAudioOnly: Boolean = false,
    val estimatedResolution: String? = null,
    val title: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val isHeuristicAcceptHeader: Boolean = false
)
