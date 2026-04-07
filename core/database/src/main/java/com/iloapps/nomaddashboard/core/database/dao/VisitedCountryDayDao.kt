package com.iloapps.nomaddashboard.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iloapps.nomaddashboard.core.database.entity.VisitedCountryDayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitedCountryDayDao {
    @Query("SELECT * FROM visited_country_days ORDER BY dateIso DESC")
    fun observeAll(): Flow<List<VisitedCountryDayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: VisitedCountryDayEntity)
}

