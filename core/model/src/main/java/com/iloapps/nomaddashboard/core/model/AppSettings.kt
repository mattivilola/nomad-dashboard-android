package com.iloapps.nomaddashboard.core.model

enum class AppearanceMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class DashboardCardId {
    CONNECTIVITY,
    POWER,
    TIME_TRACKING,
    TRAVEL_CONTEXT,
    FUEL_PRICES,
    EMERGENCY_CARE,
    TRAVEL_ALERTS,
    WEATHER,
    ;

    companion object {
        val defaultOrder = listOf(
            CONNECTIVITY,
            POWER,
            TIME_TRACKING,
            TRAVEL_CONTEXT,
            FUEL_PRICES,
            EMERGENCY_CARE,
            TRAVEL_ALERTS,
            WEATHER,
        )
    }
}

enum class DashboardCardWidthMode {
    WIDE,
    NARROW,
}

data class SurfSpotConfiguration(
    val name: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class AppSettings(
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    val dashboardCardOrder: List<DashboardCardId> = DashboardCardId.defaultOrder,
    val dashboardCardWidthModes: Map<DashboardCardId, DashboardCardWidthMode> =
        DashboardCardId.defaultOrder.associateWith { DashboardCardWidthMode.WIDE },
    val publicIpGeolocationEnabled: Boolean = true,
    val shareAnonymousAnalytics: Boolean = true,
    val useCurrentLocationForWeather: Boolean = false,
    val useCurrentLocationForVisitedPlaces: Boolean = false,
    val weatherForecastExpanded: Boolean = true,
    val fuelPricesEnabled: Boolean = false,
    val emergencyCareEnabled: Boolean = false,
    val visitedPlacesEnabled: Boolean = true,
    val projectTimeTrackingEnabled: Boolean = false,
    val surfSpot: SurfSpotConfiguration = SurfSpotConfiguration(name = "Tarifa", latitude = 36.0132, longitude = -5.6069),
)
