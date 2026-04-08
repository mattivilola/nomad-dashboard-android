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

    @Query("SELECT * FROM metric_points WHERE kind = :kind ORDER BY timestampEpochMillis DESC LIMIT :limit")
    suspend fun recentByKind(kind: String, limit: Int): List<MetricPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(point: MetricPointEntity)

    @Insert
    suspend fun insert(point: MetricPointEntity): Long

    @Query(
        """
        DELETE FROM metric_points
        WHERE kind = :kind
          AND id NOT IN (
              SELECT id
              FROM metric_points
              WHERE kind = :kind
              ORDER BY timestampEpochMillis DESC
              LIMIT :keepCount
          )
        """,
    )
    suspend fun trimToLatest(kind: String, keepCount: Int)
}
