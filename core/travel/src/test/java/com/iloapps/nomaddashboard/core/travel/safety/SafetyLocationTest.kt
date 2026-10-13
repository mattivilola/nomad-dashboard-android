package com.iloapps.nomaddashboard.core.travel.safety

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import java.util.Locale
import org.junit.Test

class SafetyLocationTest {
    private val index = DiplomaticMissionIndex(
        listOf(
            MissionFixtures.mission("FI-RAB", "FI", DiplomaticMissionKind.Embassy, "Embassy of Finland in Rabat", MissionFixtures.rabat)
                .copy(city = "Rabat"),
            MissionFixtures.mission(
                "FI-CAS", "FI", DiplomaticMissionKind.Consulate, "Consulate of Finland in Casablanca", MissionFixtures.casablanca,
            ).copy(city = "Casablanca", isApproximateLocation = true),
            MissionFixtures.mission("BE-RAB", "BE", DiplomaticMissionKind.Embassy, "Embassy of Belgium in Rabat", MissionFixtures.rabat)
                .copy(city = "Rabat"),
        ),
        source = "Wikidata (CC0)",
        generatedAt = "2026-10-05",
    )
    private val builder = EmbassyOverviewBuilder(Locale.US, indexProvider = { index })

    @Test
    fun `the device location wins over the IP lookup`() {
        val location = SafetyLocation.of(
            TravelContextSnapshot(
                countryCode = "ES", latitude = 40.4, longitude = -3.7,
                deviceCountryCode = "MA", deviceLatitude = 34.02, deviceLongitude = -6.84,
            ),
            localInfoCountryCode = "FR",
        )

        assertThat(location.countryCode).isEqualTo("MA")
        assertThat(location.coordinate).isEqualTo(MissionCoordinate(34.02, -6.84))
        assertThat(location.isCoordinateApproximate).isFalse()
    }

    @Test
    fun `an IP only location is approximate`() {
        val location = SafetyLocation.of(
            TravelContextSnapshot(countryCode = "MA", latitude = 33.57, longitude = -7.59),
            localInfoCountryCode = null,
        )

        assertThat(location.countryCode).isEqualTo("MA")
        assertThat(location.coordinate).isEqualTo(MissionCoordinate(33.57, -7.59))
        assertThat(location.isCoordinateApproximate).isTrue()
    }

    @Test
    fun `the country falls back to Local Info and no coordinate is not approximate`() {
        val location = SafetyLocation.of(TravelContextSnapshot(), localInfoCountryCode = "pt")

        assertThat(location.countryCode).isEqualTo("PT")
        assertThat(location.coordinate).isNull()
        assertThat(location.isCoordinateApproximate).isFalse()
    }

    @Test
    fun `a precise device position keeps precise distances for precise missions`() {
        val overview = builder.build(
            listOf("FI"),
            SafetyLocation("MA", MissionCoordinate(34.05, -6.80), isCoordinateApproximate = false),
        )

        assertThat(overview.missions.first().distanceText).doesNotContain("~")
    }

    @Test
    fun `an IP only position shows every distance as approximate`() {
        val overview = builder.build(
            listOf("FI"),
            SafetyLocation("MA", MissionCoordinate(34.05, -6.80), isCoordinateApproximate = true),
        )

        assertThat(overview.missions).isNotEmpty()
        assertThat(overview.missions.map { it.distanceText }).isNotEmpty()
        overview.missions.forEach { assertThat(it.distanceText).matches("~\\d+ km") }
    }

    @Test
    fun `the collapsed summary and help follow an approximated nearest distance`() {
        // Belgium publishes no single consular line, so the collapsed line shows the nearest mission instead.
        val overview = builder.build(
            listOf("BE"),
            SafetyLocation("MA", MissionCoordinate(34.05, -6.80), isCoordinateApproximate = true),
        )

        assertThat(overview.missions.single().distanceText).matches("~\\d+ km")
        assertThat(overview.summary).isEqualTo("Rabat ${overview.missions.single().distanceText}")
        assertThat(overview.summaryHelp).contains(overview.missions.single().distanceText!!)
    }
}
