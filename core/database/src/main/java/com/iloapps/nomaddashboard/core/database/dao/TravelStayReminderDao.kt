package com.iloapps.nomaddashboard.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iloapps.nomaddashboard.core.database.entity.TravelStayReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TravelStayReminderDao {
    @Query("SELECT * FROM travel_stay_reminders ORDER BY targetDateIso ASC")
    fun observeAll(): Flow<List<TravelStayReminderEntity>>

    @Query("SELECT * FROM travel_stay_reminders ORDER BY targetDateIso ASC")
    suspend fun loadAll(): List<TravelStayReminderEntity>

    @Query("SELECT * FROM travel_stay_reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TravelStayReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TravelStayReminderEntity)

    @Query("DELETE FROM travel_stay_reminders WHERE id = :id")
    suspend fun deleteById(id: String)
}
