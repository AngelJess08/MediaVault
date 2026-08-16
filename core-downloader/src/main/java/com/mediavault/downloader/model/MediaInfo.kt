package com.mediavault.downloader.model

data class MediaInfo(
    val url: String,
    val title: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Long = 0L, // segundos
    val platform: Platform = Platform.GENERIC,
    val uploader: String? = null,
    val uploadDate: String? = null,
    val isPlaylist: Boolean = false,
    val playlistItems: List<PlaylistItem>? = null,
    val formats: List<FormatOption> = emptyList(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val isLive: Boolean = false
)

data class FormatOption(
    val formatId: String,
    val ext: String = "mp4",       // mp4, webm, mkv, m4a, opus, mp3, etc.
    val resolution: String? = null,  // "1080p", "720p", "4K", etc.
    val fps: Float? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val filesize: Long? = null,   // bytes, puede ser null si estimado
    val filesizeApprox: Long? = null,
    val tbr: Float? = null,       // total bitrate
    val vbr: Float? = null,
    val abr: Float? = null,
    val quality: Int = 0,
    val isAudioOnly: Boolean = false,
    val isVideoOnly: Boolean = false,
    val height: Int? = null,
    val width: Int? = null,
    val dynamicRange: String? = "SDR",
    val language: String? = null,
    val isNative: Boolean = true,
    val isAiUpscaled: Boolean = false,
    val streamUrl: String? = null,
    val audioStreamUrl: String? = null
)

data class SubtitleTrack(
    val language: String,
    val languageName: String,
    val url: String?,
    val ext: String
)

data class PlaylistItem(
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val duration: Long?,
    val uploader: String?
)

enum class Platform {
    YOUTUBE, INSTAGRAM, FACEBOOK, TWITTER, TIKTOK,
    REDDIT, TWITCH, VIMEO, SOUNDCLOUD, DAILYMOTION,
    BILIBILI, PINTEREST, LINKEDIN, SNAPCHAT,
    GENERIC, UNKNOWN
}
