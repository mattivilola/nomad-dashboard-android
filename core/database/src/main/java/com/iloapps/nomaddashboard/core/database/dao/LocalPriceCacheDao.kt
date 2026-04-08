package com.iloapps.nomaddashboard.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iloapps.nomaddashboard.core.database.entity.LocalPriceCacheEntity

@Dao
interface LocalPriceCacheDao {
    @Query("SELECT * FROM local_price_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getByCacheKey(cacheKey: String): LocalPriceCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalPriceCacheEntity)

    @Query("DELETE FROM local_price_cache WHERE cacheKey LIKE 'US|%'")
    suspend fun clearUsEntries()
}
