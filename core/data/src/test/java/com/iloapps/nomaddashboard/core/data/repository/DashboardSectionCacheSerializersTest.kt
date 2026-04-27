package com.iloapps.nomaddashboard.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Test

class DashboardSectionCacheSerializersTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `travel context cache round trips with generated serializer`() {
        val snapshot = TravelContextSnapshot(
            publicIp = "198.51.100.12",
            city = "Colmar",
            region = "Grand Est",
            country = "France",
            countryCode = "FR",
            latitude = 48.079,
            longitude = 7.358,
            timeZoneId = "Europe/Paris",
            deviceCity = "Colmar",
            deviceCountry = "France",
            deviceLatitude = 48.0835,
            deviceLongitude = 7.3553,
        )

        val entity = snapshot.toCacheEntity(json = json, fetchedAt = Instant.ofEpochMilli(1234))
        val cached = loadCachedDashboardSections(entries = listOf(entity), json = json)

        assertThat(cached.travelContext).isEqualTo(snapshot)
        assertThat(cached.lastRefresh).isEqualTo(Instant.ofEpochMilli(1234))
    }
}
