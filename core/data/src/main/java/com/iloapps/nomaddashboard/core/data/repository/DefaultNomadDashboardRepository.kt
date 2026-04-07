package com.iloapps.nomaddashboard.core.data.repository

import com.iloapps.nomaddashboard.core.common.ApplicationScope
import com.iloapps.nomaddashboard.core.datastore.NomadSettingsDataSource
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.SignalLevel
import com.iloapps.nomaddashboard.core.model.SummaryTile
import com.iloapps.nomaddashboard.core.model.TimeTrackingDashboardState
import com.iloapps.nomaddashboard.core.model.TravelAlertsSnapshot
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.model.VisitedSummary
import com.iloapps.nomaddashboard.core.model.WeatherDayForecast
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import com.iloapps.nomaddashboard.core.network.api.FreeIpApiService
import com.iloapps.nomaddashboard.core.network.api.OpenMeteoService
import com.iloapps.nomaddashboard.core.network.model.OpenMeteoDaily
import com.iloapps.nomaddashboard.core.data.monitor.SystemTelemetryReader
import com.iloapps.nomaddashboard.core.data.monitor.TrafficSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultNomadDashboardRepository @Inject constructor(
    private val settingsDataSource: NomadSettingsDataSource,
    private val telemetryReader: SystemTelemetryReader,
    private val freeIpApiService: FreeIpApiService,
    private val openMeteoService: OpenMeteoService,
    @ApplicationScope
    applicationScope: CoroutineScope,
) : NomadDashboardRepository {
    override val settings: Flow<AppSettings> = settingsDataSource.settings

    private val internalSnapshot = MutableStateFlow(DashboardSnapshot())
    override val snapshot: StateFlow<DashboardSnapshot> = internalSnapshot
        .stateIn(applicationScope, SharingStarted.Eagerly, DashboardSnapshot())

    private var previousTrafficSample: TrafficSample? = null

    override suspend fun refresh() {
        val currentSettings = settings.first()
        internalSnapshot.update { it.copy(isRefreshing = true) }

        val (connectivity, currentTraffic) = telemetryReader.connectivity(previousTrafficSample)
        previousTrafficSample = currentTraffic
        val power = telemetryReader.power()

        val travelContext = if (currentSettings.publicIpGeolocationEnabled) {
            runCatching {
                freeIpApiService.lookupMe().let { response ->
                    TravelContextSnapshot(
                        publicIp = response.ipAddress,
                        city = response.cityName,
                        region = response.regionName,
                        country = response.countryName,
                        countryCode = response.countryCode,
                        latitude = response.latitude,
                        longitude = response.longitude,
                        timeZoneId = response.timeZone,
                    )
                }
            }.getOrDefault(TravelContextSnapshot())
        } else {
            TravelContextSnapshot()
        }

        val latitude = travelContext.latitude
        val longitude = travelContext.longitude

        val weather = if (latitude != null && longitude != null) {
            runCatching {
                openMeteoService.forecast(
                    latitude = latitude,
                    longitude = longitude,
                )
            }.map { response ->
                WeatherSnapshot(
                    currentTemperatureCelsius = response.current?.temperatureCelsius,
                    apparentTemperatureCelsius = response.current?.apparentTemperatureCelsius,
                    windSpeedKph = response.current?.windSpeedKph,
                    windDirectionDegrees = response.current?.windDirectionDegrees,
                    rainChancePercent = response.current?.precipitationProbability,
                    summary = weatherSummary(response.daily),
                    dailyForecast = response.daily?.toForecast() ?: emptyList(),
                )
            }.getOrElse {
                WeatherSnapshot(summary = "Weather unavailable")
            }
        } else {
            WeatherSnapshot(summary = "Weather data unavailable")
        }

        val overallHeadline = when {
            connectivity.isOnline && (power.batteryPercent ?: 0) > 20 -> "Ready"
            connectivity.isOnline -> "Connected"
            else -> "Waiting"
        }

        internalSnapshot.value = DashboardSnapshot(
            lastRefresh = Instant.now(),
            isRefreshing = false,
            overallSummary = SummaryTile(
                title = "Overall",
                headline = overallHeadline,
                detail = "${travelContext.city ?: "Travel-ready"} system telemetry",
                level = if (connectivity.isOnline) SignalLevel.GOOD else SignalLevel.WARNING,
            ),
            networkSummary = SummaryTile(
                title = "Network",
                headline = connectivity.internetState,
                detail = connectivity.wifiName ?: "Collecting network quality",
                level = if (connectivity.isOnline) SignalLevel.GOOD else SignalLevel.BAD,
            ),
            powerSummary = SummaryTile(
                title = "Power",
                headline = power.batteryHealthSummary,
                detail = power.batteryPercent?.let { "$it% battery" } ?: "Collecting power status",
                level = when {
                    power.charging -> SignalLevel.GOOD
                    (power.batteryPercent ?: 0) > 35 -> SignalLevel.NEUTRAL
                    else -> SignalLevel.WARNING
                },
            ),
            connectivity = connectivity,
            power = power,
            travelContext = travelContext,
            weather = weather,
            travelAlerts = TravelAlertsSnapshot(summary = "Travel alerts contract is ready; Android provider is deferred."),
            fuelPrices = FuelPriceSnapshot(
                summary = if (currentSettings.fuelPricesEnabled) {
                    "Provider scaffolding is ready; regional integrations follow in the next slice."
                } else {
                    "Enable fuel prices in Settings"
                },
            ),
            emergencyCare = EmergencyCareSnapshot(
                summary = if (currentSettings.emergencyCareEnabled) {
                    "Places/Maps integration is planned next."
                } else {
                    "Enable emergency care in Settings"
                },
            ),
            timeTracking = TimeTrackingDashboardState(
                enabled = currentSettings.projectTimeTrackingEnabled,
                headline = if (currentSettings.projectTimeTrackingEnabled) "Planned" else "Disabled",
                detail = if (currentSettings.projectTimeTrackingEnabled) {
                    "Foreground-service tracking scaffold is the next milestone."
                } else {
                    "Turn this on to prepare local tracking."
                },
            ),
            visited = VisitedSummary(
                citiesVisited = 0,
                countriesVisited = 0,
                trackedDays = 0,
                sourceSummary = "IP + Device",
            ),
        )
    }

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settingsDataSource.update(transform)
    }

    private fun weatherSummary(daily: OpenMeteoDaily?): String {
        val max = daily?.maxTemperatures?.firstOrNull()
        return if (max != null) "Today tops out at ${max.toInt()}°C" else "Weather unavailable"
    }

    private fun OpenMeteoDaily.toForecast(): List<WeatherDayForecast> =
        dates.mapIndexed { index, rawDate ->
            WeatherDayForecast(
                date = runCatching { LocalDate.parse(rawDate) }.getOrDefault(LocalDate.now().plusDays(index.toLong())),
                summary = weatherCodeSummary(weatherCodes.getOrNull(index)),
                minCelsius = minTemperatures.getOrNull(index),
                maxCelsius = maxTemperatures.getOrNull(index),
                rainChancePercent = rainChance.getOrNull(index),
            )
        }

    private fun weatherCodeSummary(code: Int?): String = when (code) {
        0 -> "Clear"
        1, 2, 3 -> "Partly cloudy"
        45, 48 -> "Fog"
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> "Rain"
        71, 73, 75 -> "Snow"
        95, 96, 99 -> "Storms"
        else -> "Mixed"
    }
}
