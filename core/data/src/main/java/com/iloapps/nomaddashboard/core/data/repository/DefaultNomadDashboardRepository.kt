package com.iloapps.nomaddashboard.core.data.repository

import android.util.Log
import com.iloapps.nomaddashboard.core.common.ApplicationScope
import com.iloapps.nomaddashboard.core.data.credentials.ProviderCredentialStore
import com.iloapps.nomaddashboard.core.data.emergency.EmergencyCareProvider
import com.iloapps.nomaddashboard.core.data.emergency.EmergencyCareSearchRequest
import com.iloapps.nomaddashboard.core.data.fuel.FuelPriceProvider
import com.iloapps.nomaddashboard.core.data.fuel.FuelProviderCredentials
import com.iloapps.nomaddashboard.core.data.fuel.FuelSearchRequest
import com.iloapps.nomaddashboard.core.data.location.ResolvedVisitedPlace
import com.iloapps.nomaddashboard.core.data.location.VisitedDeviceLocationProvider
import com.iloapps.nomaddashboard.core.data.localprice.LocalPriceLevelProvider
import com.iloapps.nomaddashboard.core.data.localprice.LocalPriceLevelRequest
import com.iloapps.nomaddashboard.core.data.monitor.TelemetryReader
import com.iloapps.nomaddashboard.core.data.timetracking.TimeTrackingRepository
import com.iloapps.nomaddashboard.core.data.travelalerts.BundledNeighborCountryResolver
import com.iloapps.nomaddashboard.core.data.travelalerts.ReliefWebProviderError
import com.iloapps.nomaddashboard.core.data.travelalerts.ReliefWebSecurityProvider
import com.iloapps.nomaddashboard.core.data.travelalerts.SmartravellerAdvisoryProvider
import com.iloapps.nomaddashboard.core.data.travelalerts.TravelAlertDiagnosticError
import com.iloapps.nomaddashboard.core.data.visited.VisitedHistoryStore
import com.iloapps.nomaddashboard.core.data.visited.VisitedObservation
import com.iloapps.nomaddashboard.core.database.dao.MetricPointDao
import com.iloapps.nomaddashboard.core.database.entity.MetricPointEntity
import com.iloapps.nomaddashboard.core.datastore.NomadSettingsDataSource
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareLocationSource
import com.iloapps.nomaddashboard.core.model.EmergencyCareStatus
import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.model.MarineForecastSlot
import com.iloapps.nomaddashboard.core.model.MarineSnapshot
import com.iloapps.nomaddashboard.core.model.MetricHistoryPoint
import com.iloapps.nomaddashboard.core.model.LocalPriceLevelSnapshot
import com.iloapps.nomaddashboard.core.model.LocalPriceLevelStatus
import com.iloapps.nomaddashboard.core.model.SignalLevel
import com.iloapps.nomaddashboard.core.model.SurfSpotConfiguration
import com.iloapps.nomaddashboard.core.model.SummaryTile
import com.iloapps.nomaddashboard.core.model.TimeTrackingDashboardState
import com.iloapps.nomaddashboard.core.model.TravelAlertsSnapshot
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.model.ProviderCredentialSettings
import com.iloapps.nomaddashboard.core.model.TravelAlertKind
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalSnapshot
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalState
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalStatus
import com.iloapps.nomaddashboard.core.model.TravelAlertUnavailableReason
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedSummary
import com.iloapps.nomaddashboard.core.model.WeatherDayForecast
import com.iloapps.nomaddashboard.core.model.WeatherHourlyForecastSlot
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import com.iloapps.nomaddashboard.core.model.isAutomaticallyTracked
import com.iloapps.nomaddashboard.core.model.visitedPlaceSummary
import com.iloapps.nomaddashboard.core.network.api.FreeIpApiService
import com.iloapps.nomaddashboard.core.network.api.IpifyService
import com.iloapps.nomaddashboard.core.network.api.OpenMeteoMarineService
import com.iloapps.nomaddashboard.core.network.api.OpenMeteoService
import com.iloapps.nomaddashboard.core.network.model.OpenMeteoDaily
import com.iloapps.nomaddashboard.core.network.model.FreeIpApiResponse
import com.iloapps.nomaddashboard.core.network.model.OpenMeteoHourly
import com.iloapps.nomaddashboard.core.network.model.OpenMeteoMarineHourly
import com.iloapps.nomaddashboard.core.network.model.OpenMeteoMarineResponse
import com.iloapps.nomaddashboard.core.network.model.OpenMeteoResponse
import com.iloapps.nomaddashboard.core.data.monitor.TrafficSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultNomadDashboardRepository @Inject constructor(
    private val settingsDataSource: NomadSettingsDataSource,
    private val telemetryReader: TelemetryReader,
    private val freeIpApiService: FreeIpApiService,
    private val ipifyService: IpifyService,
    private val openMeteoService: OpenMeteoService,
    private val openMeteoMarineService: OpenMeteoMarineService,
    private val localPriceLevelProvider: LocalPriceLevelProvider,
    private val fuelPriceProvider: FuelPriceProvider,
    private val emergencyCareProvider: EmergencyCareProvider,
    private val timeTrackingRepository: TimeTrackingRepository,
    private val visitedHistoryStore: VisitedHistoryStore,
    private val metricPointDao: MetricPointDao,
    private val visitedDeviceLocationProvider: VisitedDeviceLocationProvider,
    private val providerCredentialStore: ProviderCredentialStore,
    private val smartravellerAdvisoryProvider: SmartravellerAdvisoryProvider,
    private val reliefWebSecurityProvider: ReliefWebSecurityProvider,
    private val neighborCountryResolver: BundledNeighborCountryResolver,
    @ApplicationScope
    private val applicationScope: CoroutineScope,
) : NomadDashboardRepository {
    override val settings: Flow<AppSettings> = settingsDataSource.settings
    override val providerCredentials: Flow<ProviderCredentialSettings> = providerCredentialStore.credentials
    override val visitedPlaces: Flow<List<VisitedPlace>> = visitedHistoryStore.visitedPlaces
    override val visitedCountryDays: Flow<List<VisitedCountryDay>> = visitedHistoryStore.visitedCountryDays

    private val internalSnapshot = MutableStateFlow(DashboardSnapshot())
    override val snapshot: StateFlow<DashboardSnapshot> = internalSnapshot
        .stateIn(applicationScope, SharingStarted.Eagerly, DashboardSnapshot())

    private var previousTrafficSample: TrafficSample? = null

    init {
        applicationScope.launch {
            migrateLegacyProviderCredentials()
        }
    }

    override suspend fun refresh() {
        val currentSettings = settings.first()
        val currentProviderCredentials = providerCredentials.first()
        val previousTravelContext = internalSnapshot.value.travelContext
        internalSnapshot.update { it.copy(isRefreshing = true) }
        val refreshedAt = Instant.now()

        val (connectivity, currentTraffic) = telemetryReader.connectivity(previousTrafficSample)
        previousTrafficSample = currentTraffic
        val connectivityWithHistory = enrichConnectivityWithHistory(
            connectivity = connectivity,
            recordedAtMillis = currentTraffic.capturedAtMillis,
        )
        val power = enrichPowerWithHistory(
            power = telemetryReader.power(),
            recordedAtMillis = currentTraffic.capturedAtMillis,
        )

        val currentDevicePlace = if (visitedDeviceLocationProvider.hasLocationPermission()) {
            runCatching { visitedDeviceLocationProvider.currentPlace() }.getOrNull()
        } else {
            null
        }

        val travelContext = refreshTravelContext(
            currentSettings = currentSettings,
            previous = previousTravelContext,
            currentDevicePlace = currentDevicePlace,
        )

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

        val weatherLocation = buildWeatherLocation(
            currentSettings = currentSettings,
            currentDevicePlace = currentDevicePlace,
            travelContext = travelContext,
        )
        val weather = if (weatherLocation != null) {
            runCatching {
                openMeteoService.forecast(
                    latitude = weatherLocation.first,
                    longitude = weatherLocation.second,
                    forecastDays = 7,
                )
            }.map { response ->
                WeatherSnapshot(
                    currentTemperatureCelsius = response.current?.temperatureCelsius,
                    apparentTemperatureCelsius = response.current?.apparentTemperatureCelsius,
                    windSpeedKph = response.current?.windSpeedKph,
                    windDirectionDegrees = response.current?.windDirectionDegrees,
                    rainChancePercent = response.current?.precipitationProbability,
                    summary = weatherSummary(response.daily),
                    conditionDescription = weatherCodeSummary(response.current?.weatherCode),
                    weatherCode = response.current?.weatherCode,
                    hourlyForecast = response.hourly.toHourlyForecast(
                        timezone = response.timezone,
                        utcOffsetSeconds = response.utcOffsetSeconds,
                        referenceTime = refreshedAt,
                        offsetsHours = listOf(3, 6, 12),
                    ),
                    dailyForecast = response.daily?.toForecast() ?: emptyList(),
                    fetchedAt = refreshedAt,
                )
            }.getOrElse {
                WeatherSnapshot(summary = "Weather unavailable", conditionDescription = "Weather unavailable")
            }
        } else {
            WeatherSnapshot(
                summary = unavailableWeatherSummary(currentSettings),
                conditionDescription = "Weather unavailable",
            )
        }
        val marine = refreshMarineSnapshot(
            surfSpot = currentSettings.surfSpot,
            refreshedAt = refreshedAt,
        )

        val previousTravelAlerts = internalSnapshot.value.travelAlerts
        val primaryTravelAlertCountryCode = currentDevicePlace?.countryCode
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.uppercase()
            ?: travelContext.countryCode?.trim()?.takeIf(String::isNotBlank)?.uppercase()
        val primaryTravelAlertCountryName = currentDevicePlace?.country?.trim()?.takeIf(String::isNotBlank)
            ?: travelContext.country?.trim()?.takeIf(String::isNotBlank)
        val coverageCountryCodes = primaryTravelAlertCountryCode?.let { primaryCountryCode ->
            listOf(primaryCountryCode) + neighborCountryResolver.neighboringCountryCodes(primaryCountryCode)
        }.orEmpty().map { it.uppercase() }.distinct()
        val checkingTravelAlerts = synchronizedTravelAlertsSnapshot(
            previous = previousTravelAlerts,
            primaryCountryCode = primaryTravelAlertCountryCode,
            primaryCountryName = primaryTravelAlertCountryName,
            coverageCountryCodes = coverageCountryCodes,
        )
        internalSnapshot.update { current ->
            current.copy(
                isRefreshing = true,
                travelAlerts = checkingTravelAlerts,
                emergencyCare = loadingEmergencyCareSnapshot(
                    enabled = currentSettings.emergencyCareEnabled,
                    previous = current.emergencyCare,
                ),
            )
        }
        val travelAlerts = refreshTravelAlerts(
            previous = previousTravelAlerts,
            primaryCountryCode = primaryTravelAlertCountryCode,
            primaryCountryName = primaryTravelAlertCountryName,
            coverageCountryCodes = coverageCountryCodes,
            providerCredentials = currentProviderCredentials,
        )
        val emergencyCare = refreshEmergencyCare(
            currentSettings = currentSettings,
            currentDevicePlace = currentDevicePlace,
            travelContext = travelContext,
        )
        val localPriceLevel = refreshLocalPriceLevel(
            currentSettings = currentSettings,
            currentProviderCredentials = currentProviderCredentials,
            currentDevicePlace = currentDevicePlace,
            travelContext = travelContext,
        )

        val overallHeadline = when {
            connectivityWithHistory.isOnline && (power.batteryPercent ?: 0) > 20 -> "Ready"
            connectivityWithHistory.isOnline -> "Connected"
            else -> "Waiting"
        }
        val visitedPlaces = visitedHistoryStore.visitedPlaces.first()
        val visitedCountryDays = visitedHistoryStore.visitedCountryDays.first()
        val visitedPlaceSummary = visitedPlaces.visitedPlaceSummary()
        val activeTimeTracking = timeTrackingRepository.currentActiveEntry()
        val pendingTimeTracking = timeTrackingRepository.pendingEntries.first()
        val fuelPrices = if (currentSettings.fuelPricesEnabled) {
            buildFuelSearchRequest(
                currentDevicePlace = currentDevicePlace,
                travelContext = travelContext,
            )?.let { request ->
                fuelPriceProvider.prices(
                    request = request,
                    credentials = FuelProviderCredentials(
                        tankerkoenigApiKey = currentProviderCredentials.tankerkoenigApiKey,
                    ),
                )
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
            lastRefresh = refreshedAt,
            isRefreshing = false,
            overallSummary = SummaryTile(
                title = "Overall",
                headline = overallHeadline,
                detail = "${travelContextPrimaryLocation(travelContext) ?: "Travel-ready"} system telemetry",
                level = if (connectivityWithHistory.isOnline) SignalLevel.GOOD else SignalLevel.WARNING,
            ),
            networkSummary = SummaryTile(
                title = "Network",
                headline = connectivityWithHistory.internetState,
                detail = connectivityWithHistory.wifiName ?: "Collecting network quality",
                level = if (connectivityWithHistory.isOnline) SignalLevel.GOOD else SignalLevel.BAD,
            ),
            powerSummary = SummaryTile(
                title = "Power",
                headline = power.statusLabel,
                detail = powerSummaryDetail(power),
                level = when {
                    power.charging -> SignalLevel.GOOD
                    power.batteryHealthLevel == SignalLevel.BAD -> SignalLevel.BAD
                    power.batteryHealthLevel == SignalLevel.WARNING -> SignalLevel.WARNING
                    (power.batteryPercent ?: 0) > 35 -> SignalLevel.NEUTRAL
                    else -> SignalLevel.WARNING
                },
            ),
            connectivity = connectivityWithHistory,
            power = power,
            travelContext = travelContext,
            weather = weather,
            marine = marine,
            travelAlerts = travelAlerts,
            localPriceLevel = localPriceLevel,
            fuelPrices = fuelPrices,
            emergencyCare = emergencyCare,
            timeTracking = TimeTrackingDashboardState(
                enabled = currentSettings.projectTimeTrackingEnabled,
                headline = when {
                    currentSettings.projectTimeTrackingEnabled.not() -> "Disabled"
                    activeTimeTracking != null -> "Running"
                    pendingTimeTracking.isNotEmpty() -> "Paused"
                    else -> "Ready"
                },
                detail = when {
                    currentSettings.projectTimeTrackingEnabled.not() -> "Turn this on to enable the live unallocated buffer."
                    activeTimeTracking != null -> {
                        val localTime = DateTimeFormatter.ofPattern("HH:mm")
                            .format(activeTimeTracking.entry.startAt.atZone(ZoneId.systemDefault()))
                        val mode = if (activeTimeTracking.entry.isAutomaticallyTracked()) "Auto" else "Manual"
                        "$mode capture since $localTime"
                    }
                    pendingTimeTracking.isNotEmpty() ->
                        "${pendingTimeTracking.size} buffered segment(s) waiting for allocation."
                    else -> "Dashboard quick-allocation is ready once your projects are set up."
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

    override suspend fun updateProviderCredentials(transform: (ProviderCredentialSettings) -> ProviderCredentialSettings) {
        val previous = providerCredentials.first()
        providerCredentialStore.update(transform)
        val updated = providerCredentials.first()
        if (previous.hudUserApiToken != updated.hudUserApiToken) {
            localPriceLevelProvider.clearUsCache()
        }
        refresh()
    }

    override suspend fun resolveSurfSpotFromCurrentLocation(): SurfSpotConfiguration? =
        visitedDeviceLocationProvider.currentPlace()?.toSurfSpotConfiguration()

    private suspend fun enrichConnectivityWithHistory(
        connectivity: com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot,
        recordedAtMillis: Long,
    ): com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot {
        recordMetric(
            kind = ConnectivityDownloadMetricKind,
            recordedAtMillis = recordedAtMillis,
            value = connectivity.downloadMbps ?: 0.0,
        )
        recordMetric(
            kind = ConnectivityUploadMetricKind,
            recordedAtMillis = recordedAtMillis,
            value = connectivity.uploadMbps ?: 0.0,
        )
        connectivity.latencyMs?.let { latencyMs ->
            recordMetric(
                kind = ConnectivityLatencyMetricKind,
                recordedAtMillis = recordedAtMillis,
                value = latencyMs,
            )
        }

        return connectivity.copy(
            latencyHistoryMs = recentMetricHistory(ConnectivityLatencyMetricKind),
            downloadHistoryMbps = recentMetricHistory(ConnectivityDownloadMetricKind),
            uploadHistoryMbps = recentMetricHistory(ConnectivityUploadMetricKind),
        )
    }

    private suspend fun enrichPowerWithHistory(
        power: com.iloapps.nomaddashboard.core.model.PowerSnapshot,
        recordedAtMillis: Long,
    ): com.iloapps.nomaddashboard.core.model.PowerSnapshot {
        power.batteryPercent?.let { batteryPercent ->
            recordMetric(
                kind = PowerBatteryPercentMetricKind,
                recordedAtMillis = recordedAtMillis,
                value = batteryPercent.toDouble(),
                keepCount = PowerHistoryRetentionCount,
            )
        }

        return power.copy(
            batteryPercentHistory = recentMetricHistory(
                kind = PowerBatteryPercentMetricKind,
                limit = PowerHistoryRetentionCount,
            ),
        )
    }

    private suspend fun recordMetric(
        kind: String,
        recordedAtMillis: Long,
        value: Double,
        keepCount: Int = ConnectivityHistoryRetentionCount,
    ) {
        metricPointDao.insert(
            MetricPointEntity(
                kind = kind,
                timestampEpochMillis = recordedAtMillis,
                value = value,
            ),
        )
        metricPointDao.trimToLatest(kind = kind, keepCount = keepCount)
    }

    private suspend fun recentMetricHistory(
        kind: String,
        limit: Int = ConnectivityHistoryRetentionCount,
    ): List<MetricHistoryPoint> =
        metricPointDao.recentByKind(kind = kind, limit = limit)
            .asReversed()
            .map { point ->
                MetricHistoryPoint(
                    recordedAt = Instant.ofEpochMilli(point.timestampEpochMillis),
                    value = point.value,
                )
            }

    private suspend fun migrateLegacyProviderCredentials() {
        val legacyKey = settingsDataSource.legacyTankerkoenigApiKey().trim()
        if (legacyKey.isBlank()) {
            return
        }

        providerCredentialStore.update { current ->
            if (current.tankerkoenigApiKey.isBlank()) {
                current.copy(tankerkoenigApiKey = legacyKey)
            } else {
                current
            }
        }
        settingsDataSource.clearLegacyTankerkoenigApiKey()
    }

    private suspend fun refreshTravelAlerts(
        previous: TravelAlertsSnapshot,
        primaryCountryCode: String?,
        primaryCountryName: String?,
        coverageCountryCodes: List<String>,
        providerCredentials: ProviderCredentialSettings,
    ): TravelAlertsSnapshot {
        val attemptedAt = Instant.now()
        travelAlertLogDebug(
            TravelAlertsLogTag,
            "refreshTravelAlerts primary=${primaryCountryCode ?: "none"} country=${primaryCountryName ?: "none"} coverage=${coverageCountryCodes.joinToString(",")}",
        )
        val advisoryState = refreshAlertState(
            kind = TravelAlertKind.ADVISORY,
            previous = previous.state(TravelAlertKind.ADVISORY),
            primaryCountryCode = primaryCountryCode,
            attemptedAt = attemptedAt,
        ) {
            smartravellerAdvisoryProvider.advisory(
                countryCodes = coverageCountryCodes,
                primaryCountryCode = primaryCountryCode ?: error("primary country required"),
            )
        }
        val securityState = refreshAlertState(
            kind = TravelAlertKind.SECURITY,
            previous = previous.state(TravelAlertKind.SECURITY),
            primaryCountryCode = primaryCountryCode,
            attemptedAt = attemptedAt,
        ) {
            reliefWebSecurityProvider.security(
                appName = providerCredentials.reliefWebAppName,
                countryCodes = coverageCountryCodes,
                primaryCountryCode = primaryCountryCode ?: error("primary country required"),
            )
        }

        return TravelAlertsSnapshot(
            primaryCountryCode = primaryCountryCode,
            primaryCountryName = primaryCountryName,
            coverageCountryCodes = coverageCountryCodes,
            states = listOf(advisoryState, securityState),
            fetchedAt = attemptedAt,
        )
    }

    private fun synchronizedTravelAlertsSnapshot(
        previous: TravelAlertsSnapshot,
        primaryCountryCode: String?,
        primaryCountryName: String?,
        coverageCountryCodes: List<String>,
    ): TravelAlertsSnapshot =
        TravelAlertsSnapshot(
            primaryCountryCode = primaryCountryCode,
            primaryCountryName = primaryCountryName,
            coverageCountryCodes = coverageCountryCodes,
            states = TravelAlertKind.entries.map { kind ->
                val previousState = previous.state(kind)
                TravelAlertSignalState(
                    kind = kind,
                    status = TravelAlertSignalStatus.CHECKING,
                    sourceName = previousState?.sourceName ?: sourceName(kind),
                    sourceUrl = previousState?.sourceUrl ?: sourceUrl(kind),
                    lastAttemptedAt = previousState?.lastAttemptedAt,
                    lastSuccessAt = previousState?.lastSuccessAt,
                )
            },
            fetchedAt = previous.fetchedAt,
        )

    private suspend fun refreshAlertState(
        kind: TravelAlertKind,
        previous: TravelAlertSignalState?,
        primaryCountryCode: String?,
        attemptedAt: Instant,
        fetch: suspend () -> TravelAlertSignalSnapshot,
    ): TravelAlertSignalState {
        val retainedSourceName = previous?.sourceName ?: sourceName(kind)
        val retainedSourceUrl = previous?.sourceUrl ?: sourceUrl(kind)

        if (primaryCountryCode.isNullOrBlank()) {
            travelAlertLogWarn(
                TravelAlertsLogTag,
                "travel alert ${kind.name.lowercase()} unavailable: no primary country context",
            )
            return TravelAlertSignalState(
                kind = kind,
                status = TravelAlertSignalStatus.UNAVAILABLE,
                reason = TravelAlertUnavailableReason.COUNTRY_REQUIRED,
                sourceName = retainedSourceName,
                sourceUrl = retainedSourceUrl,
                lastAttemptedAt = attemptedAt,
                lastSuccessAt = previous?.lastSuccessAt,
            )
        }

        return try {
            val signal = fetch()
            travelAlertLogInfo(
                TravelAlertsLogTag,
                "travel alert ${kind.name.lowercase()} ready severity=${signal.severity.name.lowercase()} source=${signal.sourceName}",
            )
            TravelAlertSignalState(
                kind = kind,
                status = TravelAlertSignalStatus.READY,
                signal = signal,
                sourceName = signal.sourceName,
                sourceUrl = signal.sourceUrl ?: retainedSourceUrl,
                lastAttemptedAt = attemptedAt,
                lastSuccessAt = attemptedAt,
            )
        } catch (error: Throwable) {
            val reason = unavailableReason(error)
            val diagnosticSummary = diagnosticSummary(error)
            travelAlertLogWarn(
                TravelAlertsLogTag,
                "travel alert ${kind.name.lowercase()} failed reason=${reason.name.lowercase()} diagnostic=${diagnosticSummary ?: "none"} message=${error.message ?: "none"}",
                error,
            )
            val previousSignal = previous?.signal
            if (previousSignal != null) {
                TravelAlertSignalState(
                    kind = kind,
                    status = TravelAlertSignalStatus.STALE,
                    signal = previousSignal,
                    reason = reason,
                    diagnosticSummary = diagnosticSummary,
                    sourceName = retainedSourceName,
                    sourceUrl = retainedSourceUrl,
                    lastAttemptedAt = attemptedAt,
                    lastSuccessAt = previous.lastSuccessAt,
                )
            } else {
                TravelAlertSignalState(
                    kind = kind,
                    status = TravelAlertSignalStatus.UNAVAILABLE,
                    reason = reason,
                    diagnosticSummary = diagnosticSummary,
                    sourceName = retainedSourceName,
                    sourceUrl = retainedSourceUrl,
                    lastAttemptedAt = attemptedAt,
                    lastSuccessAt = previous?.lastSuccessAt,
                )
            }
        }
    }

    private fun unavailableReason(error: Throwable): TravelAlertUnavailableReason =
        when (error) {
            is ReliefWebProviderError.AppNameApprovalRequired,
            is ReliefWebProviderError.AppNameMissing,
            -> TravelAlertUnavailableReason.SOURCE_CONFIGURATION_REQUIRED
            else -> TravelAlertUnavailableReason.SOURCE_UNAVAILABLE
        }

    private fun diagnosticSummary(error: Throwable): String? =
        (error as? TravelAlertDiagnosticError)?.diagnosticSummary

    private fun sourceName(kind: TravelAlertKind): String = when (kind) {
        TravelAlertKind.ADVISORY -> "Smartraveller"
        TravelAlertKind.SECURITY -> "ReliefWeb"
    }

    private fun sourceUrl(kind: TravelAlertKind): String = when (kind) {
        TravelAlertKind.ADVISORY -> "https://www.smartraveller.gov.au"
        TravelAlertKind.SECURITY -> "https://reliefweb.int"
    }

    private fun weatherSummary(daily: OpenMeteoDaily?): String {
        val max = daily?.maxTemperatures?.firstOrNull()
        return if (max != null) "Today tops out at ${max.toInt()}°C" else "Weather unavailable"
    }

    private suspend fun refreshMarineSnapshot(
        surfSpot: SurfSpotConfiguration,
        refreshedAt: Instant,
    ): MarineSnapshot? {
        val latitude = surfSpot.latitude
        val longitude = surfSpot.longitude
        if (surfSpot.isConfigured().not() || surfSpot.hasValidCoordinates().not() || latitude == null || longitude == null) {
            return null
        }

        return runCatching {
            val marineResponse = openMeteoMarineService.forecast(
                latitude = latitude,
                longitude = longitude,
                forecastDays = 2,
            )
            val windResponse = openMeteoService.forecast(
                latitude = latitude,
                longitude = longitude,
                current = "wind_speed_10m,wind_gusts_10m,wind_direction_10m",
                hourly = "wind_speed_10m,wind_gusts_10m,wind_direction_10m",
                forecastDays = 2,
            )

            marineResponse.toMarineSnapshot(
                surfSpot = surfSpot,
                windResponse = windResponse,
                referenceTime = refreshedAt,
            )
        }.getOrNull()
    }

    private suspend fun refreshEmergencyCare(
        currentSettings: AppSettings,
        currentDevicePlace: ResolvedVisitedPlace?,
        travelContext: TravelContextSnapshot,
    ): EmergencyCareSnapshot {
        if (currentSettings.emergencyCareEnabled.not()) {
            return EmergencyCareSnapshot(detail = "Enable emergency care in Settings")
        }

        val request = buildEmergencyCareSearchRequest(
            currentDevicePlace = currentDevicePlace,
            travelContext = travelContext,
        )
        if (request != null) {
            return emergencyCareProvider.nearbyHospital(request)
        }

        val locationPermissionMissing = visitedDeviceLocationProvider.hasLocationPermission().not()
        return if (locationPermissionMissing) {
            EmergencyCareSnapshot(
                status = EmergencyCareStatus.PERMISSION_REQUIRED,
                countryCode = travelContext.countryCode,
                countryName = travelContext.country,
                detail = "Grant location permission or re-enable IP geolocation to search nearby hospitals.",
            )
        } else {
            EmergencyCareSnapshot(
                status = EmergencyCareStatus.UNAVAILABLE,
                countryCode = travelContext.countryCode,
                countryName = travelContext.country,
                detail = "Nearby hospitals need a resolved current location before the search can run.",
            )
        }
    }

    private fun loadingEmergencyCareSnapshot(
        enabled: Boolean,
        previous: EmergencyCareSnapshot,
    ): EmergencyCareSnapshot =
        if (enabled.not()) {
            EmergencyCareSnapshot(detail = "Enable emergency care in Settings")
        } else {
            previous.copy(
                status = EmergencyCareStatus.LOADING,
                detail = "Searching nearby hospitals...",
                note = previous.note.takeIf { previous.facility != null },
            )
        }

    private suspend fun refreshTravelContext(
        currentSettings: AppSettings,
        previous: TravelContextSnapshot,
        currentDevicePlace: ResolvedVisitedPlace?,
    ): TravelContextSnapshot {
        val ipTravelContext = if (currentSettings.publicIpGeolocationEnabled) {
            fetchCurrentTravelContext(previous = previous)
                ?: previous.takeIf { it.containsIpContext() }
                ?: TravelContextSnapshot()
        } else {
            TravelContextSnapshot()
        }

        return ipTravelContext.withDeviceLocation(currentDevicePlace)
    }

    private suspend fun refreshLocalPriceLevel(
        currentSettings: AppSettings,
        currentProviderCredentials: ProviderCredentialSettings,
        currentDevicePlace: ResolvedVisitedPlace?,
        travelContext: TravelContextSnapshot,
    ): LocalPriceLevelSnapshot {
        if (currentSettings.localPriceLevelEnabled.not()) {
            return LocalPriceLevelSnapshot(
                detail = "Enable local price level in Settings",
            )
        }

        val request = buildLocalPriceLevelRequest(
            currentDevicePlace = currentDevicePlace,
            travelContext = travelContext,
        )
        return runCatching {
            localPriceLevelProvider.prices(
                request = request,
                hudUserApiToken = currentProviderCredentials.hudUserApiToken,
            )
        }.getOrElse {
            LocalPriceLevelSnapshot(
                status = LocalPriceLevelStatus.UNAVAILABLE,
                countryCode = request.countryCode,
                countryName = request.countryName,
                sources = listOf("Eurostat", "HUD USER", "US Census Geocoder"),
                fetchedAt = Instant.now(),
                detail = "Local price level is unavailable right now.",
            )
        }
    }

    private suspend fun fetchCurrentTravelContext(
        previous: TravelContextSnapshot,
    ): TravelContextSnapshot? {
        runCatching {
            freeIpApiService.lookupMe().toTravelContextSnapshot(previous = previous)
        }.getOrNull()?.takeIf { it.containsIpContext() }?.let { return it }

        val fallbackIpAddress = runCatching { ipifyService.lookupIp().ip.normalizedOr(previous.publicIp) }
            .getOrNull()
            ?.normalizedOr(previous.publicIp)
            ?: return null

        return runCatching {
            freeIpApiService.lookup(fallbackIpAddress).toTravelContextSnapshot(previous = previous)
                .copy(publicIp = fallbackIpAddress)
        }.getOrNull()?.takeIf { it.containsIpContext() }
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

    private fun buildLocalPriceLevelRequest(
        currentDevicePlace: ResolvedVisitedPlace?,
        travelContext: TravelContextSnapshot,
    ): LocalPriceLevelRequest =
        LocalPriceLevelRequest(
            latitude = currentDevicePlace?.latitude,
            longitude = currentDevicePlace?.longitude,
            countryCode = currentDevicePlace?.countryCode?.trim()?.takeIf(String::isNotBlank)?.uppercase()
                ?: travelContext.countryCode?.trim()?.takeIf(String::isNotBlank)?.uppercase(),
            countryName = currentDevicePlace?.country?.trim()?.takeIf(String::isNotBlank)
                ?: travelContext.country?.trim()?.takeIf(String::isNotBlank),
            locality = currentDevicePlace?.city?.trim()?.takeIf(String::isNotBlank)
                ?: travelContext.city?.trim()?.takeIf(String::isNotBlank),
        )

    private fun buildWeatherLocation(
        currentSettings: AppSettings,
        currentDevicePlace: ResolvedVisitedPlace?,
        travelContext: TravelContextSnapshot,
    ): Pair<Double, Double>? {
        if (currentSettings.useCurrentLocationForWeather) {
            val latitude = currentDevicePlace?.latitude
            val longitude = currentDevicePlace?.longitude
            if (latitude != null && longitude != null) {
                return latitude to longitude
            }
        }

        val latitude = travelContext.latitude
        val longitude = travelContext.longitude
        return if (latitude != null && longitude != null) {
            latitude to longitude
        } else {
            null
        }
    }

    private fun unavailableWeatherSummary(
        currentSettings: AppSettings,
    ): String = when {
        currentSettings.useCurrentLocationForWeather &&
            visitedDeviceLocationProvider.hasLocationPermission().not() &&
            currentSettings.publicIpGeolocationEnabled ->
            "Grant location permission or re-enable IP geolocation for weather."

        currentSettings.useCurrentLocationForWeather &&
            visitedDeviceLocationProvider.hasLocationPermission().not() ->
            "Grant location permission for weather."

        currentSettings.useCurrentLocationForWeather ||
            currentSettings.publicIpGeolocationEnabled ->
            "Weather data unavailable"

        else -> "Enable IP geolocation or current location for weather."
    }

    private fun buildEmergencyCareSearchRequest(
        currentDevicePlace: ResolvedVisitedPlace?,
        travelContext: TravelContextSnapshot,
    ): EmergencyCareSearchRequest? {
        currentDevicePlace?.let { place ->
            val latitude = place.latitude
            val longitude = place.longitude
            if (latitude != null && longitude != null) {
                return EmergencyCareSearchRequest(
                    latitude = latitude,
                    longitude = longitude,
                    countryCode = place.countryCode?.trim()?.takeIf(String::isNotBlank)?.uppercase(),
                    countryName = place.country,
                    locationSource = EmergencyCareLocationSource.DEVICE,
                )
            }
        }

        val latitude = travelContext.latitude
        val longitude = travelContext.longitude
        if (latitude != null && longitude != null) {
            return EmergencyCareSearchRequest(
                latitude = latitude,
                longitude = longitude,
                countryCode = travelContext.countryCode?.trim()?.takeIf(String::isNotBlank)?.uppercase(),
                countryName = travelContext.country,
                locationSource = EmergencyCareLocationSource.IP_GEOLOCATION,
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
                weatherCode = weatherCodes.getOrNull(index),
                windSpeedKph = windSpeedsKph.getOrNull(index),
                windDirectionDegrees = windDirectionDegrees.getOrNull(index),
            )
        }

    private fun OpenMeteoHourly?.toHourlyForecast(
        timezone: String?,
        utcOffsetSeconds: Int?,
        referenceTime: Instant,
        offsetsHours: List<Int>,
    ): List<WeatherHourlyForecastSlot> {
        if (this == null) {
            return emptyList()
        }

        val times = parseOpenMeteoTimes(
            rawValues = times,
            timezone = timezone,
            utcOffsetSeconds = utcOffsetSeconds,
        )

        return offsetsHours.mapNotNull { hourOffset ->
            val targetTime = referenceTime.plusSeconds(hourOffset * 3_600L)
            val index = nearestIndex(times, targetTime) ?: return@mapNotNull null
            WeatherHourlyForecastSlot(
                at = times[index],
                summary = weatherCodeSummary(weatherCodes.getOrNull(index)),
                temperatureCelsius = temperatures.getOrNull(index),
                rainChancePercent = rainChance.getOrNull(index),
                windSpeedKph = windSpeedsKph.getOrNull(index),
                windDirectionDegrees = windDirectionDegrees.getOrNull(index),
                weatherCode = weatherCodes.getOrNull(index),
            )
        }
    }

    private fun OpenMeteoMarineResponse.toMarineSnapshot(
        surfSpot: SurfSpotConfiguration,
        windResponse: OpenMeteoResponse,
        referenceTime: Instant,
    ): MarineSnapshot {
        val marineHourly = hourly ?: return MarineSnapshot(
            spotName = surfSpot.displayName(),
            fetchedAt = referenceTime,
        )
        val marineTimes = parseOpenMeteoTimes(
            rawValues = marineHourly.times,
            timezone = timezone,
            utcOffsetSeconds = utcOffsetSeconds,
        )
        val windHourly = windResponse.hourly
        val windTimes = parseOpenMeteoTimes(
            rawValues = windHourly?.times.orEmpty(),
            timezone = windResponse.timezone,
            utcOffsetSeconds = windResponse.utcOffsetSeconds,
        )
        val currentMarineIndex = nearestIndex(marineTimes, referenceTime)
        val currentWindIndex = nearestIndex(windTimes, referenceTime)

        return MarineSnapshot(
            spotName = surfSpot.displayName(),
            sourceName = "Open-Meteo",
            waveHeightMeters = currentMarineIndex?.let(marineHourly.waveHeightMeters::getOrNull),
            wavePeriodSeconds = currentMarineIndex?.let(marineHourly.wavePeriodSeconds::getOrNull),
            swellHeightMeters = currentMarineIndex?.let(marineHourly.swellWaveHeightMeters::getOrNull),
            swellPeriodSeconds = currentMarineIndex?.let(marineHourly.swellWavePeriodSeconds::getOrNull),
            swellDirectionDegrees = currentMarineIndex?.let(marineHourly.swellWaveDirectionDegrees::getOrNull),
            windSpeedKph = currentWindIndex?.let { windResponse.current?.windSpeedKph ?: windHourly?.windSpeedsKph?.getOrNull(it) },
            windGustKph = currentWindIndex?.let { windResponse.current?.windGustKph ?: 0.0 },
            windDirectionDegrees = currentWindIndex?.let { windResponse.current?.windDirectionDegrees ?: windHourly?.windDirectionDegrees?.getOrNull(it) },
            seaSurfaceTemperatureCelsius = currentMarineIndex?.let(marineHourly.seaSurfaceTemperatureCelsius::getOrNull),
            forecastSlots = listOf(3, 6, 12).mapNotNull { hourOffset ->
                val targetTime = referenceTime.plusSeconds(hourOffset * 3_600L)
                val marineIndex = nearestIndex(marineTimes, targetTime) ?: return@mapNotNull null
                val windIndex = nearestIndex(windTimes, targetTime)
                MarineForecastSlot(
                    at = marineTimes[marineIndex],
                    waveHeightMeters = marineHourly.waveHeightMeters.getOrNull(marineIndex),
                    swellHeightMeters = marineHourly.swellWaveHeightMeters.getOrNull(marineIndex),
                    windSpeedKph = windIndex?.let { windHourly?.windSpeedsKph?.getOrNull(it) },
                    windDirectionDegrees = windIndex?.let { windHourly?.windDirectionDegrees?.getOrNull(it) },
                )
            },
            fetchedAt = referenceTime,
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

    private fun powerSummaryDetail(power: com.iloapps.nomaddashboard.core.model.PowerSnapshot): String =
        power.batteryPercent?.let { batteryPercent ->
            listOfNotNull(
                "$batteryPercent% battery",
                power.batteryHealthSummary.takeUnless { it.equals("Estimating", ignoreCase = true) },
                power.powerSourceLabel?.takeUnless { it.equals("Battery", ignoreCase = true) }?.let { "$it power" },
            ).joinToString(" · ")
        } ?: "Collecting power status"

    private fun parseOpenMeteoTimes(
        rawValues: List<String>,
        timezone: String?,
        utcOffsetSeconds: Int?,
    ): List<Instant> {
        val zoneId = timezone?.let(ZoneId::of)
            ?: utcOffsetSeconds?.let { java.time.ZoneOffset.ofTotalSeconds(it) }
            ?: ZoneId.of("UTC")
        return rawValues.mapNotNull { rawValue ->
            runCatching {
                LocalDateTime.parse(rawValue).atZone(zoneId).toInstant()
            }.getOrNull()
        }
    }

    private fun nearestIndex(
        times: List<Instant>,
        target: Instant,
    ): Int? = times
        .mapIndexed { index, instant -> index to kotlin.math.abs(instant.epochSecond - target.epochSecond) }
        .minByOrNull { it.second }
        ?.first

    private fun SurfSpotConfiguration.isConfigured(): Boolean = latitude != null && longitude != null

    private fun SurfSpotConfiguration.hasValidCoordinates(): Boolean =
        (latitude?.let { it in -90.0..90.0 } == true) &&
            (longitude?.let { it in -180.0..180.0 } == true)

    private fun SurfSpotConfiguration.displayName(): String =
        name.takeIf(String::isNotBlank)
            ?: listOfNotNull(
                latitude?.let { String.format("%.3f", it) },
                longitude?.let { String.format("%.3f", it) },
            ).joinToString(", ")
                .ifBlank { "Surf Spot" }

    private fun ResolvedVisitedPlace.toSurfSpotConfiguration(): SurfSpotConfiguration =
        SurfSpotConfiguration(
            name = listOfNotNull(
                city?.trim()?.takeIf(String::isNotBlank),
                country.trim().takeIf(String::isNotBlank),
            ).joinToString(", ").ifBlank { "Current location" },
            latitude = latitude,
            longitude = longitude,
        )

    private fun FreeIpApiResponse.toTravelContextSnapshot(
        previous: TravelContextSnapshot,
    ): TravelContextSnapshot =
        previous.copy(
            publicIp = ipAddress.normalizedOr(previous.publicIp),
            city = cityName.normalizedOr(previous.city),
            region = regionName.normalizedOr(previous.region),
            country = countryName.normalizedOr(previous.country),
            countryCode = countryCode.normalizedOr(previous.countryCode)?.uppercase(),
            latitude = latitude ?: previous.latitude,
            longitude = longitude ?: previous.longitude,
            timeZoneId = timeZone.normalizedOr(timeZones.firstOrNull().normalizedOr(previous.timeZoneId)),
        )

    private fun TravelContextSnapshot.withDeviceLocation(
        currentDevicePlace: ResolvedVisitedPlace?,
    ): TravelContextSnapshot =
        copy(
            deviceCity = currentDevicePlace?.city?.trim()?.takeIf(String::isNotBlank),
            deviceRegion = currentDevicePlace?.region?.trim()?.takeIf(String::isNotBlank),
            deviceCountry = currentDevicePlace?.country?.trim()?.takeIf(String::isNotBlank),
            deviceCountryCode = currentDevicePlace?.countryCode?.trim()?.takeIf(String::isNotBlank)?.uppercase(),
            deviceLatitude = currentDevicePlace?.latitude,
            deviceLongitude = currentDevicePlace?.longitude,
        )

    private fun TravelContextSnapshot.containsIpContext(): Boolean =
        publicIp != null ||
            city != null ||
            region != null ||
            country != null ||
            latitude != null ||
            longitude != null

    private fun travelContextPrimaryLocation(
        travelContext: TravelContextSnapshot,
    ): String? = listOfNotNull(
        travelContext.deviceCity ?: travelContext.city,
        travelContext.deviceCountry ?: travelContext.country,
    ).joinToString(", ").takeIf(String::isNotBlank)

    private fun String?.normalizedOr(fallback: String?): String? =
        this?.trim()?.takeIf(String::isNotBlank) ?: fallback
}

private const val ConnectivityHistoryRetentionCount = 24
private const val PowerHistoryRetentionCount = 48
private const val TravelAlertsLogTag = "NomadTravelAlerts"
private const val ConnectivityDownloadMetricKind = "connectivity_download_mbps"
private const val ConnectivityUploadMetricKind = "connectivity_upload_mbps"
private const val ConnectivityLatencyMetricKind = "connectivity_latency_ms"
private const val PowerBatteryPercentMetricKind = "power_battery_percent"

private fun travelAlertLogDebug(tag: String, message: String) {
    runCatching { Log.d(tag, message) }
}

private fun travelAlertLogInfo(tag: String, message: String) {
    runCatching { Log.i(tag, message) }
}

private fun travelAlertLogWarn(tag: String, message: String, error: Throwable? = null) {
    runCatching {
        if (error != null) {
            Log.w(tag, message, error)
        } else {
            Log.w(tag, message)
        }
    }
}
