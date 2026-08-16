package com.mediavault.downloader.model

data class MediaInfo(
    val url: String,
    val title: String,
    val description: String?,
    val thumbnailUrl: String?,
    val duration: Long, // segundos
    val platform: Platform,
    val uploader: String?,
    val uploadDate: String?,
    val isPlaylist: Boolean,
    val playlistItems: List<PlaylistItem>?,
    val formats: List<FormatOption>,
    val subtitles: List<SubtitleTrack>,
    val isLive: Boolean
)

data class FormatOption(
    val formatId: String,
    val ext: String,       // mp4, webm, mkv, m4a, opus, mp3, etc.
    val resolution: String?,  // "1920x1080", null para audio
    val fps: Float?,
    val vcodec: String?,
    val acodec: String?,
    val filesize: Long?,   // bytes, puede ser null si estimado
    val filesizeApprox: Long?,
    val tbr: Float?,       // total bitrate
    val vbr: Float?,
    val abr: Float?,
    val quality: Int,
    val isAudioOnly: Boolean,
    val isVideoOnly: Boolean,
    val height: Int?,
    val width: Int?,
    val dynamicRange: String?, // SDR, HDR10, HDR12, etc.
    val language: String?
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
