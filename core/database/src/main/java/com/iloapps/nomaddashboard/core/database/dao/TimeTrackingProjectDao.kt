package com.iloapps.nomaddashboard.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iloapps.nomaddashboard.core.database.entity.TimeTrackingProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeTrackingProjectDao {
    @Query("SELECT * FROM time_tracking_projects WHERE isArchived = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TimeTrackingProjectEntity>>

    @Query("SELECT * FROM time_tracking_projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TimeTrackingProjectEntity?

    @Query("SELECT * FROM time_tracking_projects WHERE lower(name) = lower(:name) LIMIT 1")
    suspend fun findByName(name: String): TimeTrackingProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: TimeTrackingProjectEntity)
}
