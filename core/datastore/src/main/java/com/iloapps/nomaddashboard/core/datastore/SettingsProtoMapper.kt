package com.iloapps.nomaddashboard.core.datastore

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.AppearanceMode
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardCardWidthMode
import com.iloapps.nomaddashboard.core.model.SurfSpotConfiguration

fun AppSettingsProto.toExternalModel(): AppSettings {
    val shouldRepairEmptyDefaults = settingsSchemaVersion == 0 && this == AppSettingsProto.getDefaultInstance()
    val defaults = AppSettings()
    val persistedOrder = dashboardCardOrderList.mapNotNull { raw ->
        dashboardCardIdFor(raw)
    }
    val order = persistedOrder.ifEmpty {
        DashboardCardId.defaultOrder
    }.withMissingDashboardCards()

    val persistedWidths = dashboardCardWidthModesMap.mapNotNull { (key, value) ->
        val card = dashboardCardIdFor(key) ?: return@mapNotNull null
        val width = DashboardCardWidthMode.entries.firstOrNull { it.name == value } ?: return@mapNotNull null
        card to width
    }.toMap()

    val widths = order.associateWith { card ->
        persistedWidths[card] ?: DashboardCardWidthMode.WIDE
    }
    val autoStartMinutes = projectTimeTrackingAutoStartMinutes
    val autoStopMinutes = projectTimeTrackingAutoStopMinutes
    val useDefaultTrackingWindow = autoStartMinutes == 0 && autoStopMinutes == 0

    return AppSettings(
        appearanceMode = AppearanceMode.entries.firstOrNull { it.name == appearanceMode } ?: AppearanceMode.SYSTEM,
        dashboardCardOrder = order,
        dashboardCardWidthModes = widths,
        publicIpGeolocationEnabled = if (shouldRepairEmptyDefaults) {
            defaults.publicIpGeolocationEnabled
        } else {
            publicIpGeolocationEnabled
        },
        shareAnonymousAnalytics = shareAnonymousAnalytics,
        useCurrentLocationForWeather = useCurrentLocationForWeather,
        useCurrentLocationForVisitedPlaces = useCurrentLocationForVisitedPlaces,
        weatherForecastExpanded = if (shouldRepairEmptyDefaults) {
            defaults.weatherForecastExpanded
        } else {
            weatherForecastExpanded
        },
        localInfoEnabled = localInfoEnabled,
        fuelPricesEnabled = fuelPricesEnabled,
        emergencyCareEnabled = emergencyCareEnabled,
        visitedPlacesEnabled = visitedPlacesEnabled,
        projectTimeTrackingEnabled = projectTimeTrackingEnabled,
        projectTimeTrackingAutoStartMinutes = if (useDefaultTrackingWindow) 7 * 60 else autoStartMinutes.coerceIn(0, 23 * 60 + 59),
        projectTimeTrackingAutoStopMinutes = if (useDefaultTrackingWindow) 19 * 60 else autoStopMinutes.coerceIn(0, 23 * 60 + 59),
        surfSpot = SurfSpotConfiguration(
            name = surfSpotName.ifBlank { defaults.surfSpot.name },
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
        .setSettingsSchemaVersion(CurrentSettingsSchemaVersion)
        .build()

private const val CurrentSettingsSchemaVersion = 1

private fun dashboardCardIdFor(raw: String): DashboardCardId? =
    when (raw) {
        "LOCAL_PRICE_LEVEL" -> DashboardCardId.LOCAL_INFO
        else -> DashboardCardId.entries.firstOrNull { it.name == raw }
    }

private fun List<DashboardCardId>.withMissingDashboardCards(): List<DashboardCardId> {
    val current = toMutableList()
    DashboardCardId.defaultOrder.forEach { card ->
        if (current.contains(card).not()) {
            current += card
        }
    }
    return current
}
