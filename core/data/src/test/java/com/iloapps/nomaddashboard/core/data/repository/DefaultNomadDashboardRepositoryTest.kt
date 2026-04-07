package com.iloapps.nomaddashboard.core.data.repository

import androidx.datastore.core.DataStore
import com.iloapps.nomaddashboard.core.data.fuel.FuelPriceProvider
import com.iloapps.nomaddashboard.core.data.fuel.FuelProviderCredentials
import com.iloapps.nomaddashboard.core.data.fuel.FuelSearchRequest
import com.iloapps.nomaddashboard.core.data.credentials.ProviderCredentialStore
import com.iloapps.nomaddashboard.core.data.emergency.EmergencyCareProvider
import com.iloapps.nomaddashboard.core.data.emergency.EmergencyCareSearchRequest
import com.iloapps.nomaddashboard.core.data.timetracking.CreateProjectResult
import com.iloapps.nomaddashboard.core.data.timetracking.StartTrackingResult
import com.iloapps.nomaddashboard.core.data.timetracking.StopTrackingResult
import com.iloapps.nomaddashboard.core.data.timetracking.TimeTrackingRepository
import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.data.location.ResolvedVisitedPlace
import com.iloapps.nomaddashboard.core.data.location.VisitedDeviceLocationProvider
import com.iloapps.nomaddashboard.core.data.monitor.TelemetryReader
import com.iloapps.nomaddashboard.core.data.monitor.TrafficSample
import com.iloapps.nomaddashboard.core.data.travelalerts.BundledNeighborCountryResolver
import com.iloapps.nomaddashboard.core.data.travelalerts.CountryNameResolver
import com.iloapps.nomaddashboard.core.data.travelalerts.ReliefWebSecurityProvider
import com.iloapps.nomaddashboard.core.data.travelalerts.SmartravellerAdvisoryProvider
import com.iloapps.nomaddashboard.core.data.visited.VisitedHistoryStore
import com.iloapps.nomaddashboard.core.data.visited.VisitedObservation
import com.iloapps.nomaddashboard.core.datastore.AppSettingsProto
import com.iloapps.nomaddashboard.core.datastore.NomadSettingsDataSource
import com.iloapps.nomaddashboard.core.datastore.toProto
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareLocationSource
import com.iloapps.nomaddashboard.core.model.EmergencyCareSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareStatus
import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.model.FuelStationPrice
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.model.ProviderCredentialSettings
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalStatus
import com.iloapps.nomaddashboard.core.model.TravelAlertUnavailableReason
import com.iloapps.nomaddashboard.core.model.TimeTrackingEntry
import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
import com.iloapps.nomaddashboard.core.model.TimeTrackingRecord
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import com.iloapps.nomaddashboard.core.network.api.FreeIpApiService
import com.iloapps.nomaddashboard.core.network.api.OpenMeteoService
import com.iloapps.nomaddashboard.core.network.api.ReliefWebReportsService
import com.iloapps.nomaddashboard.core.network.api.SmartravellerService
import com.iloapps.nomaddashboard.core.network.model.ReliefWebReportsRequest
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response

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
                override fun hasLocationPermission(): Boolean = false

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
    fun `refresh uses device location first for emergency care lookup`() = runTest {
        val settings = AppSettings(
            emergencyCareEnabled = true,
            publicIpGeolocationEnabled = true,
        )
        val emergencyCareProvider = FakeEmergencyCareProvider(
            snapshot = EmergencyCareSnapshot(
                status = EmergencyCareStatus.READY,
                countryCode = "ES",
                countryName = "Spain",
                detail = "Nearest hospital found within 10 km.",
            ),
        )
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(settings.toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(),
            emergencyCareProvider = emergencyCareProvider,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(
                value = ResolvedVisitedPlace(
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

        val request = emergencyCareProvider.requests.single()
        assertThat(request.latitude).isWithin(0.001).of(36.72)
        assertThat(request.locationSource).isEqualTo(EmergencyCareLocationSource.DEVICE)
    }

    @Test
    fun `refresh falls back to ip travel context for emergency care lookup`() = runTest {
        val settings = AppSettings(
            emergencyCareEnabled = true,
            publicIpGeolocationEnabled = true,
        )
        val emergencyCareProvider = FakeEmergencyCareProvider()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(settings.toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(),
            emergencyCareProvider = emergencyCareProvider,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(value = null),
            applicationScope = backgroundScope,
        )

        repository.refresh()
        advanceUntilIdle()

        val request = emergencyCareProvider.requests.single()
        assertThat(request.latitude).isWithin(0.001).of(60.1699)
        assertThat(request.locationSource).isEqualTo(EmergencyCareLocationSource.IP_GEOLOCATION)
    }

    @Test
    fun `refresh marks emergency care permission required when no fallback location exists`() = runTest {
        val settings = AppSettings(
            emergencyCareEnabled = true,
            publicIpGeolocationEnabled = false,
        )
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(settings.toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(),
            emergencyCareProvider = FakeEmergencyCareProvider(),
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(
                value = null,
                hasLocationPermission = false,
            ),
            applicationScope = backgroundScope,
        )

        repository.refresh()
        advanceUntilIdle()

        val snapshot = repository.snapshot.first { it.lastRefresh != null }.emergencyCare
        assertThat(snapshot.status).isEqualTo(EmergencyCareStatus.PERMISSION_REQUIRED)
        assertThat(snapshot.detail).contains("Grant location permission")
    }

    @Test
    fun `refresh forwards provider credentials to fuel provider`() = runTest {
        val settings = AppSettings(
            fuelPricesEnabled = true,
            publicIpGeolocationEnabled = false,
        )
        val fuelPriceProvider = FakeFuelPriceProvider()
        val providerCredentialStore = FakeProviderCredentialStore(
            ProviderCredentialSettings(tankerkoenigApiKey = "user-key-123"),
        )
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(settings.toProto())),
            fuelPriceProvider = fuelPriceProvider,
            providerCredentialStore = providerCredentialStore,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(
                ResolvedVisitedPlace(
                    city = "Berlin",
                    region = "Berlin",
                    country = "Germany",
                    countryCode = "DE",
                    latitude = 52.52,
                    longitude = 13.405,
                ),
            ),
            applicationScope = backgroundScope,
        )

        repository.refresh()
        advanceUntilIdle()

        assertThat(fuelPriceProvider.credentials.single().tankerkoenigApiKey).isEqualTo("user-key-123")
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

    @Test
    fun `refresh resolves travel alerts and prefers device country coverage`() = runTest {
        val securityRequests = mutableListOf<List<String>>()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(
                FakeAppSettingsDataStore(
                    AppSettings(
                        fuelPricesEnabled = true,
                        publicIpGeolocationEnabled = true,
                    ).toProto(),
                ),
            ),
            fuelPriceProvider = FakeFuelPriceProvider(),
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(
                ResolvedVisitedPlace(
                    city = "Berlin",
                    region = "Berlin",
                    country = "Germany",
                    countryCode = "DE",
                    latitude = 52.52,
                    longitude = 13.405,
                ),
            ),
            smartravellerAdvisoryProvider = smartravellerProvider(
            ),
            reliefWebSecurityProvider = reliefWebProvider(
                onRequest = { securityRequests += it },
            ),
            neighborCountryResolver = BundledNeighborCountryResolver.fromRecords(
                mapOf("DE" to listOf("PL", "FR")),
            ),
            applicationScope = backgroundScope,
        )

        repository.refresh()
        advanceUntilIdle()

        val snapshot = repository.snapshot.first { it.lastRefresh != null }.travelAlerts
        assertThat(snapshot.primaryCountryCode).isEqualTo("DE")
        assertThat(snapshot.coverageCountryCodes).containsExactly("DE", "PL", "FR").inOrder()
        assertThat(snapshot.state(com.iloapps.nomaddashboard.core.model.TravelAlertKind.ADVISORY)?.status)
            .isEqualTo(TravelAlertSignalStatus.READY)
        assertThat(snapshot.state(com.iloapps.nomaddashboard.core.model.TravelAlertKind.SECURITY)?.status)
            .isEqualTo(TravelAlertSignalStatus.READY)
        assertThat(securityRequests.single()).containsExactly("Germany", "Poland", "France").inOrder()
    }

    @Test
    fun `refresh marks travel alerts unavailable when no country context exists`() = runTest {
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(
                FakeAppSettingsDataStore(
                    AppSettings(publicIpGeolocationEnabled = false).toProto(),
                ),
            ),
            fuelPriceProvider = FakeFuelPriceProvider(),
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null),
            applicationScope = backgroundScope,
        )

        repository.refresh()
        advanceUntilIdle()

        val snapshot = repository.snapshot.first { it.lastRefresh != null }.travelAlerts
        assertThat(snapshot.state(com.iloapps.nomaddashboard.core.model.TravelAlertKind.ADVISORY)?.reason)
            .isEqualTo(TravelAlertUnavailableReason.COUNTRY_REQUIRED)
        assertThat(snapshot.state(com.iloapps.nomaddashboard.core.model.TravelAlertKind.SECURITY)?.reason)
            .isEqualTo(TravelAlertUnavailableReason.COUNTRY_REQUIRED)
    }

    @Test
    fun `refresh maps reliefweb configuration failures to unavailable state`() = runTest {
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(AppSettings().toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(),
            providerCredentialStore = FakeProviderCredentialStore(
                ProviderCredentialSettings(reliefWebAppName = ""),
            ),
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null),
            reliefWebSecurityProvider = reliefWebProvider(),
            applicationScope = backgroundScope,
        )

        repository.refresh()
        advanceUntilIdle()

        val securityState = repository.snapshot.first { it.lastRefresh != null }
            .travelAlerts
            .state(com.iloapps.nomaddashboard.core.model.TravelAlertKind.SECURITY)

        assertThat(securityState?.status).isEqualTo(TravelAlertSignalStatus.UNAVAILABLE)
        assertThat(securityState?.reason).isEqualTo(TravelAlertUnavailableReason.SOURCE_CONFIGURATION_REQUIRED)
    }

    @Test
    fun `refresh keeps stale travel alert signal visible after a later provider failure`() = runTest {
        val responseState = MutableStateFlow(
            """
            {
              "data": [
                {
                  "fields": {
                    "title": "Transit disruption advisory",
                    "date": {"created": "2026-04-07T09:00:00Z"},
                    "primary_country": {"shortname": "France"},
                    "source": [{"shortname": "ReliefWeb"}],
                    "url_alias": "/report/france/transit"
                  }
                }
              ]
            }
            """.trimIndent(),
        )
        val provider = reliefWebProvider(responseBody = responseState)
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(AppSettings().toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(),
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null),
            reliefWebSecurityProvider = provider,
            applicationScope = backgroundScope,
        )

        repository.refresh()
        advanceUntilIdle()
        responseState.value = "__INVALID_JSON__"

        repository.refresh()
        advanceUntilIdle()

        val securityState = repository.snapshot.first { it.lastRefresh != null }
            .travelAlerts
            .state(com.iloapps.nomaddashboard.core.model.TravelAlertKind.SECURITY)

        assertThat(securityState?.status).isEqualTo(TravelAlertSignalStatus.STALE)
        assertThat(securityState?.signal?.summary).contains("nearby security bulletin")
    }

    private fun repository(
        settingsDataSource: NomadSettingsDataSource,
        fuelPriceProvider: FuelPriceProvider,
        emergencyCareProvider: EmergencyCareProvider = FakeEmergencyCareProvider(),
        providerCredentialStore: ProviderCredentialStore = FakeProviderCredentialStore(
            ProviderCredentialSettings(reliefWebAppName = "NomadDashboardTests"),
        ),
        visitedHistoryStore: FakeVisitedHistoryStore,
        visitedDeviceLocationProvider: VisitedDeviceLocationProvider,
        applicationScope: CoroutineScope,
        timeTrackingRepository: TimeTrackingRepository = FakeTimeTrackingRepository(),
        smartravellerAdvisoryProvider: SmartravellerAdvisoryProvider = smartravellerProvider(),
        reliefWebSecurityProvider: ReliefWebSecurityProvider = reliefWebProvider(),
        neighborCountryResolver: BundledNeighborCountryResolver = BundledNeighborCountryResolver.fromRecords(
            mapOf("FI" to listOf("SE", "NO"), "DE" to listOf("PL", "FR")),
        ),
    ): DefaultNomadDashboardRepository =
        DefaultNomadDashboardRepository(
            settingsDataSource = settingsDataSource,
            telemetryReader = FakeTelemetryReader(),
            freeIpApiService = FakeFreeIpApiService(),
            openMeteoService = FakeOpenMeteoService(),
            fuelPriceProvider = fuelPriceProvider,
            emergencyCareProvider = emergencyCareProvider,
            timeTrackingRepository = timeTrackingRepository,
            visitedHistoryStore = visitedHistoryStore,
            visitedDeviceLocationProvider = visitedDeviceLocationProvider,
            providerCredentialStore = providerCredentialStore,
            smartravellerAdvisoryProvider = smartravellerAdvisoryProvider,
            reliefWebSecurityProvider = reliefWebSecurityProvider,
            neighborCountryResolver = neighborCountryResolver,
            applicationScope = applicationScope,
        )

    private fun smartravellerProvider(
    ): SmartravellerAdvisoryProvider =
        SmartravellerAdvisoryProvider(
            service = object : SmartravellerService {
                override suspend fun destinations(): JsonElement = TestJson.parseToJsonElement(
                    """
                    {
                      "data": [
                        {
                          "title": "Finland",
                          "advice_level": 1,
                          "canonical_url": "https://example.com/finland",
                          "updated_at": "2026-04-07T10:00:00Z"
                        },
                        {
                          "title": "Germany",
                          "advice_level": 1,
                          "canonical_url": "https://example.com/germany",
                          "updated_at": "2026-04-07T10:00:00Z"
                        },
                        {
                          "name": "France",
                          "adviceLevel": "2",
                          "link": "https://example.com/france",
                          "updatedAt": "2026-04-07T10:00:00Z"
                        },
                        {
                          "name": "Poland",
                          "adviceLevel": "1",
                          "link": "https://example.com/poland",
                          "updatedAt": "2026-04-07T10:00:00Z"
                        }
                      ]
                    }
                    """.trimIndent(),
                )
            },
            countryNameResolver = CountryNameResolver(),
        )

    private fun reliefWebProvider(
        onRequest: (List<String>) -> Unit = {},
        responseBody: MutableStateFlow<String> = MutableStateFlow(
            """
            {
              "data": [
                {
                  "fields": {
                    "title": "Border protest update",
                    "date": {"created": "2026-04-07T09:00:00Z"},
                    "primary_country": {"shortname": "France"},
                    "source": [{"shortname": "ReliefWeb"}],
                    "url_alias": "/report/france/border-protest"
                  }
                },
                {
                  "fields": {
                    "title": "Transit disruption advisory",
                    "date": {"created": "2026-04-07T08:00:00Z"},
                    "primary_country": {"shortname": "Poland"},
                    "source": [{"shortname": "ReliefWeb"}],
                    "url_alias": "/report/poland/transit"
                  }
                }
              ]
            }
            """.trimIndent(),
        ),
    ): ReliefWebSecurityProvider =
        ReliefWebSecurityProvider(
            service = object : ReliefWebReportsService {
                override suspend fun reports(
                    appName: String,
                    request: ReliefWebReportsRequest,
                ): Response<ResponseBody> {
                    onRequest(request.filter.conditions.single().value)
                    if (appName.isBlank()) {
                        return Response.error(
                            400,
                            """{"status":400,"error":{"message":"Missing appname parameter"}}"""
                                .toResponseBody(JsonMediaType),
                        )
                    }
                    return Response.success(responseBody.value.toResponseBody(JsonMediaType))
                }
            },
            countryNameResolver = CountryNameResolver(),
            json = TestJson,
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
    private val hasLocationPermission: Boolean = true,
) : VisitedDeviceLocationProvider {
    override fun hasLocationPermission(): Boolean = hasLocationPermission

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
    val credentials = mutableListOf<FuelProviderCredentials>()

    override suspend fun prices(
        request: FuelSearchRequest,
        credentials: FuelProviderCredentials,
    ): FuelPriceSnapshot {
        requests += request
        this.credentials += credentials
        return snapshot
    }
}

private class FakeEmergencyCareProvider(
    private val snapshot: EmergencyCareSnapshot = EmergencyCareSnapshot(
        status = EmergencyCareStatus.UNAVAILABLE,
        detail = "No nearby hospitals found within 10 km.",
    ),
) : EmergencyCareProvider {
    val requests = mutableListOf<EmergencyCareSearchRequest>()

    override suspend fun nearbyHospital(request: EmergencyCareSearchRequest): EmergencyCareSnapshot {
        requests += request
        return snapshot
    }
}

private class FakeProviderCredentialStore(
    initialValue: ProviderCredentialSettings = ProviderCredentialSettings(),
) : ProviderCredentialStore {
    private val state = MutableStateFlow(initialValue)

    override val credentials = state

    override suspend fun update(transform: (ProviderCredentialSettings) -> ProviderCredentialSettings) {
        state.value = transform(state.value)
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

private val TestJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private val JsonMediaType = "application/json".toMediaType()

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
