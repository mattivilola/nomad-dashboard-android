package com.iloapps.nomaddashboard.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.iloapps.nomaddashboard.core.database.dao.MetricPointDao
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingEntryDao
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingProjectDao
import com.iloapps.nomaddashboard.core.database.dao.VisitedCountryDayDao
import com.iloapps.nomaddashboard.core.database.dao.VisitedPlaceDao
import com.iloapps.nomaddashboard.core.database.entity.MetricPointEntity
import com.iloapps.nomaddashboard.core.database.entity.TimeTrackingEntryEntity
import com.iloapps.nomaddashboard.core.database.entity.TimeTrackingProjectEntity
import com.iloapps.nomaddashboard.core.database.entity.VisitedCountryDayEntity
import com.iloapps.nomaddashboard.core.database.entity.VisitedPlaceEntity

@Database(
    entities = [
        MetricPointEntity::class,
        VisitedPlaceEntity::class,
        VisitedCountryDayEntity::class,
        TimeTrackingProjectEntity::class,
        TimeTrackingEntryEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(NomadDatabaseConverters::class)
abstract class NomadDatabase : RoomDatabase() {
    abstract fun metricPointDao(): MetricPointDao
    abstract fun visitedPlaceDao(): VisitedPlaceDao
    abstract fun visitedCountryDayDao(): VisitedCountryDayDao
    abstract fun timeTrackingProjectDao(): TimeTrackingProjectDao
    abstract fun timeTrackingEntryDao(): TimeTrackingEntryDao

    companion object {
        private const val LEGACY_TIME_TRACKING_PROJECT_ID = "00000000-0000-0000-0000-000000000001"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS visited_places_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        city TEXT,
                        region TEXT,
                        country TEXT NOT NULL,
                        countryCode TEXT,
                        latitude REAL,
                        longitude REAL,
                        sources TEXT NOT NULL,
                        firstVisitedAtEpochMillis INTEGER NOT NULL,
                        lastVisitedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT INTO visited_places_new (
                        id,
                        city,
                        region,
                        country,
                        countryCode,
                        latitude,
                        longitude,
                        sources,
                        firstVisitedAtEpochMillis,
                        lastVisitedAtEpochMillis
                    )
                    SELECT
                        id,
                        city,
                        region,
                        country,
                        countryCode,
                        latitude,
                        longitude,
                        source,
                        firstVisitedAtEpochMillis,
                        lastVisitedAtEpochMillis
                    FROM visited_places
                    """.trimIndent(),
                )
                database.execSQL("DROP TABLE visited_places")
                database.execSQL("ALTER TABLE visited_places_new RENAME TO visited_places")

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS visited_country_days_new (
                        dateIso TEXT NOT NULL PRIMARY KEY,
                        country TEXT NOT NULL,
                        countryCode TEXT,
                        source TEXT NOT NULL,
                        isInferred INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT INTO visited_country_days_new (
                        dateIso,
                        country,
                        countryCode,
                        source,
                        isInferred
                    )
                    SELECT
                        dateIso,
                        country,
                        countryCode,
                        'PUBLIC_IP_GEOLOCATION',
                        0
                    FROM visited_country_days
                    """.trimIndent(),
                )
                database.execSQL("DROP TABLE visited_country_days")
                database.execSQL("ALTER TABLE visited_country_days_new RENAME TO visited_country_days")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS time_tracking_projects (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        isArchived INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT INTO time_tracking_projects (id, name, isArchived)
                    SELECT '$LEGACY_TIME_TRACKING_PROJECT_ID', 'Imported entries', 0
                    WHERE EXISTS (SELECT 1 FROM time_tracking_entries)
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    ALTER TABLE time_tracking_entries
                    ADD COLUMN projectId TEXT NOT NULL DEFAULT '$LEGACY_TIME_TRACKING_PROJECT_ID'
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    UPDATE time_tracking_entries
                    SET projectId = '$LEGACY_TIME_TRACKING_PROJECT_ID'
                    WHERE projectId IS NULL OR projectId = ''
                    """.trimIndent(),
                )
            }
        }
    }
}
