package com.iloapps.nomaddashboard.core.model

import java.time.Instant
import java.time.Duration
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

/** What the device knows about its internet connection. [label] is the text the UI shows for it. */
enum class InternetState(val label: String) {
    /** No reading yet. */
    CHECKING("Checking"),
    ONLINE("Online"),

    /** A network is active but the platform has not validated internet access (captive portal, no route). */
    CAPTIVE_OR_UNVALIDATED("Captive/Checking"),

    /** No active network at all. */
    OFFLINE("Offline"),
}

data class ConnectivitySnapshot(
    val internetState: InternetState = InternetState.CHECKING,
    val isOnline: Boolean = false,
    val latencyMs: Double? = null,
    /** Variation between the last few latency readings; null until at least two exist. */
    val jitterMs: Double? = null,
    /** When [latencyMs] was measured. Null for a reading whose age is unknown. */
    val latencyMeasuredAt: Instant? = null,
    /** The host that answered the latency probe ("1.1.1.1"); null together with [latencyMs]. */
    val latencyProbeHost: String? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val latencyHistoryMs: List<MetricHistoryPoint> = emptyList(),
    val downloadHistoryMbps: List<MetricHistoryPoint> = emptyList(),
    val uploadHistoryMbps: List<MetricHistoryPoint> = emptyList(),
    val wifiName: String? = null,
    val wifiSignalDbm: Int? = null,
    val wifiLinkSpeedMbps: Int? = null,
    val wifiFrequencyMhz: Int? = null,
    val vpnActive: Boolean = false,
    val timeZoneId: String = "UTC",
) {
    /** True when the device has no active network at all. */
    val isOffline: Boolean
        get() = internetState == InternetState.OFFLINE

    /** True before the first connection reading arrived. */
    val isChecking: Boolean
        get() = internetState == InternetState.CHECKING

    /**
     * This snapshot without its latency and jitter when the reading is older than [MaximumLatencyAge]
     * at [now]. A reading with no timestamp has an unknown age and is kept. The history stays.
     */
    fun withoutStaleLatency(now: Instant): ConnectivitySnapshot {
        val measuredAt = latencyMeasuredAt ?: return this
        if (latencyMs == null && jitterMs == null) return this
        val age = Duration.between(measuredAt, now)
        return if (age > MaximumLatencyAge) copy(latencyMs = null, jitterMs = null, latencyMeasuredAt = null, latencyProbeHost = null) else this
    }

    companion object {
        /** A latency reading older than this says nothing about the connection now. */
        val MaximumLatencyAge: Duration = Duration.ofMinutes(5)
    }
}

data class MetricHistoryPoint(
    val recordedAt: Instant,
    val value: Double,
)

data class PowerSnapshot(
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    val statusLabel: String = "Checking",
    val batteryHealthSummary: String = "Estimating",
    val batteryHealthLevel: SignalLevel = SignalLevel.NEUTRAL,
    val dischargeWatts: Double? = null,
    val powerSourceLabel: String? = null,
    val temperatureCelsius: Double? = null,
    val voltageVolts: Double? = null,
    val batteryPercentHistory: List<MetricHistoryPoint> = emptyList(),
    /** Android Battery Saver, the equivalent of macOS Low Power Mode. */
    val powerSaveMode: Boolean = false,
    /** Estimated minutes until the battery is empty while discharging; null when unknown or charging. */
    val minutesRemaining: Int? = null,
    /** Estimated minutes until fully charged while charging; null when unknown or not charging. */
    val minutesToFull: Int? = null,
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
    val deviceCity: String? = null,
    val deviceRegion: String? = null,
    val deviceCountry: String? = null,
    val deviceCountryCode: String? = null,
    val deviceLatitude: Double? = null,
    val deviceLongitude: Double? = null,
)

enum class StartupLocationBootstrapPhase {
    CHECKING_DEVICE_LOCATION,
    USING_DEVICE_LOCATION,
    FALLING_BACK_TO_IP_LOCATION,
    NO_LOCATION_SOURCE_AVAILABLE,
}

data class StartupLocationBootstrapState(
    val phase: StartupLocationBootstrapPhase = StartupLocationBootstrapPhase.NO_LOCATION_SOURCE_AVAILABLE,
    val isChecking: Boolean = false,
)

data class WeatherDayForecast(
    val date: LocalDate,
    val summary: String,
    val minCelsius: Double?,
    val maxCelsius: Double?,
    val rainChancePercent: Int?,
    val weatherCode: Int? = null,
    val windSpeedKph: Double? = null,
    val windDirectionDegrees: Double? = null,
)

data class WeatherHourlyForecastSlot(
    val at: Instant,
    val summary: String,
    val temperatureCelsius: Double?,
    val rainChancePercent: Int?,
    val windSpeedKph: Double?,
    val windDirectionDegrees: Double?,
    val weatherCode: Int? = null,
    /** WHO UV index as a whole number; null when unavailable or cached before UV was captured. */
    val uvIndex: Int? = null,
)

data class MarineForecastSlot(
    val at: Instant,
    val waveHeightMeters: Double?,
    val swellHeightMeters: Double?,
    val windSpeedKph: Double?,
    val windDirectionDegrees: Double?,
)

data class MarineSnapshot(
    val spotName: String,
    val sourceName: String = "Open-Meteo",
    val waveHeightMeters: Double? = null,
    val wavePeriodSeconds: Double? = null,
    val swellHeightMeters: Double? = null,
    val swellPeriodSeconds: Double? = null,
    val swellDirectionDegrees: Double? = null,
    val windSpeedKph: Double? = null,
    val windGustKph: Double? = null,
    val windDirectionDegrees: Double? = null,
    val seaSurfaceTemperatureCelsius: Double? = null,
    val forecastSlots: List<MarineForecastSlot> = emptyList(),
    val fetchedAt: Instant? = null,
)

data class WeatherSnapshot(
    val currentTemperatureCelsius: Double? = null,
    val apparentTemperatureCelsius: Double? = null,
    val windSpeedKph: Double? = null,
    val windDirectionDegrees: Double? = null,
    val rainChancePercent: Int? = null,
    val summary: String = "Weather unavailable",
    val conditionDescription: String = "Weather unavailable",
    val weatherCode: Int? = null,
    /** WHO UV index right now as a whole number; null when unavailable or cached before UV was captured. */
    val uvIndex: Int? = null,
    val hourlyForecast: List<WeatherHourlyForecastSlot> = emptyList(),
    val dailyForecast: List<WeatherDayForecast> = emptyList(),
    val sourceName: String = "Open-Meteo",
    val fetchedAt: Instant? = null,
    val isRefreshing: Boolean = false,
)

enum class EmergencyCareStatus {
    LOADING,
    READY,
    CONFIGURATION_REQUIRED,
    PERMISSION_REQUIRED,
    UNAVAILABLE,
    ERROR,
}

enum class HospitalOwnership(val displayName: String) {
    PUBLIC("Public"),
    PRIVATE("Private"),
    UNKNOWN("Unknown"),
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
    val ownership: HospitalOwnership = HospitalOwnership.UNKNOWN,
)

data class EmergencyCareSnapshot(
    val status: EmergencyCareStatus = EmergencyCareStatus.UNAVAILABLE,
    val sourceName: String = "Google Places",
    val countryCode: String? = null,
    val countryName: String? = null,
    val searchRadiusKilometers: Double = 25.0,
    val locationSource: EmergencyCareLocationSource? = null,
    /** Nearby hospitals, nearest first. */
    val facilities: List<EmergencyCareFacility> = emptyList(),
    val fetchedAt: Instant? = null,
    val detail: String = "Enable emergency care in Settings",
    val note: String? = null,
    val isRefreshing: Boolean = false,
)

data class TimeTrackingDashboardState(
    val enabled: Boolean = false,
    val headline: String = "Disabled",
    val detail: String = "Time tracking arrives in the next slice.",
    val interruptionsToday: Int = 0,
    val estimatedFocusLoss: Duration = Duration.ZERO,
    val estimatedFocusTime: Duration = Duration.ZERO,
)

data class VisitedSummary(
    val citiesVisited: Int = 0,
    val countriesVisited: Int = 0,
    val trackedDays: Int = 0,
    val sourceSummary: String = "IP + Device",
)

data class SectionRefreshState(
    val lastSuccessAt: Instant? = null,
    val locationKey: String = "",
    val locationLabel: String = "",
    val isRefreshing: Boolean = false,
    val message: String? = null,
)

data class DashboardSnapshot(
    val localSignalsAt: Instant? = null,
    val sectionStates: Map<DashboardCardId, SectionRefreshState> = emptyMap(),
    val lowImpactMode: Boolean = false,
    val resourcePolicyLabel: String = "Normal data use",
    val lastRefresh: Instant? = null,
    val isRefreshing: Boolean = false,
    val overallSummary: SummaryTile = SummaryTile("Overall", "Waiting", "Collecting dashboard signals"),
    val networkSummary: SummaryTile = SummaryTile("Network", "Waiting", "Collecting network quality"),
    val powerSummary: SummaryTile = SummaryTile("Power", "Waiting", "Collecting power status"),
    val connectivity: ConnectivitySnapshot = ConnectivitySnapshot(),
    val power: PowerSnapshot = PowerSnapshot(),
    val travelContext: TravelContextSnapshot = TravelContextSnapshot(),
    val startupLocation: StartupLocationBootstrapState = StartupLocationBootstrapState(),
    val weather: WeatherSnapshot = WeatherSnapshot(),
    val marine: MarineSnapshot? = null,
    val airQuality: AirQualitySnapshot? = null,
    val travelAlerts: TravelAlertsSnapshot = TravelAlertsSnapshot(),
    val localInfo: LocalInfoSnapshot = LocalInfoSnapshot(),
    val fuelPrices: FuelPriceSnapshot = FuelPriceSnapshot(),
    val emergencyCare: EmergencyCareSnapshot = EmergencyCareSnapshot(),
    val timeTracking: TimeTrackingDashboardState = TimeTrackingDashboardState(),
    val visited: VisitedSummary = VisitedSummary(),
)
