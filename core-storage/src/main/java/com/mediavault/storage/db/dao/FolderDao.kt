package com.mediavault.storage.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.mediavault.storage.db.entity.FolderEntity

@Dao
interface FolderDao {
    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("SELECT * FROM folders")
    fun getAllFlow(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: Long): FolderEntity?
}
