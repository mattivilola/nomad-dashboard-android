package com.iloapps.nomaddashboard.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iloapps.nomaddashboard.core.database.entity.TimeTrackingEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeTrackingEntryDao {
    @Query("SELECT * FROM time_tracking_entries ORDER BY startAtEpochMillis DESC")
    fun observeAll(): Flow<List<TimeTrackingEntryEntity>>

    @Query("SELECT * FROM time_tracking_entries WHERE endAtEpochMillis IS NOT NULL ORDER BY startAtEpochMillis DESC")
    fun observeCompleted(): Flow<List<TimeTrackingEntryEntity>>

    @Query("SELECT * FROM time_tracking_entries WHERE endAtEpochMillis IS NULL ORDER BY startAtEpochMillis DESC LIMIT 1")
    fun observeActive(): Flow<TimeTrackingEntryEntity?>

    @Query("SELECT * FROM time_tracking_entries WHERE endAtEpochMillis IS NULL ORDER BY startAtEpochMillis DESC LIMIT 1")
    suspend fun getActive(): TimeTrackingEntryEntity?

    @Query("SELECT * FROM time_tracking_entries ORDER BY startAtEpochMillis DESC")
    suspend fun getAll(): List<TimeTrackingEntryEntity>

    @Query("SELECT * FROM time_tracking_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TimeTrackingEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: TimeTrackingEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<TimeTrackingEntryEntity>)
}
