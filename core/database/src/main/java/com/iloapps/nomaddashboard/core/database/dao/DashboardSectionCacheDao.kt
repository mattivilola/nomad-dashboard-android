package com.iloapps.nomaddashboard.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iloapps.nomaddashboard.core.database.entity.DashboardSectionCacheEntity

@Dao
interface DashboardSectionCacheDao {
    @Query("SELECT * FROM dashboard_section_cache")
    suspend fun all(): List<DashboardSectionCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DashboardSectionCacheEntity)
}
