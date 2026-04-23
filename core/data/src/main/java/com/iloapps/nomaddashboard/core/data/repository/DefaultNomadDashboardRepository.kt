package com.iloapps.nomaddashboard.core.data.repository

import android.util.Log
import com.iloapps.nomaddashboard.core.common.ApplicationScope
import com.iloapps.nomaddashboard.core.data.credentials.ProviderCredentialStore
import com.iloapps.nomaddashboard.core.data.emergency.EmergencyCareProvider
import com.iloapps.nomaddashboard.core.data.emergency.EmergencyCareSearchRequest
import com.iloapps.nomaddashboard.core.data.fuel.FuelPriceProvider
import com.iloapps.nomaddashboard.core.data.fuel.FuelProviderCredentials
import com.iloapps.nomaddashboard.core.data.fuel.FuelSearchRequest
import com.iloapps.nomaddashboard.core.data.localinfo.LocalInfoLocationSource
import com.iloapps.nomaddashboard.core.data.localinfo.LocalInfoProvider
import com.iloapps.nomaddashboard.core.data.localinfo.LocalInfoRequest
import com.iloapps.nomaddashboard.core.data.location.DeviceLocationResolutionStatus
import com.iloapps.nomaddashboard.core.data.location.DeviceLocationSnapshot
import com.iloapps.nomaddashboard.core.data.location.ResolvedVisitedPlace
import com.iloapps.nomaddashboard.core.data.location.VisitedDeviceLocationProvider
import com.iloapps.nomaddashboard.core.data.localprice.LocalPriceLevelProvider
import com.iloapps.nomaddashboard.core.data.monitor.TelemetryReader
import com.iloapps.nomaddashboard.core.data.timetracking.TimeTrackingRepository
import com.iloapps.nomaddashboard.core.data.travelalerts.BundledNeighborCountryResolver
import com.iloapps.nomaddashboard.core.data.travelalerts.ReliefWebProviderError
import com.iloapps.nomaddashboard.core.data.travelalerts.ReliefWebSecurityProvider
import com.iloapps.nomaddashboard.core.data.travelalerts.SmartravellerAdvisoryProvider
import com.iloapps.nomaddashboard.core.data.travelalerts.TravelAlertDiagnosticError
import com.iloapps.nomaddashboard.core.data.visited.VisitedHistoryStore
import com.iloapps.nomaddashboard.core.data.visited.VisitedObservation
import com.iloapps.nomaddashboard.core.database.dao.DashboardSectionCacheDao
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
import com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot
import com.iloapps.nomaddashboard.core.model.LocalInfoStatus
import com.iloapps.nomaddashboard.core.model.StartupLocationBootstrapPhase
import com.iloapps.nomaddashboard.core.model.StartupLocationBootstrapState
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.yield
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
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
    private val localInfoProvider: LocalInfoProvider,
    private val fuelPriceProvider: FuelPriceProvider,
    private val emergencyCareProvider: EmergencyCareProvider,
    private val timeTrackingRepository: TimeTrackingRepository,
    private val visitedHistoryStore: VisitedHistoryStore,
    private val dashboardSectionCacheDao: DashboardSectionCacheDao,
    private val metricPointDao: MetricPointDao,
    private val visitedDeviceLocationProvider: VisitedDeviceLocationProvider,
    private val providerCredentialStore: ProviderCredentialStore,
    private val smartravellerAdvisoryProvider: SmartravellerAdvisoryProvider,
    private val reliefWebSecurityProvider: ReliefWebSecurityProvider,
    private val neighborCountryResolver: BundledNeighborCountryResolver,
    private val json: Json,
    @ApplicationScope
    private val applicationScope: CoroutineScope,
) : NomadDashboardRepository {
    override val settings: Flow<AppSettings> = settingsDataSource.settings
    override val providerCredentials: Flow<ProviderCredentialSettings> = providerCredentialStore.credentials
    override val visitedPlaces: Flow<List<VisitedPlace>> = visitedHistoryStore.visitedPlaces
    override val visitedCountryDays: Flow<List<VisitedCountryDay>> = visitedHistoryStore.visitedCountryDays

    private val internalSnapshot = MutableStateFlow(DashboardSnapshot())
    override val snapshot: StateFlow<DashboardSnapshot> = internalSnapshot

    private var previousTrafficSample: TrafficSample? = null
    private val refreshCoordinatorMutex = Mutex()
    private var activeRefresh: CompletableDeferred<Unit>? = null

    override suspend fun warmStart() {
        migrateLegacyProviderCredentials()
        val cachedSections = loadCachedDashboardSections(
            entries = dashboardSectionCacheDao.all(),
            json = json,
        )
        internalSnapshot.update { current ->
            current.copy(
                lastRefresh = cachedSections.lastRefresh ?: current.lastRefresh,
                travelContext = cachedSections.travelContext ?: current.travelContext,
                weather = cachedSections.weather ?: current.weather,
                travelAlerts = cachedSections.travelAlerts ?: current.travelAlerts,
                localInfo = cachedSections.localInfo ?: current.localInfo,
                fuelPrices = cachedSections.fuelPrices ?: current.fuelPrices,
                emergencyCare = cachedSections.emergencyCare ?: current.emergencyCare,
            )
        }
    }

    override suspend fun refresh() {
        migrateLegacyProviderCredentials()
        val (refreshSignal, shouldRun) = refreshCoordinatorMutex.withLock {
            activeRefresh?.takeIf { it.isActive }?.let { existing ->
                existing to false
            } ?: CompletableDeferred<Unit>().also { created ->
                activeRefresh = created
            }.let { created ->
                created to true
            }
        }

        if (shouldRun.not()) {
            refreshSignal.await()
            return
        }

        try {
            runCatching {
                performRefresh()
            }.onFailure { error ->
                logWarn("Dashboard refresh failed", error)
                clearRefreshingStateAfterFailure()
            }
            refreshSignal.complete(Unit)
        } finally {
            refreshCoordinatorMutex.withLock {
                if (activeRefresh === refreshSignal) {
                    activeRefresh = null
                }
            }
        }
    }

    private suspend fun performRefresh() = supervisorScope {
        val currentSettings = settings.first()
        val currentProviderCredentials = providerCredentials.first()
        val previousSnapshot = internalSnapshot.value
        val previousTravelContext = previousSnapshot.travelContext
        val hasLocationPermission = visitedDeviceLocationProvider.hasLocationPermission()
        internalSnapshot.update {
            it.copy(
                isRefreshing = true,
                startupLocation = initialStartupLocationState(
                    publicIpGeolocationEnabled = currentSettings.publicIpGeolocationEnabled,
                    hasLocationPermission = hasLocationPermission,
                ),
                weather = it.weather.copy(
                    isRefreshing = currentSettings.useCurrentLocationForWeather || currentSettings.publicIpGeolocationEnabled,
                ),
                travelAlerts = it.travelAlerts.copy(isRefreshing = true),
                localInfo = refreshingLocalInfoSnapshot(
                    enabled = currentSettings.localInfoEnabled,
                    previous = it.localInfo,
                ),
                fuelPrices = refreshingFuelPriceSnapshot(
                    enabled = currentSettings.fuelPricesEnabled,
                    previous = it.fuelPrices,
                ),
                emergencyCare = refreshingEmergencyCareSnapshot(
                    enabled = currentSettings.emergencyCareEnabled,
                    previous = it.emergencyCare,
                ),
            )
        }
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
        val marineDeferred = async {
            refreshMarineSnapshot(
                surfSpot = currentSettings.surfSpot,
                refreshedAt = refreshedAt,
            )
        }
        val deviceLocationDeferred = async { resolveStartupDeviceLocation(hasLocationPermission) }
        val ipTravelContextDeferred = async {
            resolveIpTravelContext(
                currentSettings = currentSettings,
                previous = previousTravelContext,
            )
        }
        try {
            val locationContext = resolveInitialLocationContext(
                currentSettings = currentSettings,
                previousTravelContext = previousTravelContext,
                hasLocationPermission = hasLocationPermission,
                deviceLocationDeferred = deviceLocationDeferred,
                ipTravelContextDeferred = ipTravelContextDeferred,
            )

            recordVisitedObservations(
                currentSettings = currentSettings,
                locationContext = locationContext,
            )

            val initialSections = refreshLocationDependentSections(
                currentSettings = currentSettings,
                providerCredentials = currentProviderCredentials,
                previousSnapshot = previousSnapshot,
                locationContext = locationContext,
                refreshedAt = refreshedAt,
                keepRefreshing = locationContext.awaitingDevicePromotion,
            )

            persistSectionCaches(
                refreshedAt = refreshedAt,
                travelContext = locationContext.travelContext,
                sections = initialSections,
            )

            val visitedPlaces = visitedHistoryStore.visitedPlaces.first()
            val visitedCountryDays = visitedHistoryStore.visitedCountryDays.first()
            val activeTimeTracking = timeTrackingRepository.currentActiveEntry()
            val pendingTimeTracking = timeTrackingRepository.pendingEntries.first()
            val timeTrackingReport = timeTrackingRepository.report.first()
            val marine = marineDeferred.await()

            internalSnapshot.value = buildDashboardSnapshot(
                refreshedAt = refreshedAt,
                isRefreshing = locationContext.awaitingDevicePromotion,
                connectivity = connectivityWithHistory,
                power = power,
                travelContext = locationContext.travelContext,
                startupLocation = locationContext.startupLocation,
                weather = initialSections.weather,
                marine = marine,
                travelAlerts = initialSections.travelAlerts,
                localInfo = initialSections.localInfo,
                fuelPrices = initialSections.fuelPrices,
                emergencyCare = initialSections.emergencyCare,
                currentSettings = currentSettings,
                activeTimeTracking = activeTimeTracking,
                pendingTimeTrackingCount = pendingTimeTracking.size,
                timeTrackingReport = timeTrackingReport,
                visitedPlaceSummary = visitedPlaces.visitedPlaceSummary(),
                trackedDays = visitedCountryDays.size,
            )

            if (locationContext.awaitingDevicePromotion.not()) {
                return@supervisorScope
            }

            val promotedLocation = awaitPromotedDeviceLocation(
                initial = locationContext,
                deviceLocationDeferred = deviceLocationDeferred,
            ) ?: run {
                internalSnapshot.update {
                    it.copy(
                        isRefreshing = false,
                        startupLocation = it.startupLocation.copy(isChecking = false),
                        weather = it.weather.copy(isRefreshing = false),
                        travelAlerts = it.travelAlerts.copy(isRefreshing = false),
                        localInfo = it.localInfo.copy(isRefreshing = false),
                        fuelPrices = it.fuelPrices.copy(isRefreshing = false),
                        emergencyCare = it.emergencyCare.copy(isRefreshing = false),
                    )
                }
                return@supervisorScope
            }

            internalSnapshot.update {
                it.copy(
                    isRefreshing = true,
                    travelContext = promotedLocation.travelContext,
                    startupLocation = promotedLocation.startupLocation.copy(isChecking = true),
                    weather = it.weather.copy(isRefreshing = true),
                    travelAlerts = it.travelAlerts.copy(isRefreshing = true),
                    localInfo = it.localInfo.copy(isRefreshing = true),
                    fuelPrices = it.fuelPrices.copy(isRefreshing = true),
                    emergencyCare = it.emergencyCare.copy(isRefreshing = true),
                )
            }

            recordVisitedObservations(
                currentSettings = currentSettings,
                locationContext = promotedLocation,
            )

            val promotedSections = refreshLocationDependentSections(
                currentSettings = currentSettings,
                providerCredentials = currentProviderCredentials,
                previousSnapshot = internalSnapshot.value,
                locationContext = promotedLocation,
                refreshedAt = Instant.now(),
                keepRefreshing = false,
            )

            persistSectionCaches(
                refreshedAt = Instant.now(),
                travelContext = promotedLocation.travelContext,
                sections = promotedSections,
            )

            internalSnapshot.value = buildDashboardSnapshot(
                refreshedAt = Instant.now(),
                isRefreshing = false,
                connectivity = connectivityWithHistory,
                power = power,
                travelContext = promotedLocation.travelContext,
                startupLocation = promotedLocation.startupLocation,
                weather = promotedSections.weather,
                marine = marine,
                travelAlerts = promotedSections.travelAlerts,
                localInfo = promotedSections.localInfo,
                fuelPrices = promotedSections.fuelPrices,
                emergencyCare = promotedSections.emergencyCare,
                currentSettings = currentSettings,
                activeTimeTracking = activeTimeTracking,
                pendingTimeTrackingCount = pendingTimeTracking.size,
                timeTrackingReport = timeTrackingReport,
                visitedPlaceSummary = visitedPlaces.visitedPlaceSummary(),
                trackedDays = visitedCountryDays.size,
            )
        } finally {
            marineDeferred.cancel()
            deviceLocationDeferred.cancel()
            ipTravelContextDeferred.cancel()
        }
    }

    private suspend fun clearRefreshingStateAfterFailure() {
        internalSnapshot.update {
            it.copy(
                isRefreshing = false,
                startupLocation = it.startupLocation.copy(isChecking = false),
                weather = it.weather.copy(isRefreshing = false),
                travelAlerts = it.travelAlerts.copy(isRefreshing = false),
                localInfo = it.localInfo.copy(isRefreshing = false),
                fuelPrices = it.fuelPrices.copy(isRefreshing = false),
                emergencyCare = it.emergencyCare.copy(isRefreshing = false),
            )
        }
    }

    private suspend fun resolveInitialLocationContext(
        currentSettings: AppSettings,
        previousTravelContext: TravelContextSnapshot,
        hasLocationPermission: Boolean,
        deviceLocationDeferred: Deferred<DeviceLocationSnapshot>,
        ipTravelContextDeferred: Deferred<TravelContextSnapshot>,
    ): ResolvedLocationContext {
        yield()
        val earlyDeviceLocation = if (hasLocationPermission) {
            runCatching {
                if (deviceLocationDeferred.isActive.not()) deviceLocationDeferred.await() else null
            }.getOrNull()
        } else {
            DeviceLocationSnapshot(status = DeviceLocationResolutionStatus.PERMISSION_MISSING)
        }

        if (earlyDeviceLocation?.hasCoordinates == true) {
            val ipTravelContext = if (currentSettings.publicIpGeolocationEnabled) {
                runCatching {
                    if (ipTravelContextDeferred.isActive.not()) ipTravelContextDeferred.await() else null
                }.getOrNull()
                    ?: previousTravelContext.takeIf { it.containsIpContext() }
                    ?: TravelContextSnapshot()
            } else {
                TravelContextSnapshot()
            }
            return ResolvedLocationContext(
                deviceLocation = earlyDeviceLocation,
                currentDevicePlace = earlyDeviceLocation.toResolvedVisitedPlaceOrNull(),
                travelContext = ipTravelContext.withDeviceLocation(earlyDeviceLocation),
                startupLocation = StartupLocationBootstrapState(
                    phase = StartupLocationBootstrapPhase.USING_DEVICE_LOCATION,
                ),
                awaitingDevicePromotion = false,
            )
        }

        val ipTravelContext = if (currentSettings.publicIpGeolocationEnabled) {
            runCatching {
                if (ipTravelContextDeferred.isActive.not()) ipTravelContextDeferred.await() else null
            }.getOrNull() ?: ipTravelContextDeferred.await()
        } else {
            TravelContextSnapshot()
        }

        if (ipTravelContext.containsIpContext()) {
            val interimDeviceLocation = earlyDeviceLocation ?: if (hasLocationPermission) {
                DeviceLocationSnapshot(status = DeviceLocationResolutionStatus.NO_FIX)
            } else {
                DeviceLocationSnapshot(status = DeviceLocationResolutionStatus.PERMISSION_MISSING)
            }
            return ResolvedLocationContext(
                deviceLocation = interimDeviceLocation,
                currentDevicePlace = interimDeviceLocation.toResolvedVisitedPlaceOrNull(),
                travelContext = ipTravelContext.withDeviceLocation(interimDeviceLocation),
                startupLocation = StartupLocationBootstrapState(
                    phase = StartupLocationBootstrapPhase.FALLING_BACK_TO_IP_LOCATION,
                    isChecking = hasLocationPermission,
                ),
                awaitingDevicePromotion = hasLocationPermission,
            )
        }

        val finalDeviceLocation = if (hasLocationPermission) {
            deviceLocationDeferred.await()
        } else {
            DeviceLocationSnapshot(status = DeviceLocationResolutionStatus.PERMISSION_MISSING)
        }

        return ResolvedLocationContext(
            deviceLocation = finalDeviceLocation,
            currentDevicePlace = finalDeviceLocation.toResolvedVisitedPlaceOrNull(),
            travelContext = TravelContextSnapshot().withDeviceLocation(finalDeviceLocation),
            startupLocation = resolvedStartupLocationState(
                deviceLocation = finalDeviceLocation,
                travelContext = TravelContextSnapshot(),
            ),
            awaitingDevicePromotion = false,
        )
    }

    private suspend fun awaitPromotedDeviceLocation(
        initial: ResolvedLocationContext,
        deviceLocationDeferred: Deferred<DeviceLocationSnapshot>,
    ): ResolvedLocationContext? {
        if (initial.awaitingDevicePromotion.not()) {
            return null
        }

        val promotedDeviceLocation = runCatching { deviceLocationDeferred.await() }
            .getOrElse {
                DeviceLocationSnapshot(status = DeviceLocationResolutionStatus.NO_FIX)
            }
        if (shouldPromoteDeviceLocation(initial.deviceLocation, promotedDeviceLocation).not()) {
            return null
        }

        return ResolvedLocationContext(
            deviceLocation = promotedDeviceLocation,
            currentDevicePlace = promotedDeviceLocation.toResolvedVisitedPlaceOrNull(),
            travelContext = initial.travelContext.withDeviceLocation(promotedDeviceLocation),
            startupLocation = StartupLocationBootstrapState(
                phase = StartupLocationBootstrapPhase.USING_DEVICE_LOCATION,
            ),
            awaitingDevicePromotion = false,
        )
    }

    private fun shouldPromoteDeviceLocation(
        previous: DeviceLocationSnapshot,
        candidate: DeviceLocationSnapshot,
    ): Boolean = candidate.hasCoordinates && (
        previous.hasCoordinates.not() ||
            (previous.hasPlace.not() && candidate.hasPlace)
        )

    private suspend fun resolveIpTravelContext(
        currentSettings: AppSettings,
        previous: TravelContextSnapshot,
    ): TravelContextSnapshot =
        if (currentSettings.publicIpGeolocationEnabled) {
            fetchCurrentTravelContext(previous = previous)
                ?: previous.takeIf { it.containsIpContext() }
                ?: TravelContextSnapshot()
        } else {
            TravelContextSnapshot()
        }

    private suspend fun recordVisitedObservations(
        currentSettings: AppSettings,
        locationContext: ResolvedLocationContext,
    ) {
        if (currentSettings.visitedPlacesEnabled.not()) {
            return
        }

        val observedAt = Instant.now()
        buildIpObservation(locationContext.travelContext, observedAt)?.let { observation ->
            runCatching { visitedHistoryStore.recordObservation(observation) }
        }

        if (currentSettings.useCurrentLocationForVisitedPlaces) {
            locationContext.currentDevicePlace?.let { place ->
                runCatching {
                    visitedHistoryStore.recordObservation(place.toObservation(observedAt))
                }
            }
        }
    }

    private suspend fun refreshLocationDependentSections(
        currentSettings: AppSettings,
        providerCredentials: ProviderCredentialSettings,
        previousSnapshot: DashboardSnapshot,
        locationContext: ResolvedLocationContext,
        refreshedAt: Instant,
        keepRefreshing: Boolean,
    ): LocationDependentSections = supervisorScope {
        val weatherDeferred = async {
            refreshWeatherSnapshot(
                currentSettings = currentSettings,
                previous = previousSnapshot.weather,
                deviceLocation = locationContext.deviceLocation,
                travelContext = locationContext.travelContext,
                refreshedAt = refreshedAt,
                keepRefreshing = keepRefreshing,
            )
        }
        val travelAlertsDeferred = async {
            refreshTravelAlertsSnapshot(
                previous = previousSnapshot.travelAlerts,
                currentDevicePlace = locationContext.currentDevicePlace,
                travelContext = locationContext.travelContext,
                providerCredentials = providerCredentials,
                keepRefreshing = keepRefreshing,
            )
        }
        val localInfoDeferred = async {
            refreshLocalInfoSnapshot(
                currentSettings = currentSettings,
                providerCredentials = providerCredentials,
                currentDevicePlace = locationContext.currentDevicePlace,
                travelContext = locationContext.travelContext,
                previous = previousSnapshot.localInfo,
                keepRefreshing = keepRefreshing,
            )
        }
        val fuelDeferred = async {
            refreshFuelPriceSnapshot(
                currentSettings = currentSettings,
                providerCredentials = providerCredentials,
                deviceLocation = locationContext.deviceLocation,
                currentDevicePlace = locationContext.currentDevicePlace,
                travelContext = locationContext.travelContext,
                previous = previousSnapshot.fuelPrices,
                keepRefreshing = keepRefreshing,
            )
        }
        val emergencyDeferred = async {
            refreshEmergencyCareSnapshot(
                currentSettings = currentSettings,
                deviceLocation = locationContext.deviceLocation,
                travelContext = locationContext.travelContext,
                previous = previousSnapshot.emergencyCare,
                keepRefreshing = keepRefreshing,
            )
        }

        LocationDependentSections(
            weather = weatherDeferred.await(),
            travelAlerts = travelAlertsDeferred.await(),
            localInfo = localInfoDeferred.await(),
            fuelPrices = fuelDeferred.await(),
            emergencyCare = emergencyDeferred.await(),
        )
    }

    private suspend fun refreshWeatherSnapshot(
        currentSettings: AppSettings,
        previous: WeatherSnapshot,
        deviceLocation: DeviceLocationSnapshot,
        travelContext: TravelContextSnapshot,
        refreshedAt: Instant,
        keepRefreshing: Boolean,
    ): WeatherSnapshot {
        val weatherLocation = buildWeatherLocation(
            currentSettings = currentSettings,
            deviceLocation = deviceLocation,
            travelContext = travelContext,
        )
        if (weatherLocation == null) {
            val fallback = previous.takeIf { it.fetchedAt != null }
            return (fallback ?: WeatherSnapshot(
                summary = unavailableWeatherSummary(currentSettings),
                conditionDescription = "Weather unavailable",
            )).copy(isRefreshing = keepRefreshing)
        }

        return runCatching {
            retryTransient {
                openMeteoService.forecast(
                    latitude = weatherLocation.first,
                    longitude = weatherLocation.second,
                    forecastDays = 7,
                )
            }
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
                isRefreshing = keepRefreshing,
            )
        }.getOrElse { error ->
            logWarn("Weather refresh failed", error)
            previous.takeIf { it.fetchedAt != null }?.copy(isRefreshing = false)
                ?: WeatherSnapshot(
                    summary = "Weather unavailable",
                    conditionDescription = "Weather unavailable",
                    isRefreshing = false,
                )
        }
    }

    private suspend fun refreshTravelAlertsSnapshot(
        previous: TravelAlertsSnapshot,
        currentDevicePlace: ResolvedVisitedPlace?,
        travelContext: TravelContextSnapshot,
        providerCredentials: ProviderCredentialSettings,
        keepRefreshing: Boolean,
    ): TravelAlertsSnapshot {
        val primaryCountryCode = currentDevicePlace?.countryCode
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.uppercase()
            ?: travelContext.countryCode?.trim()?.takeIf(String::isNotBlank)?.uppercase()
        val primaryCountryName = currentDevicePlace?.country?.trim()?.takeIf(String::isNotBlank)
            ?: travelContext.country?.trim()?.takeIf(String::isNotBlank)
        val coverageCountryCodes = primaryCountryCode?.let { primary ->
            listOf(primary) + neighborCountryResolver.neighboringCountryCodes(primary)
        }.orEmpty().map { it.uppercase() }.distinct()

        return refreshTravelAlerts(
            previous = previous,
            primaryCountryCode = primaryCountryCode,
            primaryCountryName = primaryCountryName,
            coverageCountryCodes = coverageCountryCodes,
            providerCredentials = providerCredentials,
        ).copy(isRefreshing = keepRefreshing)
    }

    private suspend fun refreshLocalInfoSnapshot(
        currentSettings: AppSettings,
        providerCredentials: ProviderCredentialSettings,
        currentDevicePlace: ResolvedVisitedPlace?,
        travelContext: TravelContextSnapshot,
        previous: LocalInfoSnapshot,
        keepRefreshing: Boolean,
    ): LocalInfoSnapshot =
        runCatching {
            refreshLocalInfo(
                currentSettings = currentSettings,
                currentProviderCredentials = providerCredentials,
                currentDevicePlace = currentDevicePlace,
                travelContext = travelContext,
            ).copy(isRefreshing = keepRefreshing)
        }.getOrElse { error ->
            logWarn("Local Info refresh failed", error)
            previous.takeIf { it.fetchedAt != null }?.copy(isRefreshing = false)
                ?: LocalInfoSnapshot(
                    status = LocalInfoStatus.UNAVAILABLE,
                    detail = "Local Info is unavailable right now.",
                    isRefreshing = false,
                )
        }

    private suspend fun refreshFuelPriceSnapshot(
        currentSettings: AppSettings,
        providerCredentials: ProviderCredentialSettings,
        deviceLocation: DeviceLocationSnapshot,
        currentDevicePlace: ResolvedVisitedPlace?,
        travelContext: TravelContextSnapshot,
        previous: FuelPriceSnapshot,
        keepRefreshing: Boolean,
    ): FuelPriceSnapshot {
        if (currentSettings.fuelPricesEnabled.not()) {
            return FuelPriceSnapshot(detail = "Enable fuel prices in Settings")
        }

        val request = buildFuelSearchRequest(
            deviceLocation = deviceLocation,
            travelContext = travelContext,
        ) ?: return previous.takeIf { it.fetchedAt != null }?.copy(isRefreshing = false)
            ?: FuelPriceSnapshot(
                status = FuelPriceStatus.UNAVAILABLE,
                sourceName = "Nomad Fuel Prices",
                countryCode = currentDevicePlace?.countryCode ?: travelContext.countryCode,
                countryName = currentDevicePlace?.country ?: travelContext.country,
                detail = "Nearby fuel prices need a resolved current location and country.",
            )

        return runCatching {
            retryTransient {
                fuelPriceProvider.prices(
                    request = request,
                    credentials = FuelProviderCredentials(
                        tankerkoenigApiKey = providerCredentials.tankerkoenigApiKey,
                    ),
                )
            }.copy(isRefreshing = keepRefreshing)
        }.getOrElse { error ->
            logWarn("Fuel refresh failed", error)
            previous.takeIf { it.fetchedAt != null }?.copy(isRefreshing = false)
                ?: FuelPriceSnapshot(
                    status = FuelPriceStatus.UNAVAILABLE,
                    sourceName = "Nomad Fuel Prices",
                    countryCode = currentDevicePlace?.countryCode ?: travelContext.countryCode,
                    countryName = currentDevicePlace?.country ?: travelContext.country,
                    detail = "Nearby fuel prices are unavailable right now.",
                )
        }
    }

    private suspend fun refreshEmergencyCareSnapshot(
        currentSettings: AppSettings,
        deviceLocation: DeviceLocationSnapshot,
        travelContext: TravelContextSnapshot,
        previous: EmergencyCareSnapshot,
        keepRefreshing: Boolean,
    ): EmergencyCareSnapshot =
        runCatching {
            retryTransient {
                refreshEmergencyCare(
                    currentSettings = currentSettings,
                    deviceLocation = deviceLocation,
                    travelContext = travelContext,
                ).also { snapshot ->
                    if (snapshot.status == EmergencyCareStatus.ERROR) {
                        throw java.io.IOException(snapshot.detail)
                    }
                }
            }.copy(isRefreshing = keepRefreshing)
        }.getOrElse { error ->
            logWarn("Emergency care refresh failed", error)
            previous.takeIf { it.fetchedAt != null }?.copy(isRefreshing = false)
                ?: EmergencyCareSnapshot(
                    status = EmergencyCareStatus.ERROR,
                    detail = "Nearby hospitals are unavailable right now.",
                    isRefreshing = false,
                )
        }

    private suspend fun persistSectionCaches(
        refreshedAt: Instant,
        travelContext: TravelContextSnapshot,
        sections: LocationDependentSections,
    ) {
        if (travelContext.containsIpContext() || travelContext.deviceLatitude != null || travelContext.deviceLongitude != null) {
            dashboardSectionCacheDao.upsert(travelContext.toCacheEntity(json = json, fetchedAt = refreshedAt))
        }
        sections.weather.toCacheEntity(json)?.let { dashboardSectionCacheDao.upsert(it) }
        sections.travelAlerts.toCacheEntity(json)?.let { dashboardSectionCacheDao.upsert(it) }
        sections.localInfo.toCacheEntity(json)?.let { dashboardSectionCacheDao.upsert(it) }
        sections.fuelPrices.toCacheEntity(json)?.let { dashboardSectionCacheDao.upsert(it) }
        sections.emergencyCare.toCacheEntity(json)?.let { dashboardSectionCacheDao.upsert(it) }
    }

    private fun buildDashboardSnapshot(
        refreshedAt: Instant,
        isRefreshing: Boolean,
        connectivity: com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot,
        power: com.iloapps.nomaddashboard.core.model.PowerSnapshot,
        travelContext: TravelContextSnapshot,
        startupLocation: StartupLocationBootstrapState,
        weather: WeatherSnapshot,
        marine: MarineSnapshot?,
        travelAlerts: TravelAlertsSnapshot,
        localInfo: LocalInfoSnapshot,
        fuelPrices: FuelPriceSnapshot,
        emergencyCare: EmergencyCareSnapshot,
        currentSettings: AppSettings,
        activeTimeTracking: com.iloapps.nomaddashboard.core.model.TimeTrackingRecord?,
        pendingTimeTrackingCount: Int,
        timeTrackingReport: com.iloapps.nomaddashboard.core.model.TimeTrackingReportSnapshot,
        visitedPlaceSummary: com.iloapps.nomaddashboard.core.model.VisitedPlaceSummary,
        trackedDays: Int,
    ): DashboardSnapshot {
        val overallHeadline = when {
            connectivity.isOnline && (power.batteryPercent ?: 0) > 20 -> "Ready"
            connectivity.isOnline -> "Connected"
            else -> "Waiting"
        }

        return DashboardSnapshot(
            lastRefresh = refreshedAt,
            isRefreshing = isRefreshing,
            overallSummary = SummaryTile(
                title = "Overall",
                headline = overallHeadline,
                detail = "${travelContextPrimaryLocation(travelContext) ?: "Travel-ready"} system telemetry",
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
            connectivity = connectivity,
            power = power,
            travelContext = travelContext,
            startupLocation = startupLocation,
            weather = weather,
            marine = marine,
            travelAlerts = travelAlerts,
            localInfo = localInfo,
            fuelPrices = fuelPrices,
            emergencyCare = emergencyCare,
            timeTracking = TimeTrackingDashboardState(
                enabled = currentSettings.projectTimeTrackingEnabled,
                headline = when {
                    currentSettings.projectTimeTrackingEnabled.not() -> "Disabled"
                    activeTimeTracking != null -> "Running"
                    pendingTimeTrackingCount > 0 -> "Paused"
                    else -> "Ready"
                },
                detail = when {
                    currentSettings.projectTimeTrackingEnabled.not() -> "Turn this on to enable the live unallocated buffer."
                    activeTimeTracking != null -> {
                        val localTime = DateTimeFormatter.ofPattern("HH:mm")
                            .format(activeTimeTracking.entry.startAt.atZone(ZoneId.systemDefault()))
                        val mode = if (activeTimeTracking.entry.isAutomaticallyTracked()) "Auto" else "Manual"
                        "$mode capture since $localTime · ${timeTrackingReport.interruptionsToday} interruption(s) today"
                    }
                    pendingTimeTrackingCount > 0 ->
                        "$pendingTimeTrackingCount buffered segment(s) waiting for allocation · ${timeTrackingReport.interruptionsToday} interruption(s) today."
                    else -> "Dashboard quick-allocation is ready once your projects are set up."
                },
                interruptionsToday = timeTrackingReport.interruptionsToday,
                estimatedFocusLoss = timeTrackingReport.todaysEstimatedFocusLoss,
                estimatedFocusTime = timeTrackingReport.todaysEstimatedFocusTime,
            ),
            visited = VisitedSummary(
                citiesVisited = visitedPlaceSummary.citiesVisited,
                countriesVisited = visitedPlaceSummary.countriesVisited,
                trackedDays = trackedDays,
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
        visitedDeviceLocationProvider.currentLocation().toSurfSpotConfigurationOrNull()

    private fun initialStartupLocationState(
        publicIpGeolocationEnabled: Boolean,
        hasLocationPermission: Boolean,
    ): StartupLocationBootstrapState = when {
        hasLocationPermission -> StartupLocationBootstrapState(
            phase = StartupLocationBootstrapPhase.CHECKING_DEVICE_LOCATION,
            isChecking = true,
        )
        publicIpGeolocationEnabled -> StartupLocationBootstrapState(
            phase = StartupLocationBootstrapPhase.FALLING_BACK_TO_IP_LOCATION,
        )
        else -> StartupLocationBootstrapState(
            phase = StartupLocationBootstrapPhase.NO_LOCATION_SOURCE_AVAILABLE,
        )
    }

    private suspend fun resolveStartupDeviceLocation(
        hasLocationPermission: Boolean,
    ): DeviceLocationSnapshot {
        if (hasLocationPermission.not()) {
            return DeviceLocationSnapshot(status = DeviceLocationResolutionStatus.PERMISSION_MISSING)
        }

        return withTimeoutOrNull(StartupLocationBootstrapWaitMillis) {
            runCatching { visitedDeviceLocationProvider.currentLocation() }
                .getOrElse {
                    DeviceLocationSnapshot(status = DeviceLocationResolutionStatus.NO_FIX)
                }
        } ?: DeviceLocationSnapshot(status = DeviceLocationResolutionStatus.NO_FIX)
    }

    private fun resolvedStartupLocationState(
        deviceLocation: DeviceLocationSnapshot,
        travelContext: TravelContextSnapshot,
    ): StartupLocationBootstrapState = when {
        deviceLocation.hasCoordinates -> StartupLocationBootstrapState(
            phase = StartupLocationBootstrapPhase.USING_DEVICE_LOCATION,
        )
        travelContext.containsIpContext() -> StartupLocationBootstrapState(
            phase = StartupLocationBootstrapPhase.FALLING_BACK_TO_IP_LOCATION,
        )
        else -> StartupLocationBootstrapState(
            phase = StartupLocationBootstrapPhase.NO_LOCATION_SOURCE_AVAILABLE,
        )
    }

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
            val signal = retryTransient { fetch() }
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
            val previousSignal = previous?.resolvedSignal ?: previous?.signal
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
                    lastSuccessAt = previous?.lastSuccessAt,
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
        deviceLocation: DeviceLocationSnapshot,
        travelContext: TravelContextSnapshot,
    ): EmergencyCareSnapshot {
        if (currentSettings.emergencyCareEnabled.not()) {
            return EmergencyCareSnapshot(detail = "Enable emergency care in Settings")
        }

        val request = buildEmergencyCareSearchRequest(
            deviceLocation = deviceLocation,
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

    private fun refreshingEmergencyCareSnapshot(
        enabled: Boolean,
        previous: EmergencyCareSnapshot,
    ): EmergencyCareSnapshot =
        if (enabled.not()) {
            EmergencyCareSnapshot(detail = "Enable emergency care in Settings")
        } else {
            previous.copy(isRefreshing = true)
        }

    private suspend fun refreshTravelContext(
        currentSettings: AppSettings,
        previous: TravelContextSnapshot,
        deviceLocation: DeviceLocationSnapshot,
    ): TravelContextSnapshot {
        val ipTravelContext = if (currentSettings.publicIpGeolocationEnabled) {
            fetchCurrentTravelContext(previous = previous)
                ?: previous.takeIf { it.containsIpContext() }
                ?: TravelContextSnapshot()
        } else {
            TravelContextSnapshot()
        }

        return ipTravelContext.withDeviceLocation(deviceLocation)
    }

    private suspend fun refreshLocalInfo(
        currentSettings: AppSettings,
        currentProviderCredentials: ProviderCredentialSettings,
        currentDevicePlace: ResolvedVisitedPlace?,
        travelContext: TravelContextSnapshot,
    ): LocalInfoSnapshot {
        if (currentSettings.localInfoEnabled.not()) {
            return LocalInfoSnapshot(
                status = LocalInfoStatus.OFF,
                detail = "Local Info is disabled. Enable it in Settings.",
            )
        }

        val request = buildLocalInfoRequest(
            currentDevicePlace = currentDevicePlace,
            travelContext = travelContext,
        )
        return retryTransient {
            localInfoProvider.localInfo(
                request = request,
                hudUserApiToken = currentProviderCredentials.hudUserApiToken,
            )
        }
    }

    private suspend fun fetchCurrentTravelContext(
        previous: TravelContextSnapshot,
    ): TravelContextSnapshot? {
        runCatching {
            retryTransient {
                freeIpApiService.lookupMe()
            }.toTravelContextSnapshot(previous = previous)
        }.getOrNull()?.takeIf { it.containsIpContext() }?.let { return it }

        val fallbackIpAddress = runCatching {
            retryTransient { ipifyService.lookupIp() }.ip.normalizedOr(previous.publicIp)
        }
            .getOrNull()
            ?.normalizedOr(previous.publicIp)
            ?: return null

        return runCatching {
            retryTransient {
                freeIpApiService.lookup(fallbackIpAddress)
            }.toTravelContextSnapshot(previous = previous)
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
        deviceLocation: DeviceLocationSnapshot,
        travelContext: TravelContextSnapshot,
    ): FuelSearchRequest? {
        if (deviceLocation.hasCoordinates) {
            val latitude = deviceLocation.latitude
            val longitude = deviceLocation.longitude
            val countryCode = deviceLocation.countryCode?.trim()?.takeIf(String::isNotBlank)?.uppercase()
                ?: travelContext.countryCode?.trim()?.takeIf(String::isNotBlank)?.uppercase()
            if (latitude != null && longitude != null && countryCode != null) {
                return FuelSearchRequest(
                    latitude = latitude,
                    longitude = longitude,
                    countryCode = countryCode,
                    countryName = deviceLocation.country ?: travelContext.country,
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

    private fun buildLocalInfoRequest(
        currentDevicePlace: ResolvedVisitedPlace?,
        travelContext: TravelContextSnapshot,
    ): LocalInfoRequest {
        val usingDeviceLocation = currentDevicePlace != null
        val countryCode = currentDevicePlace?.countryCode?.trim()?.takeIf(String::isNotBlank)?.uppercase()
            ?: travelContext.countryCode?.trim()?.takeIf(String::isNotBlank)?.uppercase()
        val countryName = currentDevicePlace?.country?.trim()?.takeIf(String::isNotBlank)
            ?: travelContext.country?.trim()?.takeIf(String::isNotBlank)
        val timezoneId = when {
            travelContext.timeZoneId.isNullOrBlank().not() &&
                (usingDeviceLocation.not() || currentDevicePlace?.countryCode.equals(travelContext.countryCode, ignoreCase = true)) ->
                travelContext.timeZoneId
            else -> ZoneId.systemDefault().id
        }

        return LocalInfoRequest(
            latitude = currentDevicePlace?.latitude ?: travelContext.latitude,
            longitude = currentDevicePlace?.longitude ?: travelContext.longitude,
            locality = currentDevicePlace?.city?.trim()?.takeIf(String::isNotBlank)
                ?: travelContext.city?.trim()?.takeIf(String::isNotBlank),
            region = currentDevicePlace?.region?.trim()?.takeIf(String::isNotBlank)
                ?: travelContext.region?.trim()?.takeIf(String::isNotBlank),
            countryCode = countryCode,
            countryName = countryName,
            timeZoneId = timezoneId,
            locationSource = if (usingDeviceLocation) {
                LocalInfoLocationSource.DEVICE
            } else {
                LocalInfoLocationSource.IP_GEOLOCATION
            },
        )
    }

    private fun checkingLocalInfoSnapshot(
        enabled: Boolean,
        previous: LocalInfoSnapshot,
    ): LocalInfoSnapshot =
        if (enabled.not()) {
            LocalInfoSnapshot(
                status = LocalInfoStatus.OFF,
                detail = "Local Info is disabled. Enable it in Settings.",
            )
        } else {
            previous.copy(isRefreshing = true)
        }

    private fun refreshingLocalInfoSnapshot(
        enabled: Boolean,
        previous: LocalInfoSnapshot,
    ): LocalInfoSnapshot = checkingLocalInfoSnapshot(enabled = enabled, previous = previous)

    private fun refreshingFuelPriceSnapshot(
        enabled: Boolean,
        previous: FuelPriceSnapshot,
    ): FuelPriceSnapshot =
        if (enabled.not()) {
            FuelPriceSnapshot(detail = "Enable fuel prices in Settings")
        } else {
            previous.copy(isRefreshing = true)
        }

    private fun loadingEmergencyCareSnapshot(
        enabled: Boolean,
        previous: EmergencyCareSnapshot,
    ): EmergencyCareSnapshot =
        refreshingEmergencyCareSnapshot(enabled = enabled, previous = previous)

    private fun buildWeatherLocation(
        currentSettings: AppSettings,
        deviceLocation: DeviceLocationSnapshot,
        travelContext: TravelContextSnapshot,
    ): Pair<Double, Double>? {
        if (currentSettings.useCurrentLocationForWeather) {
            val latitude = deviceLocation.latitude
            val longitude = deviceLocation.longitude
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
        deviceLocation: DeviceLocationSnapshot,
        travelContext: TravelContextSnapshot,
    ): EmergencyCareSearchRequest? {
        if (deviceLocation.hasCoordinates) {
            val latitude = deviceLocation.latitude
            val longitude = deviceLocation.longitude
            if (latitude != null && longitude != null) {
                return EmergencyCareSearchRequest(
                    latitude = latitude,
                    longitude = longitude,
                    countryCode = deviceLocation.countryCode?.trim()?.takeIf(String::isNotBlank)?.uppercase()
                        ?: travelContext.countryCode?.trim()?.takeIf(String::isNotBlank)?.uppercase(),
                    countryName = deviceLocation.country ?: travelContext.country,
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

    private fun DeviceLocationSnapshot.toSurfSpotConfigurationOrNull(): SurfSpotConfiguration? {
        val latitude = latitude ?: return null
        val longitude = longitude ?: return null
        return SurfSpotConfiguration(
            name = listOfNotNull(
                city?.trim()?.takeIf(String::isNotBlank),
                country?.trim()?.takeIf(String::isNotBlank),
            ).joinToString(", ").ifBlank { "Current location" },
            latitude = latitude,
            longitude = longitude,
        )
    }

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
        deviceLocation: DeviceLocationSnapshot,
    ): TravelContextSnapshot =
        copy(
            deviceCity = deviceLocation.city?.trim()?.takeIf(String::isNotBlank),
            deviceRegion = deviceLocation.region?.trim()?.takeIf(String::isNotBlank),
            deviceCountry = deviceLocation.country?.trim()?.takeIf(String::isNotBlank),
            deviceCountryCode = deviceLocation.countryCode?.trim()?.takeIf(String::isNotBlank)?.uppercase(),
            deviceLatitude = deviceLocation.latitude,
            deviceLongitude = deviceLocation.longitude,
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
private const val StartupLocationBootstrapWaitMillis = 4_000L
private const val DevicePreferredGraceMillis = 1_500L
private const val IpLookupGraceMillis = 1_500L

private data class ResolvedLocationContext(
    val deviceLocation: DeviceLocationSnapshot,
    val currentDevicePlace: ResolvedVisitedPlace?,
    val travelContext: TravelContextSnapshot,
    val startupLocation: StartupLocationBootstrapState,
    val awaitingDevicePromotion: Boolean,
)

private data class LocationDependentSections(
    val weather: WeatherSnapshot,
    val travelAlerts: TravelAlertsSnapshot,
    val localInfo: LocalInfoSnapshot,
    val fuelPrices: FuelPriceSnapshot,
    val emergencyCare: EmergencyCareSnapshot,
)

private fun logWarn(message: String, error: Throwable? = null) {
    runCatching {
        if (error != null) {
            Log.w("NomadDashboard", message, error)
        } else {
            Log.w("NomadDashboard", message)
        }
    }
}

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
