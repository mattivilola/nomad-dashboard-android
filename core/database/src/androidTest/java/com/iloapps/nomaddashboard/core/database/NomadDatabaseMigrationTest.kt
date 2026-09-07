package com.iloapps.nomaddashboard.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NomadDatabaseMigrationTest {
    @Test
    fun upgradeFromEightPreservesObservedAndInferredDays() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${java.util.UUID.randomUUID()}.db"
        try {
            // The other tables are unchanged between versions 8 and 9. Recreate the
            // exact v8 country-day table, then let Room perform and validate the upgrade.
            Room.databaseBuilder(context, NomadDatabase::class.java, name).build().useDatabase { database ->
                val sql = database.openHelper.writableDatabase
                sql.execSQL("DROP TABLE visited_country_days")
                sql.execSQL("DROP TABLE travel_stay_reminders")
                sql.execSQL("CREATE TABLE visited_country_days (dateIso TEXT NOT NULL PRIMARY KEY, country TEXT NOT NULL, countryCode TEXT, source TEXT NOT NULL, isInferred INTEGER NOT NULL)")
                sql.execSQL("INSERT INTO visited_country_days VALUES ('2026-09-01', 'Finland', 'FI', 'DEVICE_LOCATION', 0)")
                sql.execSQL("INSERT INTO visited_country_days VALUES ('2026-09-02', 'Finland', 'FI', 'DEVICE_LOCATION', 1)")
                sql.execSQL("PRAGMA user_version=8")
            }
            Room.databaseBuilder(context, NomadDatabase::class.java, name)
                .addMigrations(NomadDatabase.MIGRATION_8_9).build().useDatabase { database ->
                    database.openHelper.writableDatabase.query(
                        "SELECT dateIso, countryCode, provenance, originalCountry, isInferred FROM visited_country_days ORDER BY dateIso",
                    ).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("2026-09-01", cursor.getString(0))
                        assertEquals("FI", cursor.getString(1))
                        assertEquals("OBSERVED", cursor.getString(2))
                        assertTrue(cursor.isNull(3))
                        assertEquals(0, cursor.getInt(4))
                        assertTrue(cursor.moveToNext())
                        assertEquals("INFERRED", cursor.getString(2))
                        assertEquals(1, cursor.getInt(4))
                    }
                }
        } finally {
            context.deleteDatabase(name)
        }
    }
}

private inline fun <T> NomadDatabase.useDatabase(block: (NomadDatabase) -> T): T =
    try { block(this) } finally { close() }
