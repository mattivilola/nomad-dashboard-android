package com.iloapps.nomaddashboard.core.data.repository

import com.iloapps.nomaddashboard.core.common.ApplicationScope
import com.iloapps.nomaddashboard.core.data.fuel.FuelPriceProvider
import com.iloapps.nomaddashboard.core.data.fuel.FuelSearchRequest
import com.iloapps.nomaddashboard.core.data.location.ResolvedVisitedPlace
import com.iloapps.nomaddashboard.core.data.location.VisitedDeviceLocationProvider
import com.iloapps.nomaddashboard.core.data.monitor.TelemetryReader
import com.iloapps.nomaddashboard.core.data.timetracking.TimeTrackingRepository
import com.iloapps.nomaddashboard.core.data.visited.VisitedHistoryStore
import com.iloapps.nomaddashboard.core.data.visited.VisitedObservation
import com.iloapps.nomaddashboard.core.datastore.NomadSettingsDataSource
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.model.SignalLevel
import com.iloapps.nomaddashboard.core.model.SummaryTile
import com.iloapps.nomaddashboard.core.model.TimeTrackingDashboardState
import com.iloapps.nomaddashboard.core.model.TravelAlertsSnapshot
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedSummary
import com.iloapps.nomaddashboard.core.model.WeatherDayForecast
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import com.iloapps.nomaddashboard.core.model.visitedPlaceSummary
import com.iloapps.nomaddashboard.core.network.api.FreeIpApiService
import com.iloapps.nomaddashboard.core.network.api.OpenMeteoService
import com.iloapps.nomaddashboard.core.network.model.OpenMeteoDaily
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultNomadDashboardRepository @Inject constructor(
    private val settingsDataSource: NomadSettingsDataSource,
    private val telemetryReader: TelemetryReader,
    private val freeIpApiService: FreeIpApiService,
    private val openMeteoService: OpenMeteoService,
    private val fuelPriceProvider: FuelPriceProvider,
    private val timeTrackingRepository: TimeTrackingRepository,
    private val visitedHistoryStore: VisitedHistoryStore,
    private val visitedDeviceLocationProvider: VisitedDeviceLocationProvider,
    @ApplicationScope
    applicationScope: CoroutineScope,
) : NomadDashboardRepository {
    override val settings: Flow<AppSettings> = settingsDataSource.settings
    override val visitedPlaces: Flow<List<VisitedPlace>> = visitedHistoryStore.visitedPlaces
    override val visitedCountryDays: Flow<List<VisitedCountryDay>> = visitedHistoryStore.visitedCountryDays

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

        val currentDevicePlace = if (
            currentSettings.fuelPricesEnabled ||
            (currentSettings.visitedPlacesEnabled && currentSettings.useCurrentLocationForVisitedPlaces)
        ) {
            runCatching { visitedDeviceLocationProvider.currentPlace() }.getOrNull()
        } else {
            null
        }

        if (currentSettings.visitedPlacesEnabled) {
            val refreshStartedAt = Instant.now()

            buildIpObservation(travelContext, refreshStartedAt)?.let { observation ->
                runCatching { visitedHistoryStore.recordObservation(observation) }
            }

            if (currentSettings.useCurrentLocationForVisitedPlaces) {
                currentDevicePlace?.let { place ->
                    runCatching {
                        visitedHistoryStore.recordObservation(place.toObservation(refreshStartedAt))
                    }
                }
            }
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
        val visitedPlaces = visitedHistoryStore.visitedPlaces.first()
        val visitedCountryDays = visitedHistoryStore.visitedCountryDays.first()
        val visitedPlaceSummary = visitedPlaces.visitedPlaceSummary()
        val activeTimeTracking = timeTrackingRepository.currentActiveEntry()
        val fuelPrices = if (currentSettings.fuelPricesEnabled) {
            buildFuelSearchRequest(
                currentDevicePlace = currentDevicePlace,
                travelContext = travelContext,
            )?.let { request ->
                fuelPriceProvider.prices(request)
            } ?: FuelPriceSnapshot(
                status = FuelPriceStatus.UNAVAILABLE,
                sourceName = "Nomad Fuel Prices",
                countryCode = currentDevicePlace?.countryCode ?: travelContext.countryCode,
                countryName = currentDevicePlace?.country ?: travelContext.country,
                detail = "Nearby fuel prices need a resolved current location and country.",
            )
        } else {
            FuelPriceSnapshot(detail = "Enable fuel prices in Settings")
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
            fuelPrices = fuelPrices,
            emergencyCare = EmergencyCareSnapshot(
                summary = if (currentSettings.emergencyCareEnabled) {
                    "Places/Maps integration is planned next."
                } else {
                    "Enable emergency care in Settings"
                },
            ),
            timeTracking = TimeTrackingDashboardState(
                enabled = currentSettings.projectTimeTrackingEnabled,
                headline = when {
                    currentSettings.projectTimeTrackingEnabled.not() -> "Disabled"
                    activeTimeTracking != null -> "Tracking"
                    else -> "Ready"
                },
                detail = when {
                    currentSettings.projectTimeTrackingEnabled.not() -> "Turn this on to prepare local tracking."
                    activeTimeTracking != null -> {
                        val localTime = DateTimeFormatter.ofPattern("HH:mm")
                            .format(activeTimeTracking.entry.startAt.atZone(ZoneId.systemDefault()))
                        "Active: ${activeTimeTracking.project.name} since $localTime"
                    }
                    else -> "Open Tracking to add a project and start local time logging."
                },
            ),
            visited = VisitedSummary(
                citiesVisited = visitedPlaceSummary.citiesVisited,
                countriesVisited = visitedPlaceSummary.countriesVisited,
                trackedDays = visitedCountryDays.size,
                sourceSummary = visitedSourceSummary(currentSettings),
            ),
        )
    }

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val current = settings.first()
        val updated = transform(current)
        settingsDataSource.update { updated }
        if (current.projectTimeTrackingEnabled && updated.projectTimeTrackingEnabled.not()) {
            timeTrackingRepository.stopTracking()
        }
    }

    private fun weatherSummary(daily: OpenMeteoDaily?): String {
        val max = daily?.maxTemperatures?.firstOrNull()
        return if (max != null) "Today tops out at ${max.toInt()}°C" else "Weather unavailable"
    }

    private fun buildIpObservation(
        travelContext: TravelContextSnapshot,
        observedAt: Instant,
    ): VisitedObservation? {
        val country = travelContext.country?.trim()?.takeIf(String::isNotBlank) ?: return null
        return VisitedObservation(
            city = travelContext.city,
            region = travelContext.region,
            country = country,
            countryCode = travelContext.countryCode?.trim()?.takeIf(String::isNotBlank)?.uppercase(),
            latitude = travelContext.latitude,
            longitude = travelContext.longitude,
            source = com.iloapps.nomaddashboard.core.model.VisitedPlaceSource.PUBLIC_IP_GEOLOCATION,
            observedAt = observedAt,
        )
    }

    private fun ResolvedVisitedPlace.toObservation(observedAt: Instant): VisitedObservation =
        VisitedObservation(
            city = city,
            region = region,
            country = country,
            countryCode = countryCode,
            latitude = latitude,
            longitude = longitude,
            source = com.iloapps.nomaddashboard.core.model.VisitedPlaceSource.DEVICE_LOCATION,
            observedAt = observedAt,
        )

    private fun visitedSourceSummary(settings: AppSettings): String {
        if (settings.visitedPlacesEnabled.not()) {
            return "Disabled"
        }

        return listOfNotNull(
            "IP".takeIf { settings.publicIpGeolocationEnabled },
            "Device".takeIf { settings.useCurrentLocationForVisitedPlaces },
        ).joinToString(" + ").ifBlank { "None" }
    }

    private fun buildFuelSearchRequest(
        currentDevicePlace: ResolvedVisitedPlace?,
        travelContext: TravelContextSnapshot,
    ): FuelSearchRequest? {
        currentDevicePlace?.let { place ->
            val latitude = place.latitude
            val longitude = place.longitude
            val countryCode = place.countryCode?.trim()?.takeIf(String::isNotBlank)?.uppercase()
            if (latitude != null && longitude != null && countryCode != null) {
                return FuelSearchRequest(
                    latitude = latitude,
                    longitude = longitude,
                    countryCode = countryCode,
                    countryName = place.country,
                )
            }
        }

        val latitude = travelContext.latitude
        val longitude = travelContext.longitude
        val countryCode = travelContext.countryCode?.trim()?.takeIf(String::isNotBlank)?.uppercase()
        if (latitude != null && longitude != null && countryCode != null) {
            return FuelSearchRequest(
                latitude = latitude,
                longitude = longitude,
                countryCode = countryCode,
                countryName = travelContext.country,
            )
        }

        return null
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
