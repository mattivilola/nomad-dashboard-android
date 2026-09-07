package com.iloapps.nomaddashboard.core.data.visited

import com.iloapps.nomaddashboard.core.model.TravelHistoryBackup
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class TravelHistoryBackupValidationTest {
    @Test
    fun `duplicate place identity is rejected before import can overwrite it`() {
        val place = VisitedPlace(
            city = "Lisbon",
            region = null,
            country = "Portugal",
            countryCode = "PT",
            latitude = 38.7,
            longitude = -9.1,
            sources = listOf(VisitedPlaceSource.DEVICE_LOCATION),
            firstVisitedAt = Instant.parse("2026-07-01T08:00:00Z"),
            lastVisitedAt = Instant.parse("2026-07-01T08:00:00Z"),
        )

        val error = runCatching {
            validateTravelHistoryBackup(
                TravelHistoryBackup(
                    exportedAt = Instant.parse("2026-07-02T08:00:00Z"),
                    places = listOf(place, place.copy(latitude = 41.0)),
                    placeEvents = emptyList(),
                    countryDays = emptyList(),
                    stayReminders = emptyList(),
                ),
            )
        }.exceptionOrNull()

        assertThat(error).isNotNull()
        assertThat(error?.message).contains("duplicate place IDs")
    }
}
