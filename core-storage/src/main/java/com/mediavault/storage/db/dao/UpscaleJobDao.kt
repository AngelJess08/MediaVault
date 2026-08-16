package com.mediavault.storage.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.mediavault.storage.db.entity.UpscaleJobEntity

@Dao
interface UpscaleJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: UpscaleJobEntity): Long

    @Update
    suspend fun update(job: UpscaleJobEntity)

    @Delete
    suspend fun delete(job: UpscaleJobEntity)

    @Query("SELECT * FROM upscale_jobs ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<UpscaleJobEntity>>

    @Query("SELECT * FROM upscale_jobs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): UpscaleJobEntity?

    @Query("SELECT * FROM upscale_jobs WHERE jobId = :jobId LIMIT 1")
    suspend fun getByJobId(jobId: String): UpscaleJobEntity?

    @Query("SELECT * FROM upscale_jobs WHERE status IN ('PENDING', 'PROCESSING', 'UPLOADING')")
    suspend fun getActiveJobs(): List<UpscaleJobEntity>
}
