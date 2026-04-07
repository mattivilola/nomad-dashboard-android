package com.iloapps.nomaddashboard.core.datastore

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.AppearanceMode
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardCardWidthMode
import com.iloapps.nomaddashboard.core.model.SurfSpotConfiguration

fun AppSettingsProto.toExternalModel(): AppSettings {
    val order = dashboardCardOrderList.mapNotNull { raw ->
        DashboardCardId.entries.firstOrNull { it.name == raw }
    }.ifEmpty {
        DashboardCardId.defaultOrder
    }

    val widths = dashboardCardWidthModesMap.mapNotNull { (key, value) ->
        val card = DashboardCardId.entries.firstOrNull { it.name == key } ?: return@mapNotNull null
        val width = DashboardCardWidthMode.entries.firstOrNull { it.name == value } ?: return@mapNotNull null
        card to width
    }.toMap().ifEmpty {
        DashboardCardId.defaultOrder.associateWith { DashboardCardWidthMode.WIDE }
    }

    return AppSettings(
        appearanceMode = AppearanceMode.entries.firstOrNull { it.name == appearanceMode } ?: AppearanceMode.SYSTEM,
        dashboardCardOrder = order,
        dashboardCardWidthModes = widths,
        publicIpGeolocationEnabled = publicIpGeolocationEnabled,
        shareAnonymousAnalytics = shareAnonymousAnalytics,
        useCurrentLocationForWeather = useCurrentLocationForWeather,
        useCurrentLocationForVisitedPlaces = useCurrentLocationForVisitedPlaces,
        weatherForecastExpanded = weatherForecastExpanded,
        fuelPricesEnabled = fuelPricesEnabled,
        emergencyCareEnabled = emergencyCareEnabled,
        visitedPlacesEnabled = visitedPlacesEnabled,
        projectTimeTrackingEnabled = projectTimeTrackingEnabled,
        surfSpot = SurfSpotConfiguration(
            name = surfSpotName,
            latitude = surfSpotLatitude.takeIf { hasSurfSpotLatitude },
            longitude = surfSpotLongitude.takeIf { hasSurfSpotLongitude },
        ),
        tankerkonigApiKey = tankerkonigApiKey,
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
        .setFuelPricesEnabled(fuelPricesEnabled)
        .setEmergencyCareEnabled(emergencyCareEnabled)
        .setVisitedPlacesEnabled(visitedPlacesEnabled)
        .setProjectTimeTrackingEnabled(projectTimeTrackingEnabled)
        .setSurfSpotName(surfSpot.name)
        .setHasSurfSpotLatitude(surfSpot.latitude != null)
        .setHasSurfSpotLongitude(surfSpot.longitude != null)
        .setSurfSpotLatitude(surfSpot.latitude ?: 0.0)
        .setSurfSpotLongitude(surfSpot.longitude ?: 0.0)
        .setTankerkonigApiKey(tankerkonigApiKey)
        .build()
