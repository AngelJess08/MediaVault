package com.mediavault.storage.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.mediavault.storage.db.entity.CookieEntity

@Dao
interface CookieDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cookie: CookieEntity): Long

    @Update
    suspend fun update(cookie: CookieEntity)

    @Delete
    suspend fun delete(cookie: CookieEntity)

    @Query("SELECT * FROM cookies ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<CookieEntity>>

    @Query("SELECT * FROM cookies WHERE platform = :platform OR domain LIKE '%' || :platform || '%' LIMIT 1")
    suspend fun getByPlatform(platform: String): CookieEntity?

    @Query("SELECT * FROM cookies WHERE domain = :domain LIMIT 1")
    suspend fun getByDomain(domain: String): CookieEntity?

    @Query("DELETE FROM cookies WHERE platform = :platform OR domain = :platform")
    suspend fun deleteByPlatform(platform: String)

    @Query("DELETE FROM cookies WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM cookies")
    suspend fun deleteAll()
}
