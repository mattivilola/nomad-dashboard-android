package com.iloapps.nomaddashboard.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iloapps.nomaddashboard.core.database.entity.VisitedPlaceEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitedPlaceEventDao {
    @Query("SELECT * FROM visited_place_events ORDER BY observedDayIso ASC, firstObservedAtEpochMillis ASC")
    fun observeAll(): Flow<List<VisitedPlaceEventEntity>>

    @Query("SELECT * FROM visited_place_events WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VisitedPlaceEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: VisitedPlaceEventEntity)

    @Query("DELETE FROM visited_place_events")
    suspend fun clearAll()
}
