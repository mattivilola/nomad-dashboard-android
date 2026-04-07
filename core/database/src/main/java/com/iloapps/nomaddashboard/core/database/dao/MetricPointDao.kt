package com.iloapps.nomaddashboard.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iloapps.nomaddashboard.core.database.entity.MetricPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MetricPointDao {
    @Query("SELECT * FROM metric_points WHERE kind = :kind ORDER BY timestampEpochMillis DESC")
    fun observeByKind(kind: String): Flow<List<MetricPointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(point: MetricPointEntity)
}

