package com.iloapps.nomaddashboard.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.iloapps.nomaddashboard.core.database.entity.VisitedCountryDayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitedCountryDayDao {
    @Query("SELECT * FROM visited_country_days ORDER BY dateIso ASC")
    fun observeAll(): Flow<List<VisitedCountryDayEntity>>

    @Query("SELECT * FROM visited_country_days ORDER BY dateIso ASC")
    suspend fun loadAll(): List<VisitedCountryDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<VisitedCountryDayEntity>)

    @Query("DELETE FROM visited_country_days")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<VisitedCountryDayEntity>) {
        clearAll()
        insertAll(items)
    }
}
