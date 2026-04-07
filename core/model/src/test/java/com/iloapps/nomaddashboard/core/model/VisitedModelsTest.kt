package com.iloapps.nomaddashboard.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

class VisitedModelsTest {
    @Test
    fun `storage key normalizes case and diacritics`() {
        val key = visitedPlaceStorageKey(
            countryCode = null,
            country = "España",
            city = "Málaga",
        )

        assertThat(key).isEqualTo("espana|malaga")
    }

    @Test
    fun `storage key falls back to country when city missing`() {
        val key = visitedPlaceStorageKey(
            countryCode = "es",
            country = "Spain",
            city = null,
        )

        assertThat(key).isEqualTo("ES|__country__")
    }

    @Test
    fun `visited place summary counts mappable cities and countries`() {
        val summary = listOf(
            VisitedPlace(
                city = "Malaga",
                region = "Andalusia",
                country = "Spain",
                countryCode = "ES",
                latitude = 36.7,
                longitude = -4.4,
                sources = listOf(VisitedPlaceSource.PUBLIC_IP_GEOLOCATION),
                firstVisitedAt = Instant.parse("2026-01-01T10:00:00Z"),
                lastVisitedAt = Instant.parse("2026-01-02T10:00:00Z"),
            ),
            VisitedPlace(
                city = null,
                region = null,
                country = "Finland",
                countryCode = "FI",
                latitude = null,
                longitude = null,
                sources = listOf(VisitedPlaceSource.DEVICE_LOCATION),
                firstVisitedAt = Instant.parse("2026-01-03T10:00:00Z"),
                lastVisitedAt = Instant.parse("2026-01-04T10:00:00Z"),
            ),
        ).visitedPlaceSummary()

        assertThat(summary.citiesVisited).isEqualTo(1)
        assertThat(summary.countriesVisited).isEqualTo(2)
        assertThat(summary.latestVisitAt).isEqualTo(Instant.parse("2026-01-04T10:00:00Z"))
    }

    @Test
    fun `year and monthly summaries group and sort correctly`() {
        val entries = listOf(
            VisitedCountryDay(
                date = LocalDate.of(2026, 1, 1),
                country = "Finland",
                countryCode = "FI",
                source = VisitedPlaceSource.DEVICE_LOCATION,
                isInferred = false,
            ),
            VisitedCountryDay(
                date = LocalDate.of(2026, 1, 2),
                country = "Spain",
                countryCode = "ES",
                source = VisitedPlaceSource.PUBLIC_IP_GEOLOCATION,
                isInferred = false,
            ),
            VisitedCountryDay(
                date = LocalDate.of(2026, 3, 4),
                country = "Spain",
                countryCode = "ES",
                source = VisitedPlaceSource.DEVICE_LOCATION,
                isInferred = false,
            ),
            VisitedCountryDay(
                date = LocalDate.of(2026, 3, 5),
                country = "Spain",
                countryCode = "ES",
                source = VisitedPlaceSource.DEVICE_LOCATION,
                isInferred = true,
            ),
        )

        val yearSummary = entries.yearSummary(2026)
        val monthly = entries.monthlySummaries(2026)

        assertThat(entries.availableYears()).containsExactly(2026)
        assertThat(yearSummary?.totalTrackedDays).isEqualTo(4)
        assertThat(yearSummary?.items?.map { it.countryCode to it.dayCount }).containsExactly(
            "ES" to 3,
            "FI" to 1,
        ).inOrder()
        assertThat(monthly.map { it.month to it.totalTrackedDays }).containsExactly(
            3 to 2,
            1 to 2,
        ).inOrder()
        assertThat(monthly.first().days.map { it.date.dayOfMonth }).containsExactly(4, 5).inOrder()
    }
}
