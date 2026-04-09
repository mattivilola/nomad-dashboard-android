package com.iloapps.nomaddashboard.core.datastore

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.AppearanceMode
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardCardWidthMode
import com.iloapps.nomaddashboard.core.model.SurfSpotConfiguration

fun AppSettingsProto.toExternalModel(): AppSettings {
    val order = dashboardCardOrderList.mapNotNull { raw ->
        dashboardCardIdFor(raw)
    }.ifEmpty {
        DashboardCardId.defaultOrder
    }

    val widths = dashboardCardWidthModesMap.mapNotNull { (key, value) ->
        val card = dashboardCardIdFor(key) ?: return@mapNotNull null
        val width = DashboardCardWidthMode.entries.firstOrNull { it.name == value } ?: return@mapNotNull null
        card to width
    }.toMap().ifEmpty {
        DashboardCardId.defaultOrder.associateWith { DashboardCardWidthMode.WIDE }
    }
    val autoStartMinutes = projectTimeTrackingAutoStartMinutes
    val autoStopMinutes = projectTimeTrackingAutoStopMinutes
    val useDefaultTrackingWindow = autoStartMinutes == 0 && autoStopMinutes == 0

    return AppSettings(
        appearanceMode = AppearanceMode.entries.firstOrNull { it.name == appearanceMode } ?: AppearanceMode.SYSTEM,
        dashboardCardOrder = order,
        dashboardCardWidthModes = widths,
        publicIpGeolocationEnabled = publicIpGeolocationEnabled,
        shareAnonymousAnalytics = shareAnonymousAnalytics,
        useCurrentLocationForWeather = useCurrentLocationForWeather,
        useCurrentLocationForVisitedPlaces = useCurrentLocationForVisitedPlaces,
        weatherForecastExpanded = weatherForecastExpanded,
        localInfoEnabled = localInfoEnabled,
        fuelPricesEnabled = fuelPricesEnabled,
        emergencyCareEnabled = emergencyCareEnabled,
        visitedPlacesEnabled = visitedPlacesEnabled,
        projectTimeTrackingEnabled = projectTimeTrackingEnabled,
        projectTimeTrackingAutoStartMinutes = if (useDefaultTrackingWindow) 7 * 60 else autoStartMinutes.coerceIn(0, 23 * 60 + 59),
        projectTimeTrackingAutoStopMinutes = if (useDefaultTrackingWindow) 19 * 60 else autoStopMinutes.coerceIn(0, 23 * 60 + 59),
        surfSpot = SurfSpotConfiguration(
            name = surfSpotName,
            latitude = surfSpotLatitude.takeIf { hasSurfSpotLatitude },
            longitude = surfSpotLongitude.takeIf { hasSurfSpotLongitude },
        ),
    )
}

fun AppSettings.toProto(): AppSettingsProto =
    AppSettingsProto.newBuilder()
        .setAppearanceMode(appearanceMode.name)
        .addAllDashboardCardOrder(dashboardCardOrder.map { it.name })
        .putAllDashboardCardWidthModes(dashboardCardWidthModes.mapKeys { it.key.name }.mapValues { it.value.name })
        .setPublicIpGeolocationEnabled(publicIpGeolocationEnabled)
        .setShareAnonymousAnalytics(shareAnonymousAnalytics)
        .setUseCurrentLocationForWeather(useCurrentLocationForWeather)
        .setUseCurrentLocationForVisitedPlaces(useCurrentLocationForVisitedPlaces)
        .setWeatherForecastExpanded(weatherForecastExpanded)
        .setLocalInfoEnabled(localInfoEnabled)
        .setFuelPricesEnabled(fuelPricesEnabled)
        .setEmergencyCareEnabled(emergencyCareEnabled)
        .setVisitedPlacesEnabled(visitedPlacesEnabled)
        .setProjectTimeTrackingEnabled(projectTimeTrackingEnabled)
        .setProjectTimeTrackingAutoStartMinutes(projectTimeTrackingAutoStartMinutes)
        .setProjectTimeTrackingAutoStopMinutes(projectTimeTrackingAutoStopMinutes)
        .setSurfSpotName(surfSpot.name)
        .setHasSurfSpotLatitude(surfSpot.latitude != null)
        .setHasSurfSpotLongitude(surfSpot.longitude != null)
        .setSurfSpotLatitude(surfSpot.latitude ?: 0.0)
        .setSurfSpotLongitude(surfSpot.longitude ?: 0.0)
        .build()

private fun dashboardCardIdFor(raw: String): DashboardCardId? =
    when (raw) {
        "LOCAL_PRICE_LEVEL" -> DashboardCardId.LOCAL_INFO
        else -> DashboardCardId.entries.firstOrNull { it.name == raw }
    }
