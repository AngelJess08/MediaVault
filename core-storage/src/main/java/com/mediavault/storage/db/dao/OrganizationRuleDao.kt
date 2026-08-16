package com.mediavault.storage.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.mediavault.storage.db.entity.OrganizationRuleEntity

@Dao
interface OrganizationRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: OrganizationRuleEntity): Long

    @Update
    suspend fun update(rule: OrganizationRuleEntity)

    @Delete
    suspend fun delete(rule: OrganizationRuleEntity)

    @Query("SELECT * FROM organization_rules")
    fun getAllFlow(): Flow<List<OrganizationRuleEntity>>

    @Query("SELECT * FROM organization_rules WHERE platform = :platform OR platform IS NULL")
    suspend fun getRulesForPlatform(platform: String): List<OrganizationRuleEntity>
}
