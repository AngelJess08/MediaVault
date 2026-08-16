package com.mediavault.storage.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.mediavault.storage.db.entity.TagEntity

@Dao
interface TagDao {
    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Delete
    suspend fun delete(tag: TagEntity)

    @Query("SELECT * FROM tags")
    fun getAllFlow(): Flow<List<TagEntity>>
}
