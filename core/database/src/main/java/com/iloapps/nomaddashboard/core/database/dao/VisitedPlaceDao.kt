package com.iloapps.nomaddashboard.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iloapps.nomaddashboard.core.database.entity.VisitedPlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitedPlaceDao {
    @Query("SELECT * FROM visited_places ORDER BY lastVisitedAtEpochMillis DESC")
    fun observeAll(): Flow<List<VisitedPlaceEntity>>

    @Query("SELECT * FROM visited_places WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VisitedPlaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(place: VisitedPlaceEntity)

    @Query("DELETE FROM visited_places")
    suspend fun clearAll()
}
