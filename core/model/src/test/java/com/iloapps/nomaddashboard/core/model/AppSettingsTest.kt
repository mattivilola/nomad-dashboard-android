package com.iloapps.nomaddashboard.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppSettingsTest {
    @Test
    fun `recommended orders cover every card exactly once`() {
        TravelProfile.entries.forEach { profile ->
            assertThat(DashboardCardId.defaultOrder(profile)).containsExactlyElementsIn(DashboardCardId.entries)
            assertThat(DashboardCardId.defaultOrder(profile)).containsNoDuplicates()
        }
        assertThat(DashboardCardId.defaultOrder).isEqualTo(DashboardCardId.defaultOrder(TravelProfile.NOMAD))
        assertThat(AppSettings().dashboardCardWidthModes.keys).containsExactlyElementsIn(DashboardCardId.entries)
    }

    @Test
    fun `profile titles and summaries match the macOS wording`() {
        assertThat(TravelProfile.NOMAD.title).isEqualTo("Nomad")
        assertThat(TravelProfile.NOMAD.summary).isEqualTo("Puts connection quality, work time and city context first.")
        assertThat(TravelProfile.ROAD_TRIP.title).isEqualTo("Road trip & RV")
        assertThat(TravelProfile.ROAD_TRIP.summary)
            .isEqualTo("Puts weather, fuel, emergency care, power and data allowance first.")
    }

    @Test
    fun `sanitized order drops duplicates`() {
        val sanitized = DashboardCardId.sanitizedOrder(
            listOf(DashboardCardId.POWER, DashboardCardId.WEATHER, DashboardCardId.POWER),
        )

        assertThat(sanitized).containsNoDuplicates()
        assertThat(sanitized).containsExactlyElementsIn(DashboardCardId.entries)
    }

    @Test
    fun `sanitized order inserts a missing card right after its nearest recommended predecessor`() {
        val custom = listOf(
            DashboardCardId.WEATHER,
            DashboardCardId.TRAVEL_CONTEXT,
            DashboardCardId.POWER,
            DashboardCardId.CONNECTIVITY,
        )

        val sanitized = DashboardCardId.sanitizedOrder(custom)

        assertThat(sanitized.first()).isEqualTo(DashboardCardId.WEATHER)
        assertThat(sanitized.indexOf(DashboardCardId.STAYS)).isEqualTo(sanitized.indexOf(DashboardCardId.TRAVEL_CONTEXT) + 1)
        assertThat(sanitized.indexOf(DashboardCardId.TIME_TRACKING)).isEqualTo(sanitized.indexOf(DashboardCardId.POWER) + 1)
        assertThat(sanitized).containsExactlyElementsIn(DashboardCardId.entries)
    }

    @Test
    fun `sanitized order puts a card without a present predecessor first`() {
        val sanitized = DashboardCardId.sanitizedOrder(listOf(DashboardCardId.WEATHER))

        assertThat(sanitized.first()).isEqualTo(DashboardCardId.CONNECTIVITY)
        assertThat(sanitized.last()).isEqualTo(DashboardCardId.WEATHER)
        assertThat(sanitized.indexOf(DashboardCardId.EMBASSY)).isEqualTo(sanitized.indexOf(DashboardCardId.EMERGENCY_CARE) + 1)
    }

    @Test
    fun `sanitized order of an empty list is the profile recommendation`() {
        assertThat(DashboardCardId.sanitizedOrder(emptyList())).isEqualTo(DashboardCardId.defaultOrder)
        assertThat(DashboardCardId.sanitizedOrder(emptyList(), TravelProfile.ROAD_TRIP))
            .isEqualTo(DashboardCardId.defaultOrder(TravelProfile.ROAD_TRIP))
    }

    @Test
    fun `an untouched order from before new cards existed becomes the new recommendation`() {
        val withoutNewCards = DashboardCardId.defaultOrder.filter {
            it != DashboardCardId.STAYS && it != DashboardCardId.EMBASSY
        }

        assertThat(DashboardCardId.sanitizedOrder(withoutNewCards)).isEqualTo(DashboardCardId.defaultOrder)

        val roadTripWithoutNewCards = DashboardCardId.defaultOrder(TravelProfile.ROAD_TRIP).filter {
            it != DashboardCardId.STAYS && it != DashboardCardId.EMBASSY
        }
        assertThat(DashboardCardId.sanitizedOrder(roadTripWithoutNewCards, TravelProfile.ROAD_TRIP))
            .isEqualTo(DashboardCardId.defaultOrder(TravelProfile.ROAD_TRIP))
    }

    @Test
    fun `applying a profile moves an untouched order to the new recommendation`() {
        val settings = AppSettings()
        assertThat(settings.isUsingRecommendedOrder).isTrue()

        val roadTrip = settings.applyTravelProfile(TravelProfile.ROAD_TRIP)

        assertThat(roadTrip.travelProfile).isEqualTo(TravelProfile.ROAD_TRIP)
        assertThat(roadTrip.dashboardCardOrder).isEqualTo(DashboardCardId.defaultOrder(TravelProfile.ROAD_TRIP))
        assertThat(roadTrip.isUsingRecommendedOrder).isTrue()
        assertThat(roadTrip.applyTravelProfile(TravelProfile.NOMAD).dashboardCardOrder).isEqualTo(DashboardCardId.defaultOrder)
    }

    @Test
    fun `applying a profile keeps a customised order`() {
        val custom = DashboardCardId.defaultOrder.reversed()
        val settings = AppSettings(dashboardCardOrder = custom)
        assertThat(settings.isUsingRecommendedOrder).isFalse()

        val roadTrip = settings.applyTravelProfile(TravelProfile.ROAD_TRIP)

        assertThat(roadTrip.travelProfile).isEqualTo(TravelProfile.ROAD_TRIP)
        assertThat(roadTrip.dashboardCardOrder).isEqualTo(custom)
        assertThat(roadTrip.isUsingRecommendedOrder).isFalse()
    }

    @Test
    fun `citizenship codes are upper-cased deduplicated letter pairs capped at three`() {
        assertThat(AppSettings.sanitizedCitizenshipCodes(listOf(" fi ", "FI", "xyz", "1A", "", "us", "gb", "de")))
            .containsExactly("FI", "US", "GB")
            .inOrder()
        assertThat(AppSettings.sanitizedCitizenshipCodes(listOf("é1", "Ä", "ab"))).containsExactly("AB")
        assertThat(AppSettings.sanitizedCitizenshipCodes(emptyList())).isEmpty()
    }

    @Test
    fun `stay allowances are sanitized to country codes and one to 365 days`() {
        val sanitized = AppSettings.sanitizedStayAllowances(
            mapOf(" ma " to 90, "TH" to 60, "xyz" to 30, "1A" to 30, "" to 30, "ES" to 0, "FR" to -5, "PT" to 4_000),
        )

        assertThat(sanitized).containsExactly("MA", 90, "TH", 60, "PT", 365)
        assertThat(AppSettings.sanitizedStayAllowances(mapOf("ma" to 30, "MA" to 60))).containsExactly("MA", 60)
    }

    @Test
    fun `tax residence threshold is clamped`() {
        assertThat(AppSettings.sanitizedTaxResidenceThreshold(10)).isEqualTo(30)
        assertThat(AppSettings.sanitizedTaxResidenceThreshold(30)).isEqualTo(30)
        assertThat(AppSettings.sanitizedTaxResidenceThreshold(183)).isEqualTo(183)
        assertThat(AppSettings.sanitizedTaxResidenceThreshold(365)).isEqualTo(365)
        assertThat(AppSettings.sanitizedTaxResidenceThreshold(1_000)).isEqualTo(365)
        assertThat(AppSettings().taxResidenceThresholdDays).isEqualTo(183)
    }

    @Test
    fun `country code normalization rejects anything but a letter pair`() {
        assertThat(AppSettings.normalizedCountryCode(" pt ")).isEqualTo("PT")
        assertThat(AppSettings.normalizedCountryCode("Portugal")).isNull()
        assertThat(AppSettings.normalizedCountryCode("")).isNull()
    }

    @Test
    fun `air quality is only active with device location weather`() {
        assertThat(AppSettings(airQualityEnabled = true, useCurrentLocationForWeather = true).isAirQualityActive).isTrue()
        assertThat(AppSettings(airQualityEnabled = true, useCurrentLocationForWeather = false).isAirQualityActive).isFalse()
        assertThat(AppSettings(airQualityEnabled = false, useCurrentLocationForWeather = true).isAirQualityActive).isFalse()
    }

    @Test
    fun `card feature enablement follows the matching setting`() {
        val off = AppSettings(
            localInfoEnabled = false,
            fuelPricesEnabled = false,
            emergencyCareEnabled = false,
            projectTimeTrackingEnabled = false,
            visitedPlacesEnabled = false,
            citizenshipCountryCodes = emptyList(),
        )
        val on = AppSettings(
            localInfoEnabled = true,
            fuelPricesEnabled = true,
            emergencyCareEnabled = true,
            projectTimeTrackingEnabled = true,
            visitedPlacesEnabled = true,
            citizenshipCountryCodes = listOf("FI"),
        )
        val toggled = listOf(
            DashboardCardId.LOCAL_INFO,
            DashboardCardId.FUEL_PRICES,
            DashboardCardId.EMERGENCY_CARE,
            DashboardCardId.TIME_TRACKING,
            DashboardCardId.STAYS,
            DashboardCardId.EMBASSY,
        )

        toggled.forEach { card ->
            assertThat(card.isFeatureEnabled(off)).isFalse()
            assertThat(card.isFeatureEnabled(on)).isTrue()
        }
        (DashboardCardId.entries - toggled.toSet()).forEach { card ->
            assertThat(card.isFeatureEnabled(off)).isTrue()
        }
    }

    @Test
    fun `turned-off cards map to the settings group that enables them`() {
        assertThat(DashboardCardId.LOCAL_INFO.settingsDestination).isEqualTo(SettingsDestination.LOCAL_INFO)
        assertThat(DashboardCardId.FUEL_PRICES.settingsDestination).isEqualTo(SettingsDestination.FUEL_PRICES)
        assertThat(DashboardCardId.EMERGENCY_CARE.settingsDestination).isEqualTo(SettingsDestination.EMERGENCY_CARE)
        assertThat(DashboardCardId.TIME_TRACKING.settingsDestination).isEqualTo(SettingsDestination.TIME_TRACKING)
        assertThat(DashboardCardId.STAYS.settingsDestination).isEqualTo(SettingsDestination.VISITED_PLACES)
        assertThat(DashboardCardId.EMBASSY.settingsDestination).isEqualTo(SettingsDestination.CITIZENSHIP)
        assertThat(DashboardCardId.TRAVEL_ALERTS.settingsDestination).isEqualTo(SettingsDestination.TRAVEL_ALERTS)
        assertThat(DashboardCardId.WEATHER.settingsDestination).isEqualTo(SettingsDestination.WEATHER)
        assertThat(DashboardCardId.CONNECTIVITY.settingsDestination).isNull()
        assertThat(DashboardCardId.POWER.settingsDestination).isNull()
        assertThat(DashboardCardId.TRAVEL_CONTEXT.settingsDestination).isNull()
    }

    @Test
    fun `settings destinations belong to the brief's pages`() {
        assertThat(SettingsPage.entries.map { it.title }).containsExactly(
            "General",
            "Weather & Local",
            "Safety",
            "Stays & Visas",
            "Connection & Data",
            "Work",
            "Privacy & Location",
            "Advanced",
        ).inOrder()
        assertThat(SettingsDestination.HOME_TIME_ZONE.page).isEqualTo(SettingsPage.GENERAL)
        assertThat(SettingsDestination.CITIZENSHIP.page).isEqualTo(SettingsPage.GENERAL)
        assertThat(SettingsDestination.SURF_SPOT.page).isEqualTo(SettingsPage.WEATHER_AND_LOCAL)
        assertThat(SettingsDestination.EMERGENCY_CARE.page).isEqualTo(SettingsPage.SAFETY)
        assertThat(SettingsDestination.QUIET_ALERTS.page).isEqualTo(SettingsPage.CONNECTION_AND_DATA)
        assertThat(SettingsDestination.WORKPLACE_DIARY.page).isEqualTo(SettingsPage.WORK)
        assertThat(SettingsDestination.VISITED_PLACES.page).isEqualTo(SettingsPage.PRIVACY_AND_LOCATION)
        assertThat(SettingsDestination.STAYS.page).isEqualTo(SettingsPage.STAYS_AND_VISAS)
    }
}
