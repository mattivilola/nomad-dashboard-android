package com.iloapps.nomaddashboard.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iloapps.nomaddashboard.core.database.entity.DashboardSectionCacheEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardSectionCacheDaoTest {
    @Test
    fun deleteRemovesOnlyTheNamedSection() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NomadDatabase::class.java).build()
        try {
            val dao = database.dashboardSectionCacheDao()
            dao.upsert(DashboardSectionCacheEntity("air_quality", "{}", 1))
            dao.upsert(DashboardSectionCacheEntity("weather", "{}", 2))

            dao.delete("air_quality")
            dao.delete("missing")

            assertEquals(listOf("weather"), dao.all().map { it.sectionId })
        } finally {
            database.close()
        }
    }
}
