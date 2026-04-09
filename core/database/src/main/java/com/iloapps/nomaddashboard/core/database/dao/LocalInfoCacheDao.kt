package com.iloapps.nomaddashboard.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iloapps.nomaddashboard.core.database.entity.LocalInfoCacheEntity

@Dao
interface LocalInfoCacheDao {
    @Query("SELECT * FROM local_info_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getByCacheKey(cacheKey: String): LocalInfoCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalInfoCacheEntity)
}
