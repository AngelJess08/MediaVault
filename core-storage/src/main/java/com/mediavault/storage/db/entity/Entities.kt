package com.mediavault.storage.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val platform: String,
    val filePath: String,
    val thumbnailPath: String?,
    val fileSize: Long,
    val downloadedAt: Long,
    val format: String,
    val type: String,
    val status: String,
    val duration: Long,
    val tags: String = "",
    val isFavorite: Boolean = false,
    val isPrivate: Boolean = false,
    val folderId: Long? = null,
    val inTrash: Boolean = false,
    val audioBitrate: String? = null,
    val videoResolution: String? = null,
    val videoFps: Int? = null,
    val author: String? = null
)

@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val platform: String,
    val selectedFormat: String,
    val selectedQuality: String,
    val audioFormat: String? = null,
    val audioBitrate: String? = null,
    val scheduledAt: Long? = null,
    val priority: Int = 0,
    val status: String = "QUEUED",
    val progress: Int = 0,
    val speed: Long = 0,
    val eta: Long = 0,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val downloadStart: Long? = null,
    val trimStart: Long? = null,
    val trimEnd: Long? = null,
    val burnSubtitles: Boolean = false,
    val subtitleLang: String? = null,
    val downloadThumbnailOnly: Boolean = false,
    val isPrivate: Boolean = false,
    val tags: String = ""
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: String = "#3D5A99"
)

@Entity(tableName = "download_tag_cross_ref", primaryKeys = ["downloadId", "tagId"])
data class DownloadTagCrossRef(
    val downloadId: Long,
    val tagId: Long
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val path: String,
    val colorCode: String = "#3D5A99",
    val isAutomatic: Boolean = false,
    val rulePattern: String? = null
)

@Entity(tableName = "subtitles")
data class SubtitleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val downloadId: Long,
    val language: String,
    val filePath: String
)

@Entity(tableName = "upscale_jobs")
data class UpscaleJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceDownloadId: Long,
    val status: String = "PENDING",
    val provider: String = "replicate",
    val targetResolution: String = "2x",
    val targetFps: Int = 60,
    val jobId: String = "",
    val resultFilePath: String? = null,
    val estimatedCost: Double = 0.0,
    val estimatedTime: Long = 0,
    val progress: Float = 0f,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val errorMessage: String? = null
)

@Entity(tableName = "cookies")
data class CookieEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platform: String,
    val domain: String,
    val cookieString: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "organization_rules")
data class OrganizationRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val platform: String?,
    val matchKeyword: String?,
    val targetFolderId: Long,
    val mediaType: String? = null
)
