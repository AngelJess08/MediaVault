package com.mediavault.storage.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.mediavault.storage.db.entity.DownloadEntity

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity)

    @Delete
    suspend fun delete(download: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE inTrash = 0")
    suspend fun getAll(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE inTrash = 0")
    fun getAllFlow(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE platform = :platform AND inTrash = 0")
    suspend fun getByPlatform(platform: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE title LIKE '%' || :query || '%' AND inTrash = 0")
    suspend fun searchByTitle(query: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE downloadedAt BETWEEN :from AND :to AND inTrash = 0")
    suspend fun getByDateRange(from: Long, to: Long): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE type = :type AND inTrash = 0")
    suspend fun getByType(type: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE isFavorite = 1 AND inTrash = 0")
    suspend fun getFavorites(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE folderId = :folderId AND inTrash = 0")
    suspend fun getByFolder(folderId: Long): List<DownloadEntity>

    @Query("SELECT d.* FROM downloads d INNER JOIN download_tag_cross_ref dt ON d.id = dt.downloadId INNER JOIN tags t ON dt.tagId = t.id WHERE t.name = :tagName AND d.inTrash = 0")
    suspend fun getByTag(tagName: String): List<DownloadEntity>

    @Query("SELECT SUM(fileSize) FROM downloads WHERE inTrash = 0")
    suspend fun getTotalSize(): Long

    @Query("SELECT COUNT(*) FROM downloads WHERE platform = :platform AND inTrash = 0")
    suspend fun getCountByPlatform(platform: String): Int

    @Query("SELECT * FROM downloads WHERE inTrash = 0 ORDER BY downloadedAt DESC LIMIT :limit")
    suspend fun getRecentDownloads(limit: Int): List<DownloadEntity>

    @Query("UPDATE downloads SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun markAsFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE downloads SET folderId = :folderId WHERE id = :id")
    suspend fun moveToFolder(id: Long, folderId: Long)

    @Query("SELECT * FROM downloads WHERE inTrash = 1")
    suspend fun getInTrash(): List<DownloadEntity>

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun permanentDelete(id: Long)

    @Query("UPDATE downloads SET inTrash = 0 WHERE id = :id")
    suspend fun restoreFromTrash(id: Long)

    @Query("DELETE FROM downloads WHERE downloadedAt < :timestamp AND inTrash = 1")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("SELECT COUNT(*) FROM downloads WHERE inTrash = 0")
    suspend fun totalDownloaded(): Int

    @Query("SELECT SUM(fileSize) FROM downloads WHERE platform = :platform AND inTrash = 0")
    suspend fun totalSizeByPlatform(platform: String): Long

    @Query("SELECT COUNT(*) FROM downloads WHERE downloadedAt > :weekStart AND inTrash = 0")
    suspend fun downloadsThisWeek(weekStart: Long): Int
}
