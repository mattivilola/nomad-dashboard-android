package com.iloapps.nomaddashboard.core.datastore

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.AppearanceMode
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardCardWidthMode
import com.iloapps.nomaddashboard.core.model.SurfSpotConfiguration
import org.junit.Test

class SettingsProtoMapperTest {
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
            fuelPricesEnabled = true,
            emergencyCareEnabled = true,
            visitedPlacesEnabled = false,
            projectTimeTrackingEnabled = true,
            surfSpot = SurfSpotConfiguration("Tarifa", 36.01, -5.60),
        )

        val restored = settings.toProto().toExternalModel()

        assertThat(restored.appearanceMode).isEqualTo(AppearanceMode.DARK)
        assertThat(restored.dashboardCardOrder).containsExactly(
            DashboardCardId.WEATHER,
            DashboardCardId.CONNECTIVITY,
        ).inOrder()
        assertThat(restored.dashboardCardWidthModes[DashboardCardId.WEATHER]).isEqualTo(DashboardCardWidthMode.NARROW)
        assertThat(restored.publicIpGeolocationEnabled).isFalse()
        assertThat(restored.useCurrentLocationForWeather).isTrue()
        assertThat(restored.useCurrentLocationForVisitedPlaces).isTrue()
        assertThat(restored.surfSpot.latitude).isEqualTo(36.01)
        assertThat(restored.surfSpot.longitude).isEqualTo(-5.60)
    }

    @Test
    fun `legacy tankerkoenig key does not round trip through app settings anymore`() {
        val proto = AppSettingsProto.newBuilder()
            .setTankerkonigApiKey("legacy-demo")
            .build()

        val roundTripped = proto.toExternalModel().toProto()

        assertThat(roundTripped.tankerkonigApiKey).isEmpty()
    }
}
