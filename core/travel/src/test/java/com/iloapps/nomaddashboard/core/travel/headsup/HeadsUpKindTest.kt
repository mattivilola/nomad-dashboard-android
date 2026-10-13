package com.iloapps.nomaddashboard.core.travel.headsup

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.TravelProfile
import org.junit.Test

/** The heads-up ranking tables (macOS DashboardInsightEngineTests and TravelProfileTests, heads-up parts). */
class HeadsUpKindTest {
    @Test
    fun `the tie-break order covers every kind exactly once for every profile`() {
        for (profile in TravelProfile.entries) {
            val order = HeadsUpKind.tieBreakOrder(profile)

            assertThat(order.toSet()).isEqualTo(HeadsUpKind.entries.toSet())
            assertThat(order).hasSize(HeadsUpKind.entries.size)
        }
    }

    @Test
    fun `the nomad order is the declaration order`() {
        assertThat(HeadsUpKind.tieBreakOrder(TravelProfile.NOMAD)).containsExactlyElementsIn(HeadsUpKind.entries).inOrder()
        assertThat(HeadsUpKind.entries.last()).isEqualTo(HeadsUpKind.DAYLIGHT)
        assertThat(HeadsUpKind.tieBreakOrder(TravelProfile.NOMAD)).containsExactly(
            HeadsUpKind.DATA_CAP,
            HeadsUpKind.STAY_ALLOWANCE,
            HeadsUpKind.SCHENGEN_LIMIT,
            HeadsUpKind.TAX_RESIDENCE_DAYS,
            HeadsUpKind.TRAVEL_ADVISORY,
            HeadsUpKind.WEATHER_ALERT,
            HeadsUpKind.REGIONAL_SECURITY,
            HeadsUpKind.STRONG_WIND,
            HeadsUpKind.RAIN_SOON,
            HeadsUpKind.UV_INDEX,
            HeadsUpKind.AIR_QUALITY,
            HeadsUpKind.PUBLIC_HOLIDAY,
            HeadsUpKind.LOCAL_RHYTHM,
            HeadsUpKind.FUEL_NEARBY,
            HeadsUpKind.DAYLIGHT,
        ).inOrder()
    }

    @Test
    fun `the road trip order matches the brief`() {
        assertThat(HeadsUpKind.tieBreakOrder(TravelProfile.ROAD_TRIP)).containsExactly(
            HeadsUpKind.STRONG_WIND,
            HeadsUpKind.DAYLIGHT,
            HeadsUpKind.UV_INDEX,
            HeadsUpKind.AIR_QUALITY,
            HeadsUpKind.FUEL_NEARBY,
            HeadsUpKind.WEATHER_ALERT,
            HeadsUpKind.DATA_CAP,
            HeadsUpKind.STAY_ALLOWANCE,
            HeadsUpKind.SCHENGEN_LIMIT,
            HeadsUpKind.TAX_RESIDENCE_DAYS,
            HeadsUpKind.TRAVEL_ADVISORY,
            HeadsUpKind.REGIONAL_SECURITY,
            HeadsUpKind.RAIN_SOON,
            HeadsUpKind.PUBLIC_HOLIDAY,
            HeadsUpKind.LOCAL_RHYTHM,
        ).inOrder()
    }

    @Test
    fun `neighbouring kinds keep their relative positions in both profiles`() {
        val nomad = HeadsUpKind.tieBreakOrder(TravelProfile.NOMAD)
        val roadTrip = HeadsUpKind.tieBreakOrder(TravelProfile.ROAD_TRIP)

        // Daylight follows strong wind on a road trip.
        assertThat(roadTrip.indexOf(HeadsUpKind.DAYLIGHT)).isEqualTo(roadTrip.indexOf(HeadsUpKind.STRONG_WIND) + 1)

        // UV and air quality follow rain for nomads and daylight for road trips.
        val rain = nomad.indexOf(HeadsUpKind.RAIN_SOON)
        assertThat(nomad.subList(rain + 1, rain + 3)).containsExactly(HeadsUpKind.UV_INDEX, HeadsUpKind.AIR_QUALITY).inOrder()
        val daylight = roadTrip.indexOf(HeadsUpKind.DAYLIGHT)
        assertThat(roadTrip.subList(daylight + 1, daylight + 3))
            .containsExactly(HeadsUpKind.UV_INDEX, HeadsUpKind.AIR_QUALITY).inOrder()

        for (order in listOf(nomad, roadTrip)) {
            // The stay heads-ups follow the data cap.
            val dataCap = order.indexOf(HeadsUpKind.DATA_CAP)
            assertThat(order.subList(dataCap + 1, dataCap + 4)).containsExactly(
                HeadsUpKind.STAY_ALLOWANCE,
                HeadsUpKind.SCHENGEN_LIMIT,
                HeadsUpKind.TAX_RESIDENCE_DAYS,
            ).inOrder()

            // The local-rhythm heads-up follows the public holiday.
            assertThat(order.indexOf(HeadsUpKind.LOCAL_RHYTHM)).isEqualTo(order.indexOf(HeadsUpKind.PUBLIC_HOLIDAY) + 1)
        }
    }

    @Test
    fun `severity ranks info below caution below attention`() {
        assertThat(HeadsUpSeverity.INFO).isLessThan(HeadsUpSeverity.CAUTION)
        assertThat(HeadsUpSeverity.CAUTION).isLessThan(HeadsUpSeverity.ATTENTION)
    }

    @Test
    fun `fresh settings use the nomad profile with no status heads-up`() {
        val settings = AppSettings()

        assertThat(settings.travelProfile).isEqualTo(TravelProfile.NOMAD)
        assertThat(settings.statusShowsHeadsUp).isFalse()
        assertThat(settings.isUsingRecommendedOrder).isTrue()
    }

    @Test
    fun `text limits truncate with an ellipsis`() {
        assertThat(HeadsUpText.truncated("x".repeat(44))).hasLength(44)
        assertThat(HeadsUpText.truncated("x".repeat(45))).isEqualTo("x".repeat(43) + "…")
        assertThat(HeadsUpText.truncated("Cheapest fuel station in town", HeadsUpText.STATUS_TEXT_CHARACTER_LIMIT))
            .isEqualTo("Cheapest fu…")
    }
}
