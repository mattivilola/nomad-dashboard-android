package com.iloapps.nomaddashboard.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iloapps.nomaddashboard.core.database.entity.TimeTrackingInterruptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeTrackingInterruptionDao {
    @Query("SELECT * FROM time_tracking_interruptions ORDER BY occurredAtEpochMillis DESC")
    fun observeAll(): Flow<List<TimeTrackingInterruptionEntity>>

    @Query("SELECT * FROM time_tracking_interruptions ORDER BY occurredAtEpochMillis DESC")
    suspend fun getAll(): List<TimeTrackingInterruptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(interruption: TimeTrackingInterruptionEntity)
}
