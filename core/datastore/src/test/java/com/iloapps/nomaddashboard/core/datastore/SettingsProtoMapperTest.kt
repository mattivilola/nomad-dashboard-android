package com.iloapps.nomaddashboard.core.datastore

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.AppearanceMode
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardCardWidthMode
import com.iloapps.nomaddashboard.core.model.SurfSpotConfiguration
import com.iloapps.nomaddashboard.core.model.TravelProfile
import java.time.Instant
import org.junit.Test

class SettingsProtoMapperTest {
    @Test
    fun `resource policy defaults and preferences survive migration`() {
        assertThat(AppSettingsProto.getDefaultInstance().toExternalModel().resourceMode)
            .isEqualTo(com.iloapps.nomaddashboard.core.model.ResourceMode.AUTO)
        com.iloapps.nomaddashboard.core.model.ResourceMode.entries.forEach { mode ->
            assertThat(AppSettings(resourceMode = mode).toProto().toExternalModel().resourceMode).isEqualTo(mode)
        }
    }

    @Test
    fun `round trips app settings through proto`() {
        val settings = AppSettings(
            appearanceMode = AppearanceMode.DARK,
            dashboardCardOrder = listOf(DashboardCardId.WEATHER, DashboardCardId.CONNECTIVITY),
            dashboardCardWidthModes = mapOf(
                DashboardCardId.WEATHER to DashboardCardWidthMode.NARROW,
                DashboardCardId.CONNECTIVITY to DashboardCardWidthMode.WIDE,
            ),
            publicIpGeolocationEnabled = false,
            shareAnonymousAnalytics = false,
            useCurrentLocationForWeather = true,
            useCurrentLocationForVisitedPlaces = true,
            weatherForecastExpanded = false,
            localInfoEnabled = true,
            fuelPricesEnabled = true,
            emergencyCareEnabled = true,
            visitedPlacesEnabled = false,
            projectTimeTrackingEnabled = true,
            projectTimeTrackingAutoStartMinutes = 6 * 60 + 30,
            projectTimeTrackingAutoStopMinutes = 20 * 60 + 15,
            surfSpot = SurfSpotConfiguration("Tarifa", 36.01, -5.60),
        )

        val restored = settings.toProto().toExternalModel()

        assertThat(restored.appearanceMode).isEqualTo(AppearanceMode.DARK)
        assertThat(restored.dashboardCardOrder.take(2)).containsExactly(
            DashboardCardId.WEATHER,
            DashboardCardId.CONNECTIVITY,
        ).inOrder()
        assertThat(restored.dashboardCardWidthModes[DashboardCardId.WEATHER]).isEqualTo(DashboardCardWidthMode.NARROW)
        assertThat(restored.dashboardCardOrder).contains(DashboardCardId.LOCAL_INFO)
        assertThat(restored.publicIpGeolocationEnabled).isFalse()
        assertThat(restored.useCurrentLocationForWeather).isTrue()
        assertThat(restored.useCurrentLocationForVisitedPlaces).isTrue()
        assertThat(restored.localInfoEnabled).isTrue()
        assertThat(restored.projectTimeTrackingAutoStartMinutes).isEqualTo(6 * 60 + 30)
        assertThat(restored.projectTimeTrackingAutoStopMinutes).isEqualTo(20 * 60 + 15)
        assertThat(restored.surfSpot.latitude).isEqualTo(36.01)
        assertThat(restored.surfSpot.longitude).isEqualTo(-5.60)
    }

    @Test
    fun `legacy local price settings migrate to local info`() {
        val proto = AppSettingsProto.newBuilder()
            .setLocalInfoEnabled(true)
            .addDashboardCardOrder("LOCAL_PRICE_LEVEL")
            .putDashboardCardWidthModes("LOCAL_PRICE_LEVEL", DashboardCardWidthMode.NARROW.name)
            .build()

        val restored = proto.toExternalModel()

        assertThat(restored.localInfoEnabled).isTrue()
        assertThat(restored.dashboardCardOrder).contains(DashboardCardId.LOCAL_INFO)
        assertThat(restored.dashboardCardWidthModes[DashboardCardId.LOCAL_INFO]).isEqualTo(DashboardCardWidthMode.NARROW)
    }

    @Test
    fun `mapper inserts missing dashboard cards for older settings`() {
        val proto = AppSettingsProto.newBuilder()
            .addDashboardCardOrder(DashboardCardId.WEATHER.name)
            .build()

        val restored = proto.toExternalModel()

        assertThat(restored.dashboardCardOrder.last()).isEqualTo(DashboardCardId.WEATHER)
        assertThat(restored.dashboardCardOrder).containsExactlyElementsIn(DashboardCardId.entries)
        assertThat(restored.dashboardCardOrder).contains(DashboardCardId.LOCAL_INFO)
        assertThat(restored.dashboardCardWidthModes[DashboardCardId.LOCAL_INFO]).isEqualTo(DashboardCardWidthMode.WIDE)
    }

    @Test
    fun `legacy tankerkoenig key does not round trip through app settings anymore`() {
        val proto = AppSettingsProto.newBuilder()
            .setTankerkonigApiKey("legacy-demo")
            .build()

        val roundTripped = proto.toExternalModel().toProto()

        assertThat(roundTripped.tankerkonigApiKey).isEmpty()
    }

    @Test
    fun `default serializer value keeps model defaults that are true`() {
        val restored = AppSettingsSerializer.defaultValue.toExternalModel()

        assertThat(restored.publicIpGeolocationEnabled).isTrue()
        assertThat(restored.shareAnonymousAnalytics).isTrue()
        assertThat(restored.weatherForecastExpanded).isTrue()
        assertThat(restored.visitedPlacesEnabled).isTrue()
    }

    @Test
    fun `empty legacy settings repair location bootstrap defaults`() {
        val restored = AppSettingsProto.getDefaultInstance().toExternalModel()

        assertThat(restored.publicIpGeolocationEnabled).isTrue()
        assertThat(restored.weatherForecastExpanded).isTrue()
        assertThat(restored.visitedPlacesEnabled).isFalse()
        assertThat(restored.surfSpot.name).isEqualTo(AppSettings().surfSpot.name)
    }

    @Test
    fun `non-empty legacy settings do not repair explicit false privacy toggles`() {
        val proto = AppSettingsProto.newBuilder()
            .setAppearanceMode(AppearanceMode.DARK.name)
            .build()

        val restored = proto.toExternalModel()

        assertThat(restored.publicIpGeolocationEnabled).isFalse()
        assertThat(restored.shareAnonymousAnalytics).isFalse()
        assertThat(restored.visitedPlacesEnabled).isFalse()
    }

    @Test
    fun `schema v1 settings preserve explicit false defaults`() {
        val proto = AppSettings()
            .copy(
                publicIpGeolocationEnabled = false,
                shareAnonymousAnalytics = false,
                weatherForecastExpanded = false,
                visitedPlacesEnabled = false,
            )
            .toProto()

        val restored = proto.toExternalModel()

        assertThat(restored.publicIpGeolocationEnabled).isFalse()
        assertThat(restored.shareAnonymousAnalytics).isFalse()
        assertThat(restored.weatherForecastExpanded).isFalse()
        assertThat(restored.visitedPlacesEnabled).isFalse()
    }

    @Test
    fun `untouched pre-profile card order migrates to the nomad recommendation`() {
        val proto = AppSettingsProto.newBuilder()
            .setSettingsSchemaVersion(1)
            .setPublicIpGeolocationEnabled(true)
            .addAllDashboardCardOrder(LegacyDefaultOrder)
            .build()

        val restored = proto.toExternalModel()

        assertThat(restored.dashboardCardOrder).isEqualTo(DashboardCardId.defaultOrder(TravelProfile.NOMAD))
        assertThat(restored.isUsingRecommendedOrder).isTrue()
    }

    @Test
    fun `legacy default order with the pre-rename local price card also migrates`() {
        val proto = AppSettingsProto.newBuilder()
            .setSettingsSchemaVersion(1)
            .addAllDashboardCardOrder(LegacyDefaultOrder.map { if (it == "LOCAL_INFO") "LOCAL_PRICE_LEVEL" else it })
            .build()

        assertThat(proto.toExternalModel().dashboardCardOrder).isEqualTo(DashboardCardId.defaultOrder)
    }

    @Test
    fun `customised pre-profile card order is kept and new cards are inserted after their neighbours`() {
        val custom = listOf("POWER", "WEATHER", "TRAVEL_CONTEXT", "CONNECTIVITY")
        val proto = AppSettingsProto.newBuilder()
            .setSettingsSchemaVersion(1)
            .addAllDashboardCardOrder(custom)
            .build()

        val restored = proto.toExternalModel().dashboardCardOrder

        assertThat(restored).containsExactlyElementsIn(DashboardCardId.entries)
        assertThat(restored.first()).isEqualTo(DashboardCardId.POWER)
        assertThat(restored.indexOf(DashboardCardId.STAYS)).isEqualTo(restored.indexOf(DashboardCardId.TRAVEL_CONTEXT) + 1)
    }

    @Test
    fun `current schema keeps a legacy-looking order the user chose`() {
        val proto = AppSettings(
            dashboardCardOrder = LegacyDefaultOrder.map { DashboardCardId.valueOf(it) },
        ).toProto()

        val restored = proto.toExternalModel()

        assertThat(restored.dashboardCardOrder.filter { it != DashboardCardId.EMBASSY && it != DashboardCardId.STAYS })
            .containsExactlyElementsIn(LegacyDefaultOrder.map { DashboardCardId.valueOf(it) })
            .inOrder()
        assertThat(restored.dashboardCardOrder).isNotEqualTo(DashboardCardId.defaultOrder)
    }

    @Test
    fun `pre-v2 settings use model defaults for the stay arrival and air quality switches`() {
        val proto = AppSettingsProto.newBuilder()
            .setSettingsSchemaVersion(1)
            .setPublicIpGeolocationEnabled(true)
            .build()

        val restored = proto.toExternalModel()

        assertThat(restored.airQualityEnabled).isTrue()
        assertThat(restored.schengenCounterEnabled).isTrue()
        assertThat(restored.taxResidenceDaysEnabled).isTrue()
        assertThat(restored.taxResidenceThresholdDays).isEqualTo(183)
        assertThat(restored.arrivalBriefingEnabled).isTrue()
        assertThat(restored.stayAllowances).isEmpty()
        assertThat(restored.arrivalBriefingAcknowledgedAt).isNull()
        assertThat(restored.arrivalBriefingAcknowledgedCountryCode).isNull()
    }

    @Test
    fun `new settings default values survive a proto round trip`() {
        val restored = AppSettings().toProto().toExternalModel()

        assertThat(restored.travelProfile).isEqualTo(TravelProfile.NOMAD)
        assertThat(restored.dashboardCollapsedCards).isEmpty()
        assertThat(restored.statusShowsHeadsUp).isFalse()
        assertThat(restored.citizenshipCountryCodes).isEmpty()
        assertThat(restored.stayAllowances).isEmpty()
        assertThat(restored.airQualityEnabled).isTrue()
        assertThat(restored.schengenCounterEnabled).isTrue()
        assertThat(restored.taxResidenceDaysEnabled).isTrue()
        assertThat(restored.taxResidenceThresholdDays).isEqualTo(183)
        assertThat(restored.arrivalBriefingEnabled).isTrue()
        assertThat(restored.dashboardCardOrder).isEqualTo(DashboardCardId.defaultOrder)
    }

    @Test
    fun `travel profile stay and briefing settings round trip through proto`() {
        val acknowledgedAt = Instant.parse("2026-10-12T13:20:30.123Z")
        val settings = AppSettings(
            travelProfile = TravelProfile.ROAD_TRIP,
            dashboardCardOrder = DashboardCardId.defaultOrder(TravelProfile.ROAD_TRIP),
            dashboardCollapsedCards = setOf(DashboardCardId.WEATHER, DashboardCardId.POWER),
            airQualityEnabled = false,
            statusShowsHeadsUp = true,
            citizenshipCountryCodes = listOf("FI", "US"),
            stayAllowances = mapOf("MA" to 90, "TH" to 60),
            schengenCounterEnabled = false,
            taxResidenceDaysEnabled = false,
            taxResidenceThresholdDays = 90,
            arrivalBriefingEnabled = false,
            arrivalBriefingAcknowledgedCountryCode = "PT",
            arrivalBriefingAcknowledgedAt = acknowledgedAt,
        )

        val restored = settings.toProto().toExternalModel()

        assertThat(restored).isEqualTo(settings)
        assertThat(restored.isUsingRecommendedOrder).isTrue()
    }

    @Test
    fun `persisted citizenship stay and threshold values are sanitized on read`() {
        val proto = AppSettingsProto.newBuilder()
            .setSettingsSchemaVersion(2)
            .addAllCitizenshipCountryCodes(listOf(" fi ", "FI", "xyz", "us", "gb", "de"))
            .putStayAllowances("ma", 90)
            .putStayAllowances("xyz", 5)
            .putStayAllowances("ES", 0)
            .putStayAllowances("PT", 999)
            .setTaxResidenceThresholdDays(5)
            .setArrivalBriefingAcknowledgedCountryCode("pt")
            .build()

        val restored = proto.toExternalModel()

        assertThat(restored.citizenshipCountryCodes).containsExactly("FI", "US", "GB").inOrder()
        assertThat(restored.stayAllowances).containsExactly("MA", 90, "PT", 365)
        assertThat(restored.taxResidenceThresholdDays).isEqualTo(30)
        assertThat(restored.arrivalBriefingAcknowledgedCountryCode).isEqualTo("PT")
        assertThat(restored.arrivalBriefingAcknowledgedAt).isNull()
    }

    @Test
    fun `unknown collapsed card and travel profile values are ignored`() {
        val proto = AppSettingsProto.newBuilder()
            .setSettingsSchemaVersion(2)
            .setTravelProfile("SPACE_TRAVEL")
            .addDashboardCollapsedCards("POWER")
            .addDashboardCollapsedCards("NOT_A_CARD")
            .build()

        val restored = proto.toExternalModel()

        assertThat(restored.travelProfile).isEqualTo(TravelProfile.NOMAD)
        assertThat(restored.dashboardCollapsedCards).containsExactly(DashboardCardId.POWER)
    }

    @Test
    fun `absent profile with road trip order sanitizes against the profile order`() {
        val roadTripWithoutStays = DashboardCardId.defaultOrder(TravelProfile.ROAD_TRIP).filter { it != DashboardCardId.STAYS }
        val proto = AppSettings(travelProfile = TravelProfile.ROAD_TRIP, dashboardCardOrder = roadTripWithoutStays).toProto()

        val restored = proto.toExternalModel()

        assertThat(restored.dashboardCardOrder).isEqualTo(DashboardCardId.defaultOrder(TravelProfile.ROAD_TRIP))
    }

    private companion object {
        val LegacyDefaultOrder = listOf(
            "WEATHER",
            "TRAVEL_ALERTS",
            "CONNECTIVITY",
            "TRAVEL_CONTEXT",
            "LOCAL_INFO",
            "FUEL_PRICES",
            "POWER",
            "TIME_TRACKING",
            "EMERGENCY_CARE",
        )
    }
}
