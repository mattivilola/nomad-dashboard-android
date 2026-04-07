package com.iloapps.nomaddashboard.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.iloapps.nomaddashboard.core.database.dao.MetricPointDao
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingEntryDao
import com.iloapps.nomaddashboard.core.database.dao.VisitedCountryDayDao
import com.iloapps.nomaddashboard.core.database.dao.VisitedPlaceDao
import com.iloapps.nomaddashboard.core.database.entity.MetricPointEntity
import com.iloapps.nomaddashboard.core.database.entity.TimeTrackingEntryEntity
import com.iloapps.nomaddashboard.core.database.entity.VisitedCountryDayEntity
import com.iloapps.nomaddashboard.core.database.entity.VisitedPlaceEntity

@Database(
    entities = [
        MetricPointEntity::class,
        VisitedPlaceEntity::class,
        VisitedCountryDayEntity::class,
        TimeTrackingEntryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class NomadDatabase : RoomDatabase() {
    abstract fun metricPointDao(): MetricPointDao
    abstract fun visitedPlaceDao(): VisitedPlaceDao
    abstract fun visitedCountryDayDao(): VisitedCountryDayDao
    abstract fun timeTrackingEntryDao(): TimeTrackingEntryDao
}

