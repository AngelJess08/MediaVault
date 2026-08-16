package com.mediavault.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mediavault.storage.db.entity.*
import com.mediavault.storage.db.dao.*

@Database(
    entities = [
        DownloadEntity::class,
        QueueItemEntity::class,
        TagEntity::class,
        DownloadTagCrossRef::class,
        FolderEntity::class,
        SubtitleEntity::class,
        UpscaleJobEntity::class,
        CookieEntity::class,
        OrganizationRuleEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun queueDao(): QueueDao
    abstract fun folderDao(): FolderDao
    abstract fun tagDao(): TagDao
    abstract fun upscaleJobDao(): UpscaleJobDao
    abstract fun cookieDao(): CookieDao
    abstract fun organizationRuleDao(): OrganizationRuleDao
}
