package com.iloapps.nomaddashboard.core.data.repository

import android.util.Log
import com.iloapps.nomaddashboard.core.common.IoDispatcher
import com.iloapps.nomaddashboard.core.common.runCatchingCancellable
import com.iloapps.nomaddashboard.core.data.airquality.AirQualityProvider
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.SectionRefreshState
import com.iloapps.nomaddashboard.core.database.entity.DashboardSectionCacheEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.selects.select
import java.util.concurrent.atomic.AtomicLong
import java.time.Clock
import java.time.Duration
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
import com.iloapps.nomaddashboard.core.model.AirQualitySnapshot
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
import com.iloapps.nomaddashboard.core.model.VisitedPlaceEvent
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
import com.iloapps.nomaddashboard.core.data.monitor.latencyJitter
import com.iloapps.nomaddashboard.core.travel.environment.AirQualityGridPoint
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
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
    private val airQualityProvider: AirQualityProvider,
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
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val resourcePolicyProvider: ResourcePolicyProvider = DefaultResourcePolicyProvider,
    private val clock: Clock = Clock.systemUTC(),
) : NomadDashboardRepository {
    override val settings: Flow<AppSettings> = settingsDataSource.settings
    override val providerCredentials: Flow<ProviderCredentialSettings> = providerCredentialStore.credentials
    override val visitedPlaces: Flow<List<VisitedPlace>> = visitedHistoryStore.visitedPlaces
    override val visitedCountryDays: Flow<List<VisitedCountryDay>> = visitedHistoryStore.visitedCountryDays
    override val visitedPlaceEvents: Flow<List<VisitedPlaceEvent>> = visitedHistoryStore.visitedPlaceEvents

    private val internalSnapshot = MutableStateFlow(DashboardSnapshot())
    override val snapshot: StateFlow<DashboardSnapshot> = internalSnapshot

    private var previousTrafficSample: TrafficSample? = null
    private val refreshCoordinatorMutex = Mutex()
    private var activeRefresh: CompletableDeferred<Unit>? = null
    private var activeRefreshCard: DashboardCardId? = null
    private val contextGeneration = AtomicLong()
    private val latencyProbeMutex = Mutex()
    private var lastLatencyAttemptAt: Instant? = null

    override suspend fun warmStart() = withContext(ioDispatcher) {
        migrateLegacyProviderCredentials()
        val cacheEntries = dashboardSectionCacheDao.all()
        val airQualityActive = settings.first().isAirQualityActive
        val cachedSections = loadCachedDashboardSections(
            entries = cacheEntries,
            json = json,
        )
        internalSnapshot.update { current ->
            current.copy(
                sectionStates = loadSectionStates(cacheEntries, json),
                lastRefresh = cachedSections.lastRefresh ?: current.lastRefresh,
                travelContext = cachedSections.travelContext ?: current.travelContext,
                weather = cachedSections.weather ?: current.weather,
                travelAlerts = cachedSections.travelAlerts ?: current.travelAlerts,
                localInfo = cachedSections.localInfo ?: current.localInfo,
                fuelPrices = cachedSections.fuelPrices ?: current.fuelPrices,
                emergencyCare = cachedSections.emergencyCare ?: current.emergencyCare,
                airQuality = cachedSections.airQuality.takeIf { airQualityActive } ?: current.airQuality,
            )
        }
    }

    override suspend fun refreshLocalSignals() = withContext(ioDispatcher) {
        val (passive, traffic) = telemetryReader.passiveConnectivity(previousTrafficSample)
        previousTrafficSample = traffic
        val power = telemetryReader.power()
        internalSnapshot.update { current -> current.copy(
            localSignalsAt = clock.instant(),
            connectivity = passive.carryingReadingsFrom(current.connectivity, clock.instant()),
            power = power.copy(batteryPercentHistory = current.power.batteryPercentHistory),
        ) }
    }

    override suspend fun refresh() = refreshInternal(null)

    override suspend fun refreshCard(card: DashboardCardId) = refreshInternal(card)

    private suspend fun refreshInternal(card: DashboardCardId?): Unit = withContext(ioDispatcher) {
        migrateLegacyProviderCredentials()
        val (signal, shouldRun, sameRequest) = refreshCoordinatorMutex.withLock {
            activeRefresh?.takeIf { it.isActive }?.let { Triple(it, false, activeRefreshCard == card) }
                ?: CompletableDeferred<Unit>().let {
                    activeRefresh = it
                    activeRefreshCard = card
                    Triple(it, true, true)
                }
        }
        if (!shouldRun) {
            signal.await()
            if (card != null && !sameRequest) refreshInternal(card)
            return@withContext
        }
        try {
            performRefresh(card)
            signal.complete(Unit)
         } catch (cancelled: CancellationException) {
            signal.cancel(cancelled)
            throw cancelled
        } catch (error: Exception) {
            logWarn("Dashboard refresh failed", error)
            signal.complete(Unit)
        } finally {
            withContext(NonCancellable) {
                clearRefreshingStateAfterFailure()
                refreshCoordinatorMutex.withLock {
                    if (activeRefresh === signal) {
                        activeRefresh = null
                        activeRefreshCard = null
                    }
                }
            }
        }
    }

    override suspend fun clearVisitedHistory() {
        visitedHistoryStore.clearHistory()
    }

    private suspend fun performRefresh(requestedCard: DashboardCardId?) = supervisorScope {
        val currentSettings = settings.first()
        val credentials = providerCredentials.first()
        val policy = resourcePolicyProvider.current(currentSettings.resourceMode)
        val previousTravelContext = internalSnapshot.value.travelContext
        val hasPermission = visitedDeviceLocationProvider.hasLocationPermission()
        var generation = contextGeneration.incrementAndGet()
        internalSnapshot.update {
            it.copy(isRefreshing = true, lowImpactMode = policy.lowImpact, resourcePolicyLabel = policy.reason,
                startupLocation = initialStartupLocationState(currentSettings.publicIpGeolocationEnabled, hasPermission))
        }

        // Passive Android signals and local actions never wait for an internet probe.
        val priorTraffic = previousTrafficSample
        val (passiveConnectivity, traffic) = telemetryReader.passiveConnectivity(priorTraffic)
        previousTrafficSample = traffic
        val power = telemetryReader.power()
        internalSnapshot.update {
            it.copy(
                connectivity = passiveConnectivity.carryingReadingsFrom(it.connectivity, clock.instant()),
                power = power,
                lastRefresh = clock.instant(),
                localSignalsAt = clock.instant(),
            )
        }
        publishLocalSummaries(currentSettings)
        val historyJob = launch {
            val enrichedPower = enrichPowerWithHistory(power, traffic.capturedAtMillis)
            internalSnapshot.update { it.copy(power = enrichedPower) }
            // The passive read above already carried forward any still-valid latency.
            val base = internalSnapshot.value.connectivity
            var isNewLatencyReading = false
            val measured = if (passiveConnectivity.isOnline && !policy.lowImpact) {
                lastLatencyAttemptAt = clock.instant()
                val fresh = telemetryReader.connectivity(priorTraffic).first.let {
                    if (it.latencyMs != null && it.latencyMeasuredAt == null) it.copy(latencyMeasuredAt = clock.instant()) else it
                }
                isNewLatencyReading = fresh.latencyMs != null
                fresh.carryingReadingsFrom(base, clock.instant())
            } else base
            val enriched = enrichConnectivityWithHistory(measured, traffic.capturedAtMillis, isNewLatencyReading)
            internalSnapshot.update { it.copy(connectivity = enriched) }
            // Low Data skips the full read but still takes a latency reading, at most once a minute.
            if (policy.lowImpact && passiveConnectivity.isOnline) probeLatencyWhenOnline(LatencyProbeInterval)
            publishLocalSummaries(currentSettings)
        }

        val device = async { resolveStartupDeviceLocation(hasPermission) }
        var freshIpObservation = false
        val ip = async {
            val fetched = if (passiveConnectivity.isOnline && currentSettings.publicIpGeolocationEnabled) {
                withTimeoutOrNull(6_000) { fetchCurrentTravelContext(previousTravelContext) }
            } else null
            freshIpObservation = fetched != null
            val result = fetched ?: if (currentSettings.publicIpGeolocationEnabled) previousTravelContext else TravelContextSnapshot()
            val previousState = internalSnapshot.value.sectionStates[DashboardCardId.TRAVEL_CONTEXT] ?: SectionRefreshState()
            val ipState = if (fetched != null) SectionRefreshState(clock.instant(), fetched.publicIp.orEmpty(),
                listOfNotNull(fetched.city, fetched.country).joinToString(", ")) else previousState.copy(
                    isRefreshing = false, message = if (currentSettings.publicIpGeolocationEnabled) "Public IP location is saved or unavailable" else "Public IP lookup disabled")
            internalSnapshot.update { it.copy(sectionStates = it.sectionStates + (DashboardCardId.TRAVEL_CONTEXT to ipState)) }
            // A late public-IP result may update its own fields, never replace physical location.
            internalSnapshot.update { current ->
                current.copy(travelContext = result.copy(
                    deviceCity = current.travelContext.deviceCity, deviceRegion = current.travelContext.deviceRegion,
                    deviceCountry = current.travelContext.deviceCountry, deviceCountryCode = current.travelContext.deviceCountryCode,
                    deviceLatitude = current.travelContext.deviceLatitude, deviceLongitude = current.travelContext.deviceLongitude,
                ))
            }
            if (fetched != null) dashboardSectionCacheDao.upsertSection(result.toCacheEntity(json, ipState.lastSuccessAt!!),
                ipState.toCacheEntity(DashboardCardId.TRAVEL_CONTEXT, json))
            result
        }
        val marineJob = launch {
            if ((requestedCard == null || requestedCard == DashboardCardId.WEATHER) && passiveConnectivity.isOnline) {
                val old = internalSnapshot.value.marine
                val spot = currentSettings.surfSpot
                if (requestedCard != null || old?.spotName != spot.name || old?.fetchedAt == null ||
                    Duration.between(old.fetchedAt, clock.instant()).toMinutes() >= 30) {
                    val fresh = withTimeoutOrNull(12_000) { refreshMarineSnapshot(spot, clock.instant()) }
                    internalSnapshot.update { it.copy(marine = fresh ?: old) }
                }
            }
        }
        try {
            var location = resolveInitialLocationContext(currentSettings, previousTravelContext, hasPermission, device, ip)
            internalSnapshot.update { it.copy(travelContext = location.travelContext, startupLocation = location.startupLocation) }
            var sections = async {
                refreshLocationDependentSections(currentSettings, credentials, internalSnapshot.value, location,
                    clock.instant(), false, requestedCard, policy, generation, passiveConnectivity.isOnline)
            }
            if (location.awaitingDevicePromotion) {
                val promoted = awaitPromotedDeviceLocation(location, device)
                if (promoted != null) {
                    // Invalidate before cancelling so a late response cannot publish into the new location.
                    generation = contextGeneration.incrementAndGet()
                    sections.cancelAndJoin()
                    location = promoted
                    internalSnapshot.update { it.copy(travelContext = promoted.travelContext, startupLocation = promoted.startupLocation) }
                    val promotedGeneration = generation
                    sections = async {
                        refreshLocationDependentSections(currentSettings, credentials, internalSnapshot.value, promoted,
                            clock.instant(), false, requestedCard, policy, promotedGeneration, passiveConnectivity.isOnline)
                    }
                }
            }
            // Record one final device-preferred observation, not an interim VPN/IP guess.
            if (location.currentDevicePlace != null || passiveConnectivity.isOnline) {
                recordVisitedObservations(currentSettings, location, allowIpObservation = freshIpObservation)
            }
            sections.await()
            historyJob.join()
            marineJob.join()
            publishLocalSummaries(currentSettings)
            internalSnapshot.update { it.copy(startupLocation = it.startupLocation.copy(isChecking = false)) }
        } finally {
            device.cancel()
            ip.cancel()
        }
    }

    private suspend fun publishLocalSummaries(currentSettings: AppSettings) {
        val places = visitedHistoryStore.visitedPlaces.first()
        val days = visitedHistoryStore.visitedCountryDays.first()
        val active = timeTrackingRepository.currentActiveEntry()
        val pending = timeTrackingRepository.pendingEntries.first()
        val report = timeTrackingRepository.report.first()
        internalSnapshot.update { current ->
            val summary = buildDashboardSnapshot(current.lastRefresh ?: clock.instant(), current.isRefreshing,
                current.connectivity, current.power, current.travelContext, current.startupLocation, current.weather,
                current.marine, current.airQuality, current.travelAlerts, current.localInfo, current.fuelPrices, current.emergencyCare,
                currentSettings, active, pending.size, report, places.visitedPlaceSummary(), days.size)
            summary.copy(localSignalsAt = current.localSignalsAt, sectionStates = current.sectionStates, lowImpactMode = current.lowImpactMode,
                resourcePolicyLabel = current.resourcePolicyLabel)
        }
    }

    private suspend fun clearRefreshingStateAfterFailure() {
        internalSnapshot.update {
            it.copy(
                isRefreshing = false,
                sectionStates = it.sectionStates.mapValues { (_, state) -> state.copy(isRefreshing = false) },
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
        val earlyDeviceLocation = if (hasLocationPermission) {
            withTimeoutOrNull(DevicePreferredGraceMillis) { deviceLocationDeferred.await() }
        } else DeviceLocationSnapshot(status = DeviceLocationResolutionStatus.PERMISSION_MISSING)

        if (earlyDeviceLocation?.hasCoordinates == true) {
            val ipTravelContext = if (currentSettings.publicIpGeolocationEnabled) {
                runCatchingCancellable {
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
            runCatchingCancellable {
                if (ipTravelContextDeferred.isActive.not()) ipTravelContextDeferred.await() else null
            }.getOrNull() ?: run {
                val winner = select<Pair<DeviceLocationSnapshot?, TravelContextSnapshot?>> {
                    if (hasLocationPermission && earlyDeviceLocation == null) {
                        deviceLocationDeferred.onAwait { it to null }
                    }
                    ipTravelContextDeferred.onAwait { null to it }
                }
                val readyDevice = winner.first
                if (readyDevice?.hasCoordinates == true) {
                    return ResolvedLocationContext(readyDevice, readyDevice.toResolvedVisitedPlaceOrNull(),
                        previousTravelContext.withDeviceLocation(readyDevice),
                        StartupLocationBootstrapState(StartupLocationBootstrapPhase.USING_DEVICE_LOCATION), false)
                }
                winner.second ?: ipTravelContextDeferred.await()
            }
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

        val promotedDeviceLocation = runCatchingCancellable { deviceLocationDeferred.await() }
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
        allowIpObservation: Boolean = true,
    ) {
        if (currentSettings.visitedPlacesEnabled.not()) {
            return
        }

        val observedAt = clock.instant()
        if (currentSettings.useCurrentLocationForVisitedPlaces) {
            locationContext.currentDevicePlace?.let { place ->
                runCatchingCancellable {
                    visitedHistoryStore.recordObservation(place.toObservation(observedAt))
                }
                return
            }
        }

        if (!allowIpObservation) return
        buildIpObservation(locationContext.travelContext, observedAt)?.let { observation ->
            runCatchingCancellable { visitedHistoryStore.recordObservation(observation) }
        }
    }

    private fun shouldDeferVisitedObservation(
        currentSettings: AppSettings,
        locationContext: ResolvedLocationContext,
    ): Boolean =
        currentSettings.visitedPlacesEnabled &&
            currentSettings.useCurrentLocationForVisitedPlaces &&
            locationContext.awaitingDevicePromotion

    private suspend fun refreshLocationDependentSections(
        currentSettings: AppSettings,
        providerCredentials: ProviderCredentialSettings,
        previousSnapshot: DashboardSnapshot,
        locationContext: ResolvedLocationContext,
        refreshedAt: Instant,
        keepRefreshing: Boolean,
        requestedCard: DashboardCardId?,
        policy: ResourcePolicy,
        generation: Long,
        online: Boolean,
    ): LocationDependentSections = supervisorScope {
        val permits = Semaphore(if (policy.lowImpact) 1 else 3)
        val context = locationContext.travelContext
        val country = locationContext.currentDevicePlace?.countryCode ?: context.deviceCountryCode ?: context.countryCode.orEmpty()
        val latitude = locationContext.deviceLocation.latitude ?: context.latitude
        val longitude = locationContext.deviceLocation.longitude ?: context.longitude
        val region = locationContext.currentDevicePlace?.region ?: context.deviceRegion ?: context.region.orEmpty()
        val pointKey = "$country:${latitude?.let { kotlin.math.round(it * 100) / 100 }}:${longitude?.let { kotlin.math.round(it * 100) / 100 }}"
        val label = travelContextPrimaryLocation(context) ?: "Saved location"
        if (context.containsIpContext() || context.deviceLatitude != null) {
            dashboardSectionCacheDao.upsert(context.toCacheEntity(json, clock.instant()))
        }
        val weatherDeferred = async {
            refreshSection(
                card = DashboardCardId.WEATHER, requestedCard = requestedCard, generation = generation,
                locationKey = pointKey + ":" + currentSettings.useCurrentLocationForWeather, locationLabel = label, ttlMinutes = 10,
                permits = permits, online = online, deferForLowData = false,
                enabled = true, previous = previousSnapshot.weather, fetchedAt = { it.fetchedAt },
                cache = { it.toCacheEntity(json) },
                publish = { result -> internalSnapshot.update { it.copy(weather = result.copy(isRefreshing = false)) } },
            ) { refreshWeatherSnapshot(currentSettings, previousSnapshot.weather, locationContext.deviceLocation, context, refreshedAt, false) }
        }
        val airQualityDeferred = async {
            refreshAirQuality(
                currentSettings = currentSettings, previous = previousSnapshot.airQuality,
                deviceLocation = locationContext.deviceLocation, requestedCard = requestedCard,
                permits = permits, online = online, generation = generation,
            )
        }
        val travelAlertsDeferred = async {
            refreshSection(
                card = DashboardCardId.TRAVEL_ALERTS, requestedCard = requestedCard, generation = generation,
                locationKey = country, locationLabel = label, ttlMinutes = 30,
                permits = permits, online = online, deferForLowData = false,
                enabled = true, previous = previousSnapshot.travelAlerts, fetchedAt = { it.fetchedAt },
                cache = { it.toCacheEntity(json) },
                publish = { result -> internalSnapshot.update { it.copy(travelAlerts = result.copy(isRefreshing = false)) } },
            ) { refreshTravelAlertsSnapshot(previousSnapshot.travelAlerts, locationContext.currentDevicePlace, context, providerCredentials, false, !policy.lowImpact || requestedCard == DashboardCardId.TRAVEL_ALERTS) }
        }
        val localInfoDeferred = async {
            refreshSection(
                card = DashboardCardId.LOCAL_INFO, requestedCard = requestedCard, generation = generation,
                locationKey = "$country:$region", locationLabel = label, ttlMinutes = 360,
                permits = permits, online = online, deferForLowData = false,
                enabled = currentSettings.localInfoEnabled, previous = previousSnapshot.localInfo, fetchedAt = { it.fetchedAt },
                cache = { it.toCacheEntity(json) },
                publish = { result -> internalSnapshot.update { it.copy(localInfo = result.copy(isRefreshing = false)) } },
            ) { refreshLocalInfoSnapshot(currentSettings, providerCredentials, locationContext.currentDevicePlace, context, previousSnapshot.localInfo, false) }
        }
        val fuelDeferred = async {
            refreshSection(
                card = DashboardCardId.FUEL_PRICES, requestedCard = requestedCard, generation = generation,
                locationKey = pointKey, locationLabel = label, ttlMinutes = 15,
                permits = permits, online = online, deferForLowData = policy.lowImpact,
                enabled = currentSettings.fuelPricesEnabled, previous = previousSnapshot.fuelPrices, fetchedAt = { it.fetchedAt },
                cache = { it.toCacheEntity(json) },
                publish = { result -> internalSnapshot.update { it.copy(fuelPrices = result.copy(isRefreshing = false)) } },
            ) { refreshFuelPriceSnapshot(currentSettings, providerCredentials, locationContext.deviceLocation, locationContext.currentDevicePlace, context, previousSnapshot.fuelPrices, false, requestedCard == DashboardCardId.FUEL_PRICES) }
        }
        val emergencyDeferred = async {
            refreshSection(
                card = DashboardCardId.EMERGENCY_CARE, requestedCard = requestedCard, generation = generation,
                locationKey = pointKey, locationLabel = label, ttlMinutes = 30,
                permits = permits, online = online, deferForLowData = false,
                enabled = currentSettings.emergencyCareEnabled, previous = previousSnapshot.emergencyCare, fetchedAt = { it.fetchedAt },
                cache = { it.toCacheEntity(json) },
                publish = { result -> internalSnapshot.update { it.copy(emergencyCare = result.copy(isRefreshing = false)) } },
            ) { refreshEmergencyCareSnapshot(currentSettings, locationContext.deviceLocation, context, previousSnapshot.emergencyCare, false) }
        }
        LocationDependentSections(weatherDeferred.await(), airQualityDeferred.await(), travelAlertsDeferred.await(),
            localInfoDeferred.await(), fuelDeferred.await(), emergencyDeferred.await())
    }

    /**
     * Air quality follows the weather location consent: it only runs while device-location weather is
     * on and a device fix exists (never from the public IP location), is refreshed with weather, and
     * keeps the last value for the same ~11 km grid point when a refresh fails.
     */
    private suspend fun refreshAirQuality(
        currentSettings: AppSettings,
        previous: AirQualitySnapshot?,
        deviceLocation: DeviceLocationSnapshot,
        requestedCard: DashboardCardId?,
        permits: Semaphore,
        online: Boolean,
        generation: Long,
    ): AirQualitySnapshot? {
        if (!currentSettings.isAirQualityActive) {
            publishAirQuality(null, generation)
            return null
        }
        if (requestedCard != null && requestedCard != DashboardCardId.WEATHER) return previous
        val latitude = deviceLocation.latitude
        val longitude = deviceLocation.longitude
        val grid = if (latitude != null && longitude != null) AirQualityGridPoint.privacyRounded(latitude, longitude) else null
        if (grid == null || latitude == null || longitude == null) return previous
        // A snapshot for another grid point must never be shown for this location.
        val sameLocation = previous?.takeIf { it.latitude == grid.latitude && it.longitude == grid.longitude }
        if (sameLocation == null && previous != null) publishAirQuality(null, generation)
        val isFresh = sameLocation != null && requestedCard == null &&
            Duration.between(sameLocation.fetchedAt, clock.instant()).let { !it.isNegative && it < AirQualityRefreshInterval }
        if (isFresh || !online) return sameLocation

        val fresh = runCatchingCancellable {
            withTimeoutOrNull(15_000) {
                permits.withPermit { retryTransient { airQualityProvider.airQuality(latitude, longitude) } }
            }
        }.onFailure { logWarn("Air quality refresh failed", it) }.getOrNull()
        val result = fresh ?: sameLocation
        publishAirQuality(result, generation)
        if (fresh != null) {
            runCatchingCancellable { fresh.toCacheEntity(json)?.let { dashboardSectionCacheDao.upsert(it) } }
                .onFailure { logWarn("Air quality cache write failed", it) }
        }
        return result
    }

    private fun publishAirQuality(airQuality: AirQualitySnapshot?, generation: Long) {
        if (contextGeneration.get() != generation) return
        internalSnapshot.update { it.copy(airQuality = airQuality) }
    }

    private suspend fun <T> refreshSection(
        card: DashboardCardId,
        requestedCard: DashboardCardId?,
        generation: Long,
        locationKey: String,
        locationLabel: String,
        ttlMinutes: Long,
        permits: Semaphore,
        online: Boolean,
        deferForLowData: Boolean,
        enabled: Boolean,
        previous: T,
        fetchedAt: (T) -> Instant?,
        cache: (T) -> DashboardSectionCacheEntity?,
        publish: (T) -> Unit,
        block: suspend () -> T,
    ): T {
        if (requestedCard != null && requestedCard != card) return previous
        val previousAt = fetchedAt(previous)
        val state = internalSnapshot.value.sectionStates[card] ?: SectionRefreshState(lastSuccessAt = previousAt)
        val force = requestedCard == card
        val stillFresh = state.locationKey == locationKey && state.lastSuccessAt?.let {
            Duration.between(it, clock.instant()).let { age -> !age.isNegative && age.toMinutes() < ttlMinutes }
        } == true
        if (!enabled || (!force && stillFresh)) return previous
        if (!online || (deferForLowData && !force)) {
            internalSnapshot.update { it.copy(sectionStates = it.sectionStates + (card to state.copy(
                isRefreshing = false, message = if (!online) "Offline · saved data" else "Low Data · refresh to download",
            ))) }
            return previous
        }
        internalSnapshot.update { it.copy(sectionStates = it.sectionStates + (card to state.copy(isRefreshing = true, message = null))) }
        try {
            val outcome = runCatchingCancellable {
                withTimeoutOrNull(if (card == DashboardCardId.TRAVEL_ALERTS || card == DashboardCardId.EMERGENCY_CARE) 25_000 else 15_000) {
                    permits.withPermit { block() }
                }
            }
            val result = outcome.getOrNull()
            val actual = result ?: previous
            if (contextGeneration.get() != generation) return previous
            val resultAt = fetchedAt(actual)
            val updated = resultAt != null && resultAt != previousAt
            val nextState = state.copy(
                lastSuccessAt = resultAt ?: state.lastSuccessAt,
                locationKey = if (updated) locationKey else state.locationKey,
                locationLabel = if (updated) locationLabel else state.locationLabel,
                isRefreshing = false,
                message = when {
                    outcome.isFailure -> "Update failed · retry when ready"
                    result == null -> "Timed out · retry when ready"
                    resultAt == null -> "No current data · retry when ready"
                    !updated && !stillFresh -> "Showing saved data"
                    else -> null
                },
            )
            publish(actual)
            internalSnapshot.update { it.copy(sectionStates = it.sectionStates + (card to nextState)) }
            // Publish first. A slow cache write must not hold a ready card back.
            runCatchingCancellable {
                cache(actual)?.let { dashboardSectionCacheDao.upsertSection(it, nextState.toCacheEntity(card, json)) }
            }.onFailure { logWarn("Section cache write failed", it) }
            return actual
        } finally {
            if (contextGeneration.get() == generation) {
                internalSnapshot.update { current -> current.copy(sectionStates = current.sectionStates.mapValues { (id, value) ->
                    if (id == card) value.copy(isRefreshing = false) else value
                }) }
            }
        }
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

        return runCatchingCancellable {
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
                uvIndex = response.current?.uvIndex?.toWholeUvIndex(),
                hourlyForecast = response.hourly.toHourlyForecast(
                    timezone = response.timezone,
                    utcOffsetSeconds = response.utcOffsetSeconds,
                    referenceTime = refreshedAt,
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
        allowBrowserFallback: Boolean = true,
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
            allowBrowserFallback = allowBrowserFallback,
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
        runCatchingCancellable {
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
        forceRefresh: Boolean = false,
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

        return runCatchingCancellable {
            retryTransient {
                val credentials = FuelProviderCredentials(tankerkoenigApiKey = providerCredentials.tankerkoenigApiKey)
                if (forceRefresh) fuelPriceProvider.refreshPrices(request, credentials)
                else fuelPriceProvider.prices(request, credentials)
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
        runCatchingCancellable {
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
        airQuality: AirQualitySnapshot?,
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
                headline = connectivity.internetState.label,
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
            airQuality = airQuality,
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
        if (current.isAirQualityActive && !updated.isAirQualityActive) {
            internalSnapshot.update { it.copy(airQuality = null) }
            runCatchingCancellable { dashboardSectionCacheDao.delete(DashboardSectionId.AIR_QUALITY) }
                .onFailure { logWarn("Air quality cache removal failed", it) }
        }
        if (current.projectTimeTrackingEnabled && updated.projectTimeTrackingEnabled.not()) {
            timeTrackingRepository.stopTracking()
        }
        if (!current.isAirQualityActive && updated.isAirQualityActive) {
            // Air quality follows the weather refresh, so turning it on (or turning device-location
            // weather on while it is enabled) fetches it right away instead of at the next refresh.
            refreshCard(DashboardCardId.WEATHER)
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
            runCatchingCancellable { visitedDeviceLocationProvider.currentLocation() }
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

    /**
     * Records the throughput and any newly measured latency, then reloads the histories. Jitter is
     * derived from the recent latency readings; a carried-over reading is not recorded again.
     */
    private suspend fun enrichConnectivityWithHistory(
        connectivity: com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot,
        recordedAtMillis: Long,
        isNewLatencyReading: Boolean,
    ): com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot {
        val latencyMillis = connectivity.latencyMeasuredAt?.toEpochMilli() ?: recordedAtMillis
        val points = listOfNotNull(
            connectivity.downloadMbps?.let { MetricPointEntity(kind = ConnectivityDownloadMetricKind, timestampEpochMillis = recordedAtMillis, value = it) },
            connectivity.uploadMbps?.let { MetricPointEntity(kind = ConnectivityUploadMetricKind, timestampEpochMillis = recordedAtMillis, value = it) },
            connectivity.latencyMs?.takeIf { isNewLatencyReading }
                ?.let { MetricPointEntity(kind = ConnectivityLatencyMetricKind, timestampEpochMillis = latencyMillis, value = it) },
        )
        metricPointDao.recordBatch(points, ConnectivityHistoryRetentionCount)

        val latencyHistory = recentMetricHistory(ConnectivityLatencyMetricKind)
        return connectivity.copy(
            latencyHistoryMs = latencyHistory,
            downloadHistoryMbps = recentMetricHistory(ConnectivityDownloadMetricKind),
            uploadHistoryMbps = recentMetricHistory(ConnectivityUploadMetricKind),
            jitterMs = if (connectivity.latencyMs == null) {
                null
            } else {
                connectivity.latencyMeasuredAt?.let { latencyJitter(latencyHistory, it) } ?: connectivity.jitterMs
            },
        )
    }

    override suspend fun probeLatencyIfStale(maximumAge: Duration) = withContext(ioDispatcher) {
        // Never probe offline (or behind a captive portal): the socket probe would only time out.
        if (!telemetryReader.passiveConnectivity(null).first.isOnline) return@withContext
        probeLatencyWhenOnline(maximumAge)
    }

    /**
     * Takes one latency reading unless a younger one exists or a probe was attempted within
     * [maximumAge], and updates only latency, jitter and the latency history.
     */
    private suspend fun probeLatencyWhenOnline(maximumAge: Duration) {
        latencyProbeMutex.withLock {
            val now = clock.instant()
            val current = internalSnapshot.value.connectivity
            val readingIsFresh = current.latencyMs != null &&
                current.latencyMeasuredAt?.let { Duration.between(it, now) < maximumAge } == true
            val attemptIsRecent = lastLatencyAttemptAt?.let { Duration.between(it, now) < maximumAge } == true
            if (readingIsFresh || attemptIsRecent) return
            lastLatencyAttemptAt = now

            val probe = telemetryReader.latencyProbe() ?: return
            val latency = probe.latencyMs
            val measuredAt = clock.instant()
            recordMetric(ConnectivityLatencyMetricKind, measuredAt.toEpochMilli(), latency)
            val history = recentMetricHistory(ConnectivityLatencyMetricKind)
            internalSnapshot.update {
                it.copy(
                    connectivity = it.connectivity.copy(
                        latencyMs = latency,
                        jitterMs = latencyJitter(history, measuredAt),
                        latencyMeasuredAt = measuredAt,
                        latencyProbeHost = probe.host,
                        latencyHistoryMs = history,
                    ),
                )
            }
        }
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
        allowBrowserFallback: Boolean = true,
    ): TravelAlertsSnapshot = supervisorScope {
        val attemptedAt = clock.instant()
        travelAlertLogDebug(
            TravelAlertsLogTag,
            "refreshTravelAlerts primary=${primaryCountryCode ?: "none"} country=${primaryCountryName ?: "none"} coverage=${coverageCountryCodes.joinToString(",")}",
        )
        val advisoryDeferred = async { refreshAlertState(
            kind = TravelAlertKind.ADVISORY,
            previous = previous.state(TravelAlertKind.ADVISORY),
            primaryCountryCode = primaryCountryCode,
            attemptedAt = attemptedAt,
        ) {
            smartravellerAdvisoryProvider.advisory(
                allowBrowserFallback = allowBrowserFallback,
                countryCodes = coverageCountryCodes,
                primaryCountryCode = primaryCountryCode ?: error("primary country required"),
            )
        }
        }
        val securityDeferred = async { refreshAlertState(
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

        }
        val advisoryState = advisoryDeferred.await()
        val securityState = securityDeferred.await()
        TravelAlertsSnapshot(
            primaryCountryCode = primaryCountryCode,
            primaryCountryName = primaryCountryName,
            coverageCountryCodes = coverageCountryCodes,
            states = listOf(advisoryState, securityState),
            fetchedAt = listOfNotNull(advisoryState.lastSuccessAt, securityState.lastSuccessAt).maxOrNull() ?: previous.fetchedAt,
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
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
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

        return runCatchingCancellable {
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
        runCatchingCancellable {
            retryTransient {
                freeIpApiService.lookupMe()
            }.toTravelContextSnapshot(previous = previous)
        }.getOrNull()?.takeIf { it.containsIpContext() }?.let { return it }

        val fallbackIpAddress = runCatchingCancellable {
            retryTransient { ipifyService.lookupIp() }.ip.normalizedOr(previous.publicIp)
        }
            .getOrNull()
            ?.normalizedOr(previous.publicIp)
            ?: return null

        return runCatchingCancellable {
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
                date = runCatchingCancellable { LocalDate.parse(rawDate) }.getOrDefault(LocalDate.now().plusDays(index.toLong())),
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
    ): List<WeatherHourlyForecastSlot> {
        if (this == null) {
            return emptyList()
        }

        val times = parseOpenMeteoTimes(
            rawValues = times,
            timezone = timezone,
            utcOffsetSeconds = utcOffsetSeconds,
        )

        // Every hour from the current one through the next 24 h: enough for the rest of today's
        // daylight and the 6 h / 12 h look-aheads of the heads-up engine.
        val windowStart = referenceTime.truncatedTo(ChronoUnit.HOURS)
        val windowEnd = referenceTime.plus(HourlyForecastWindow)
        return times.mapIndexedNotNull { index, time ->
            if (time.isBefore(windowStart) || time.isAfter(windowEnd)) return@mapIndexedNotNull null
            WeatherHourlyForecastSlot(
                at = time,
                summary = weatherCodeSummary(weatherCodes.getOrNull(index)),
                temperatureCelsius = temperatures.getOrNull(index),
                rainChancePercent = rainChance.getOrNull(index),
                windSpeedKph = windSpeedsKph.getOrNull(index),
                windDirectionDegrees = windDirectionDegrees.getOrNull(index),
                weatherCode = weatherCodes.getOrNull(index),
                uvIndex = uvIndexes.getOrNull(index)?.toWholeUvIndex(),
            )
        }
    }

    private fun Double.toWholeUvIndex(): Int? = takeIf { it.isFinite() && it >= 0 }?.roundToInt()

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
            runCatchingCancellable {
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
private val LatencyProbeInterval: Duration = Duration.ofSeconds(60)
private val AirQualityRefreshInterval: Duration = Duration.ofMinutes(30)
private val HourlyForecastWindow: Duration = Duration.ofHours(24)

private data class ResolvedLocationContext(
    val deviceLocation: DeviceLocationSnapshot,
    val currentDevicePlace: ResolvedVisitedPlace?,
    val travelContext: TravelContextSnapshot,
    val startupLocation: StartupLocationBootstrapState,
    val awaitingDevicePromotion: Boolean,
)

private data class LocationDependentSections(
    val weather: WeatherSnapshot,
    val airQuality: AirQualitySnapshot?,
    val travelAlerts: TravelAlertsSnapshot,
    val localInfo: LocalInfoSnapshot,
    val fuelPrices: FuelPriceSnapshot,
    val emergencyCare: EmergencyCareSnapshot,
)

private fun logWarn(message: String, error: Throwable? = null) {
    runCatchingCancellable {
        if (error != null) {
            Log.w("NomadDashboard", message, error)
        } else {
            Log.w("NomadDashboard", message)
        }
    }
}

private fun travelAlertLogDebug(tag: String, message: String) {
    runCatchingCancellable { Log.d(tag, message) }
}

private fun travelAlertLogInfo(tag: String, message: String) {
    runCatchingCancellable { Log.i(tag, message) }
}

private fun travelAlertLogWarn(tag: String, message: String, error: Throwable? = null) {
    runCatchingCancellable {
        if (error != null) {
            Log.w(tag, message, error)
        } else {
            Log.w(tag, message)
        }
    }
}
