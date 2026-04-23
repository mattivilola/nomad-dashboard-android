package com.iloapps.nomaddashboard.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.iloapps.nomaddashboard.core.database.dao.MetricPointDao
import com.iloapps.nomaddashboard.core.database.dao.DashboardSectionCacheDao
import com.iloapps.nomaddashboard.core.database.dao.LocalPriceCacheDao
import com.iloapps.nomaddashboard.core.database.dao.LocalInfoCacheDao
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingEntryDao
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingInterruptionDao
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingProjectDao
import com.iloapps.nomaddashboard.core.database.dao.VisitedCountryDayDao
import com.iloapps.nomaddashboard.core.database.dao.VisitedPlaceDao
import com.iloapps.nomaddashboard.core.database.entity.MetricPointEntity
import com.iloapps.nomaddashboard.core.database.entity.DashboardSectionCacheEntity
import com.iloapps.nomaddashboard.core.database.entity.LocalPriceCacheEntity
import com.iloapps.nomaddashboard.core.database.entity.LocalInfoCacheEntity
import com.iloapps.nomaddashboard.core.database.entity.TimeTrackingEntryEntity
import com.iloapps.nomaddashboard.core.database.entity.TimeTrackingInterruptionEntity
import com.iloapps.nomaddashboard.core.database.entity.TimeTrackingProjectEntity
import com.iloapps.nomaddashboard.core.database.entity.VisitedCountryDayEntity
import com.iloapps.nomaddashboard.core.database.entity.VisitedPlaceEntity

@Database(
    entities = [
        MetricPointEntity::class,
        DashboardSectionCacheEntity::class,
        LocalPriceCacheEntity::class,
        LocalInfoCacheEntity::class,
        VisitedPlaceEntity::class,
        VisitedCountryDayEntity::class,
        TimeTrackingProjectEntity::class,
        TimeTrackingEntryEntity::class,
        TimeTrackingInterruptionEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
@TypeConverters(NomadDatabaseConverters::class)
abstract class NomadDatabase : RoomDatabase() {
    abstract fun metricPointDao(): MetricPointDao
    abstract fun dashboardSectionCacheDao(): DashboardSectionCacheDao
    abstract fun localPriceCacheDao(): LocalPriceCacheDao
    abstract fun localInfoCacheDao(): LocalInfoCacheDao
    abstract fun visitedPlaceDao(): VisitedPlaceDao
    abstract fun visitedCountryDayDao(): VisitedCountryDayDao
    abstract fun timeTrackingProjectDao(): TimeTrackingProjectDao
    abstract fun timeTrackingEntryDao(): TimeTrackingEntryDao
    abstract fun timeTrackingInterruptionDao(): TimeTrackingInterruptionDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_price_cache (
                        cacheKey TEXT NOT NULL PRIMARY KEY,
                        status TEXT NOT NULL,
                        summaryBand TEXT,
                        countryCode TEXT,
                        countryName TEXT,
                        rowsJson TEXT NOT NULL,
                        sourcesJson TEXT NOT NULL,
                        fetchedAtEpochMillis INTEGER,
                        detail TEXT,
                        note TEXT
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS time_tracking_interruptions (
                        id TEXT NOT NULL PRIMARY KEY,
                        entryId TEXT,
                        occurredAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_info_cache (
                        cacheKey TEXT NOT NULL PRIMARY KEY,
                        payloadJson TEXT NOT NULL,
                        fetchedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS dashboard_section_cache (
                        sectionId TEXT NOT NULL PRIMARY KEY,
                        payloadJson TEXT NOT NULL,
                        fetchedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
