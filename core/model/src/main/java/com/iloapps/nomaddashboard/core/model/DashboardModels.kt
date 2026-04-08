package com.iloapps.nomaddashboard.core.model

import java.time.Instant
import java.time.LocalDate

enum class SignalLevel {
    GOOD,
    WARNING,
    BAD,
    NEUTRAL,
}

data class SummaryTile(
    val title: String,
    val headline: String,
    val detail: String,
    val level: SignalLevel = SignalLevel.NEUTRAL,
)

data class ConnectivitySnapshot(
    val internetState: String = "Checking",
    val isOnline: Boolean = false,
    val latencyMs: Double? = null,
    val jitterMs: Double? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val latencyHistoryMs: List<MetricHistoryPoint> = emptyList(),
    val downloadHistoryMbps: List<MetricHistoryPoint> = emptyList(),
    val uploadHistoryMbps: List<MetricHistoryPoint> = emptyList(),
    val wifiName: String? = null,
    val wifiSignalDbm: Int? = null,
    val vpnActive: Boolean = false,
    val timeZoneId: String = "UTC",
)

data class MetricHistoryPoint(
    val recordedAt: Instant,
    val value: Double,
)

data class PowerSnapshot(
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    val batteryHealthSummary: String = "Estimating",
    val dischargeWatts: Double? = null,
)

data class TravelContextSnapshot(
    val publicIp: String? = null,
    val city: String? = null,
    val region: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timeZoneId: String? = null,
)

data class WeatherDayForecast(
    val date: LocalDate,
    val summary: String,
    val minCelsius: Double?,
    val maxCelsius: Double?,
    val rainChancePercent: Int?,
)

data class WeatherSnapshot(
    val currentTemperatureCelsius: Double? = null,
    val apparentTemperatureCelsius: Double? = null,
    val windSpeedKph: Double? = null,
    val windDirectionDegrees: Double? = null,
    val rainChancePercent: Int? = null,
    val summary: String = "Weather unavailable",
    val dailyForecast: List<WeatherDayForecast> = emptyList(),
    val sourceName: String = "Open-Meteo",
)

enum class EmergencyCareStatus {
    LOADING,
    READY,
    CONFIGURATION_REQUIRED,
    PERMISSION_REQUIRED,
    UNAVAILABLE,
    ERROR,
}

enum class EmergencyCareLocationSource {
    DEVICE,
    IP_GEOLOCATION,
}

data class EmergencyCareFacility(
    val placeId: String? = null,
    val name: String,
    val address: String? = null,
    val distanceKilometers: Double,
    val latitude: Double,
    val longitude: Double,
    val primaryType: String? = null,
)

data class EmergencyCareSnapshot(
    val status: EmergencyCareStatus = EmergencyCareStatus.UNAVAILABLE,
    val sourceName: String = "Google Places",
    val countryCode: String? = null,
    val countryName: String? = null,
    val searchRadiusKilometers: Double = 10.0,
    val locationSource: EmergencyCareLocationSource? = null,
    val facility: EmergencyCareFacility? = null,
    val fetchedAt: Instant? = null,
    val detail: String = "Enable emergency care in Settings",
    val note: String? = null,
)

data class TimeTrackingDashboardState(
    val enabled: Boolean = false,
    val headline: String = "Disabled",
    val detail: String = "Time tracking arrives in the next slice.",
)

data class VisitedSummary(
    val citiesVisited: Int = 0,
    val countriesVisited: Int = 0,
    val trackedDays: Int = 0,
    val sourceSummary: String = "IP + Device",
)

data class DashboardSnapshot(
    val lastRefresh: Instant? = null,
    val isRefreshing: Boolean = false,
    val overallSummary: SummaryTile = SummaryTile("Overall", "Waiting", "Collecting dashboard signals"),
    val networkSummary: SummaryTile = SummaryTile("Network", "Waiting", "Collecting network quality"),
    val powerSummary: SummaryTile = SummaryTile("Power", "Waiting", "Collecting power status"),
    val connectivity: ConnectivitySnapshot = ConnectivitySnapshot(),
    val power: PowerSnapshot = PowerSnapshot(),
    val travelContext: TravelContextSnapshot = TravelContextSnapshot(),
    val weather: WeatherSnapshot = WeatherSnapshot(),
    val travelAlerts: TravelAlertsSnapshot = TravelAlertsSnapshot(),
    val fuelPrices: FuelPriceSnapshot = FuelPriceSnapshot(),
    val emergencyCare: EmergencyCareSnapshot = EmergencyCareSnapshot(),
    val timeTracking: TimeTrackingDashboardState = TimeTrackingDashboardState(),
    val visited: VisitedSummary = VisitedSummary(),
)
