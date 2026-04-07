package com.iloapps.nomaddashboard.core.data.repository

import com.iloapps.nomaddashboard.core.data.fuel.FuelPriceProvider
import com.iloapps.nomaddashboard.core.data.fuel.FuelSearchRequest
import com.iloapps.nomaddashboard.core.data.timetracking.CreateProjectResult
import com.iloapps.nomaddashboard.core.data.timetracking.StartTrackingResult
import com.iloapps.nomaddashboard.core.data.timetracking.StopTrackingResult
import com.iloapps.nomaddashboard.core.data.timetracking.TimeTrackingRepository
import androidx.datastore.core.DataStore
import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.data.location.ResolvedVisitedPlace
import com.iloapps.nomaddashboard.core.data.location.VisitedDeviceLocationProvider
import com.iloapps.nomaddashboard.core.data.monitor.TelemetryReader
import com.iloapps.nomaddashboard.core.data.monitor.TrafficSample
import com.iloapps.nomaddashboard.core.data.visited.VisitedHistoryStore
import com.iloapps.nomaddashboard.core.data.visited.VisitedObservation
import com.iloapps.nomaddashboard.core.datastore.AppSettingsProto
import com.iloapps.nomaddashboard.core.datastore.NomadSettingsDataSource
import com.iloapps.nomaddashboard.core.datastore.toProto
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.model.FuelStationPrice
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.model.TimeTrackingEntry
import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
import com.iloapps.nomaddashboard.core.model.TimeTrackingRecord
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import com.iloapps.nomaddashboard.core.network.api.FreeIpApiService
import com.iloapps.nomaddashboard.core.network.api.OpenMeteoService
import com.iloapps.nomaddashboard.core.network.model.FreeIpApiResponse
import com.iloapps.nomaddashboard.core.network.model.OpenMeteoResponse
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultNomadDashboardRepositoryTest {
    @Test
    fun `refresh records ip visit and updates snapshot summary`() = runTest {
        val settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(AppSettings().toProto()))
        val visitedStore = FakeVisitedHistoryStore()
        val repository = repository(
            settingsDataSource = settingsDataSource,
            fuelPriceProvider = FakeFuelPriceProvider(),
            visitedHistoryStore = visitedStore,
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null),
            applicationScope = backgroundScope,
        )

        repository.refresh()
        advanceUntilIdle()

        val observations = visitedStore.observations
        val snapshot = repository.snapshot.first { it.lastRefresh != null }

        assertThat(observations).hasSize(1)
        assertThat(observations.first().source).isEqualTo(VisitedPlaceSource.PUBLIC_IP_GEOLOCATION)
        assertThat(snapshot.visited.citiesVisited).isEqualTo(1)
        assertThat(snapshot.visited.countriesVisited).isEqualTo(1)
        assertThat(snapshot.visited.trackedDays).isEqualTo(1)
        assertThat(snapshot.visited.sourceSummary).isEqualTo("IP")
    }

    @Test
    fun `refresh records device visit when toggle is enabled`() = runTest {
        val settings = AppSettings(
            visitedPlacesEnabled = true,
            publicIpGeolocationEnabled = true,
            useCurrentLocationForVisitedPlaces = true,
        )
        val settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(settings.toProto()))
        val visitedStore = FakeVisitedHistoryStore()
        val repository = repository(
            settingsDataSource = settingsDataSource,
            fuelPriceProvider = FakeFuelPriceProvider(),
            visitedHistoryStore = visitedStore,
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(
                ResolvedVisitedPlace(
                    city = "Malaga",
                    region = "Andalusia",
                    country = "Spain",
                    countryCode = "ES",
                    latitude = 36.72,
                    longitude = -4.42,
                ),
            ),
            applicationScope = backgroundScope,
        )

        repository.refresh()
        advanceUntilIdle()

        assertThat(visitedStore.observations.map(VisitedObservation::source)).containsExactly(
            VisitedPlaceSource.PUBLIC_IP_GEOLOCATION,
            VisitedPlaceSource.DEVICE_LOCATION,
        ).inOrder()
        assertThat(repository.snapshot.first { it.lastRefresh != null }.visited.sourceSummary).isEqualTo("IP + Device")
    }

    @Test
    fun `refresh ignores device provider failure and skips writes when visited disabled`() = runTest {
        val settings = AppSettings(
            visitedPlacesEnabled = false,
            publicIpGeolocationEnabled = true,
            useCurrentLocationForVisitedPlaces = true,
        )
        val settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(settings.toProto()))
        val visitedStore = FakeVisitedHistoryStore()
        val repository = repository(
            settingsDataSource = settingsDataSource,
            fuelPriceProvider = FakeFuelPriceProvider(),
            visitedHistoryStore = visitedStore,
            visitedDeviceLocationProvider = object : VisitedDeviceLocationProvider {
                override suspend fun currentPlace(): ResolvedVisitedPlace? = error("should not be called")
            },
            applicationScope = backgroundScope,
        )

        repository.refresh()
        advanceUntilIdle()

        assertThat(visitedStore.observations).isEmpty()
        assertThat(repository.snapshot.first { it.lastRefresh != null }.visited.sourceSummary).isEqualTo("Disabled")
    }

    @Test
    fun `refresh uses device location first for fuel lookup`() = runTest {
        val settings = AppSettings(
            fuelPricesEnabled = true,
            publicIpGeolocationEnabled = true,
        )
        val fuelPriceProvider = FakeFuelPriceProvider(
            snapshot = FuelPriceSnapshot(
                status = FuelPriceStatus.READY,
                sourceName = "Spanish Ministry Fuel Prices",
                countryCode = "ES",
                countryName = "Spain",
                diesel = FuelStationPrice(
                    fuelType = FuelType.DIESEL,
                    stationName = "Station Diesel",
                    pricePerLiter = 1.5,
                    distanceKilometers = 2.0,
                    latitude = 36.72,
                    longitude = -4.42,
                ),
                detail = "Cheapest prices within 50 km.",
            ),
        )
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(settings.toProto())),
            fuelPriceProvider = fuelPriceProvider,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(
                ResolvedVisitedPlace(
                    city = "Malaga",
                    region = "Andalusia",
                    country = "Spain",
                    countryCode = "ES",
                    latitude = 36.72,
                    longitude = -4.42,
                ),
            ),
            applicationScope = backgroundScope,
        )

        repository.refresh()
        advanceUntilIdle()

        val request = fuelPriceProvider.requests.single()
        val snapshot = repository.snapshot.first { it.lastRefresh != null }

        assertThat(request.countryCode).isEqualTo("ES")
        assertThat(request.latitude).isWithin(0.001).of(36.72)
        assertThat(snapshot.fuelPrices.status).isEqualTo(FuelPriceStatus.READY)
        assertThat(snapshot.fuelPrices.sourceName).isEqualTo("Spanish Ministry Fuel Prices")
    }

    @Test
    fun `refresh falls back to ip travel context for fuel lookup`() = runTest {
        val settings = AppSettings(
            fuelPricesEnabled = true,
            publicIpGeolocationEnabled = true,
        )
        val fuelPriceProvider = FakeFuelPriceProvider(
            snapshot = FuelPriceSnapshot(
                status = FuelPriceStatus.READY,
                sourceName = "Nomad Fuel Prices",
                countryCode = "FI",
                countryName = "Finland",
                detail = "Cheapest prices within 50 km.",
            ),
        )
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(settings.toProto())),
            fuelPriceProvider = fuelPriceProvider,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null),
            applicationScope = backgroundScope,
        )

        repository.refresh()
        advanceUntilIdle()

        val request = fuelPriceProvider.requests.single()
        assertThat(request.countryCode).isEqualTo("FI")
        assertThat(request.latitude).isWithin(0.001).of(60.1699)
    }

    @Test
    fun `refresh marks fuel unavailable when no location context exists`() = runTest {
        val settings = AppSettings(
            fuelPricesEnabled = true,
            publicIpGeolocationEnabled = false,
        )
        val fuelPriceProvider = FakeFuelPriceProvider()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(settings.toProto())),
            fuelPriceProvider = fuelPriceProvider,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null),
            applicationScope = backgroundScope,
        )

        repository.refresh()
        advanceUntilIdle()

        assertThat(fuelPriceProvider.requests).isEmpty()
        assertThat(repository.snapshot.first { it.lastRefresh != null }.fuelPrices.status)
            .isEqualTo(FuelPriceStatus.UNAVAILABLE)
    }

    @Test
    fun `refresh exposes active time tracking summary`() = runTest {
        val activeRecord = TimeTrackingRecord(
            entry = TimeTrackingEntry(
                id = UUID.fromString("00000000-0000-0000-0000-000000000301"),
                projectId = UUID.fromString("00000000-0000-0000-0000-000000000302"),
                startAt = Instant.parse("2026-04-07T09:00:00Z"),
            ),
            project = TimeTrackingProject(
                id = UUID.fromString("00000000-0000-0000-0000-000000000302"),
                name = "Client Work",
            ),
        )
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(
                FakeAppSettingsDataStore(
                    AppSettings(projectTimeTrackingEnabled = true).toProto(),
                ),
            ),
            fuelPriceProvider = FakeFuelPriceProvider(),
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null),
            applicationScope = backgroundScope,
            timeTrackingRepository = FakeTimeTrackingRepository(active = activeRecord),
        )

        repository.refresh()
        advanceUntilIdle()

        val snapshot = repository.snapshot.first { it.lastRefresh != null }
        assertThat(snapshot.timeTracking.headline).isEqualTo("Tracking")
        assertThat(snapshot.timeTracking.detail).contains("Client Work")
    }

    private fun repository(
        settingsDataSource: NomadSettingsDataSource,
        fuelPriceProvider: FuelPriceProvider,
        visitedHistoryStore: FakeVisitedHistoryStore,
        visitedDeviceLocationProvider: VisitedDeviceLocationProvider,
        applicationScope: CoroutineScope,
        timeTrackingRepository: TimeTrackingRepository = FakeTimeTrackingRepository(),
    ): DefaultNomadDashboardRepository =
        DefaultNomadDashboardRepository(
            settingsDataSource = settingsDataSource,
            telemetryReader = FakeTelemetryReader(),
            freeIpApiService = FakeFreeIpApiService(),
            openMeteoService = FakeOpenMeteoService(),
            fuelPriceProvider = fuelPriceProvider,
            timeTrackingRepository = timeTrackingRepository,
            visitedHistoryStore = visitedHistoryStore,
            visitedDeviceLocationProvider = visitedDeviceLocationProvider,
            applicationScope = applicationScope,
        )
}

private class FakeAppSettingsDataStore(
    initialValue: AppSettingsProto,
) : DataStore<AppSettingsProto> {
    private val state = MutableStateFlow(initialValue)

    override val data: Flow<AppSettingsProto> = state

    override suspend fun updateData(transform: suspend (t: AppSettingsProto) -> AppSettingsProto): AppSettingsProto {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

private class FakeTelemetryReader : TelemetryReader {
    override suspend fun connectivity(previousTrafficSample: TrafficSample?): Pair<ConnectivitySnapshot, TrafficSample> =
        ConnectivitySnapshot(
            internetState = "Online",
            isOnline = true,
            wifiName = "Test Wi-Fi",
        ) to TrafficSample(rxBytes = 0, txBytes = 0, capturedAtMillis = 0)

    override suspend fun power(): PowerSnapshot =
        PowerSnapshot(
            batteryPercent = 80,
            charging = false,
            batteryHealthSummary = "Healthy",
        )
}

private class FakeFreeIpApiService : FreeIpApiService {
    override suspend fun lookupMe(): FreeIpApiResponse =
        FreeIpApiResponse(
            ipAddress = "1.2.3.4",
            cityName = "Helsinki",
            regionName = "Uusimaa",
            countryName = "Finland",
            countryCode = "FI",
            latitude = 60.1699,
            longitude = 24.9384,
            timeZone = "Europe/Helsinki",
        )
}

private class FakeOpenMeteoService : OpenMeteoService {
    override suspend fun forecast(
        latitude: Double,
        longitude: Double,
        current: String,
        daily: String,
        timezone: String,
    ): OpenMeteoResponse = OpenMeteoResponse()
}

private class FakeVisitedDeviceLocationProvider(
    private val value: ResolvedVisitedPlace?,
) : VisitedDeviceLocationProvider {
    override suspend fun currentPlace(): ResolvedVisitedPlace? = value
}

private class FakeFuelPriceProvider(
    private val snapshot: FuelPriceSnapshot = FuelPriceSnapshot(
        status = FuelPriceStatus.NO_STATIONS_FOUND,
        sourceName = "Nomad Fuel Prices",
        detail = "No priced stations found within 50 km.",
    ),
) : FuelPriceProvider {
    val requests = mutableListOf<FuelSearchRequest>()

    override suspend fun prices(request: FuelSearchRequest): FuelPriceSnapshot {
        requests += request
        return snapshot
    }
}

private class FakeTimeTrackingRepository(
    active: TimeTrackingRecord? = null,
) : TimeTrackingRepository {
    override val projects: Flow<List<TimeTrackingProject>> = MutableStateFlow(emptyList())
    override val recentEntries: Flow<List<TimeTrackingRecord>> = MutableStateFlow(emptyList())
    override val activeEntry: Flow<TimeTrackingRecord?> = MutableStateFlow(active)

    override suspend fun currentActiveEntry(): TimeTrackingRecord? = activeEntry.first()

    override suspend fun createProject(name: String): CreateProjectResult =
        CreateProjectResult.Created(
            TimeTrackingProject(id = UUID.randomUUID(), name = name.trim()),
        )

    override suspend fun startTracking(projectId: UUID): StartTrackingResult = StartTrackingResult.Started

    override suspend fun stopTracking(): StopTrackingResult = StopTrackingResult.NotTracking
}

private class FakeVisitedHistoryStore : VisitedHistoryStore {
    private val _visitedPlaces = MutableStateFlow<List<VisitedPlace>>(emptyList())
    private val _visitedCountryDays = MutableStateFlow<List<VisitedCountryDay>>(emptyList())
    val observations = mutableListOf<VisitedObservation>()

    override val visitedPlaces: Flow<List<VisitedPlace>> = _visitedPlaces
    override val visitedCountryDays: Flow<List<VisitedCountryDay>> = _visitedCountryDays

    override suspend fun recordObservation(observation: VisitedObservation) {
        observations += observation
        _visitedPlaces.value = observations.map {
            VisitedPlace(
                city = it.city,
                region = it.region,
                country = it.country,
                countryCode = it.countryCode,
                latitude = it.latitude,
                longitude = it.longitude,
                sources = listOf(it.source),
                firstVisitedAt = it.observedAt,
                lastVisitedAt = it.observedAt,
            )
        }
        _visitedCountryDays.value = observations.map {
            VisitedCountryDay(
                date = LocalDate.ofInstant(it.observedAt, java.time.ZoneId.systemDefault()),
                country = it.country,
                countryCode = it.countryCode,
                source = it.source,
                isInferred = false,
            )
        }
    }
}
