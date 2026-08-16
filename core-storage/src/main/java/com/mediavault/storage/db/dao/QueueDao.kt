package com.mediavault.storage.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.mediavault.storage.db.entity.QueueItemEntity

@Dao
interface QueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: QueueItemEntity): Long

    @Update
    suspend fun update(item: QueueItemEntity)

    @Delete
    suspend fun delete(item: QueueItemEntity)

    @Query("SELECT * FROM queue_items")
    fun getAllQueued(): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM queue_items WHERE status = :status")
    suspend fun getByStatus(status: String): List<QueueItemEntity>

    @Query("SELECT * FROM queue_items WHERE status = 'PENDING' ORDER BY priority DESC, createdAt ASC")
    suspend fun getPendingItems(): List<QueueItemEntity>

    @Query("UPDATE queue_items SET progress = :progress, speed = :speed, eta = :eta WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Int, speed: Long, eta: Long)

    @Query("UPDATE queue_items SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("SELECT * FROM queue_items WHERE status = 'PENDING' ORDER BY priority DESC, createdAt ASC LIMIT 1")
    suspend fun getNextPending(): QueueItemEntity?

    @Query("UPDATE queue_items SET status = 'CANCELLED' WHERE status IN ('PENDING', 'DOWNLOADING', 'PAUSED')")
    suspend fun cancelAll()

    @Query("UPDATE queue_items SET status = 'PAUSED' WHERE status IN ('PENDING', 'DOWNLOADING')")
    suspend fun pauseAll()

    @Query("SELECT * FROM queue_items WHERE scheduledAt IS NOT NULL AND scheduledAt <= :now")
    suspend fun getScheduled(now: Long): List<QueueItemEntity>
}
