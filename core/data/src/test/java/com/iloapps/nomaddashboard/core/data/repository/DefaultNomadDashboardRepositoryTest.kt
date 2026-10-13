package com.iloapps.nomaddashboard.core.data.repository

import androidx.datastore.core.DataStore
import com.iloapps.nomaddashboard.core.data.airquality.AirQualityProvider
import com.iloapps.nomaddashboard.core.data.fuel.FuelPriceProvider
import com.iloapps.nomaddashboard.core.data.fuel.FuelProviderCredentials
import com.iloapps.nomaddashboard.core.data.fuel.FuelSearchRequest
import com.iloapps.nomaddashboard.core.data.credentials.ProviderCredentialStore
import com.iloapps.nomaddashboard.core.data.emergency.EmergencyCareProvider
import com.iloapps.nomaddashboard.core.data.emergency.EmergencyCareSearchRequest
import com.iloapps.nomaddashboard.core.data.timetracking.CreateProjectResult
import com.iloapps.nomaddashboard.core.data.timetracking.AllocateTrackedTimeResult
import com.iloapps.nomaddashboard.core.data.timetracking.StartTrackingResult
import com.iloapps.nomaddashboard.core.data.timetracking.StopTrackingResult
import com.iloapps.nomaddashboard.core.data.timetracking.ReportInterruptionResult
import com.iloapps.nomaddashboard.core.data.timetracking.TimeTrackingRepository
import com.iloapps.nomaddashboard.core.data.timetracking.UpdateTimeTrackingEntryResult
import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.data.location.DeviceLocationResolutionStatus
import com.iloapps.nomaddashboard.core.data.location.DeviceLocationSnapshot
import com.iloapps.nomaddashboard.core.data.location.ResolvedVisitedPlace
import com.iloapps.nomaddashboard.core.data.location.VisitedDeviceLocationProvider
import com.iloapps.nomaddashboard.core.data.localinfo.LocalInfoProvider
import com.iloapps.nomaddashboard.core.data.localinfo.LocalInfoRequest
import com.iloapps.nomaddashboard.core.data.localprice.LocalPriceLevelProvider
import com.iloapps.nomaddashboard.core.data.localprice.LocalPriceLevelRequest
import com.iloapps.nomaddashboard.core.data.monitor.LatencyProbeResult
import com.iloapps.nomaddashboard.core.data.monitor.TelemetryReader
import com.iloapps.nomaddashboard.core.data.monitor.TrafficSample
import com.iloapps.nomaddashboard.core.data.travelalerts.BundledNeighborCountryResolver
import com.iloapps.nomaddashboard.core.data.travelalerts.CountryNameResolver
import com.iloapps.nomaddashboard.core.data.travelalerts.ReliefWebSecurityProvider
import com.iloapps.nomaddashboard.core.data.travelalerts.SmartravellerAdvisoryProvider
import com.iloapps.nomaddashboard.core.data.travelalerts.SmartravellerBrowserFetcher
import com.iloapps.nomaddashboard.core.data.visited.VisitedHistoryStore
import com.iloapps.nomaddashboard.core.data.visited.VisitedObservation
import com.iloapps.nomaddashboard.core.database.dao.DashboardSectionCacheDao
import com.iloapps.nomaddashboard.core.database.dao.MetricPointDao
import com.iloapps.nomaddashboard.core.database.entity.DashboardSectionCacheEntity
import com.iloapps.nomaddashboard.core.database.entity.MetricPointEntity
import com.iloapps.nomaddashboard.core.datastore.AppSettingsProto
import com.iloapps.nomaddashboard.core.datastore.NomadSettingsDataSource
import com.iloapps.nomaddashboard.core.datastore.toProto
import com.iloapps.nomaddashboard.core.model.AirQualitySnapshot
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.InternetState
import com.iloapps.nomaddashboard.core.model.EmergencyCareLocationSource
import com.iloapps.nomaddashboard.core.model.EmergencyCareSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareStatus
import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.model.FuelStationPrice
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorKind
import com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorRow
import com.iloapps.nomaddashboard.core.model.LocalPriceLevelSnapshot
import com.iloapps.nomaddashboard.core.model.LocalPriceLevelStatus
import com.iloapps.nomaddashboard.core.model.LocalPricePrecision
import com.iloapps.nomaddashboard.core.model.LocalPriceSummaryBand
import com.iloapps.nomaddashboard.core.model.LocalInfoStatus
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.model.ProviderCredentialSettings
import com.iloapps.nomaddashboard.core.model.StartupLocationBootstrapPhase
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalStatus
import com.iloapps.nomaddashboard.core.model.TravelAlertUnavailableReason
import com.iloapps.nomaddashboard.core.model.TimeTrackingEntry
import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
import com.iloapps.nomaddashboard.core.model.TimeTrackingReportSnapshot
import com.iloapps.nomaddashboard.core.model.TimeTrackingRecord
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedPlaceEvent
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import com.iloapps.nomaddashboard.core.network.api.FreeIpApiService
import com.iloapps.nomaddashboard.core.network.api.IpifyService
import com.iloapps.nomaddashboard.core.network.api.OpenMeteoMarineService
import com.iloapps.nomaddashboard.core.network.api.OpenMeteoService
import com.iloapps.nomaddashboard.core.network.api.ReliefWebReportsService
import com.iloapps.nomaddashboard.core.network.api.SmartravellerService
import com.iloapps.nomaddashboard.core.network.model.IpifyResponse
import com.iloapps.nomaddashboard.core.network.model.ReliefWebReportsRequest
import com.iloapps.nomaddashboard.core.network.model.FreeIpApiResponse
import com.iloapps.nomaddashboard.core.network.model.OpenMeteoCurrent
import com.iloapps.nomaddashboard.core.network.model.OpenMeteoDaily
import com.iloapps.nomaddashboard.core.network.model.OpenMeteoHourly
import com.iloapps.nomaddashboard.core.network.model.OpenMeteoMarineResponse
import com.iloapps.nomaddashboard.core.network.model.OpenMeteoResponse
import java.time.Duration
import java.time.Clock
import java.time.ZoneOffset
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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

        refreshRepository(repository)

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
    fun `refresh records only device visit when device resolves and ip appears to be vpn`() = runTest {
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

        refreshRepository(repository)

        assertThat(visitedStore.observations.map(VisitedObservation::source))
            .containsExactly(VisitedPlaceSource.DEVICE_LOCATION)
        assertThat(visitedStore.observations.single().countryCode).isEqualTo("ES")
        assertThat(visitedStore.observations.single().country).isEqualTo("Spain")
        assertThat(repository.snapshot.first { it.lastRefresh != null }.visited.sourceSummary).isEqualTo("IP + Device")
    }

    @Test
    fun `refresh waits for delayed device visit before storing ip fallback`() = runTest {
        val settings = AppSettings(
            visitedPlacesEnabled = true,
            publicIpGeolocationEnabled = true,
            useCurrentLocationForVisitedPlaces = true,
        )
        val visitedStore = FakeVisitedHistoryStore()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(settings.toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(),
            visitedHistoryStore = visitedStore,
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(
                value = ResolvedVisitedPlace(
                    city = "Malaga",
                    region = "Andalusia",
                    country = "Spain",
                    countryCode = "ES",
                    latitude = 36.72,
                    longitude = -4.42,
                ),
                delayMillis = 250,
            ),
            applicationScope = backgroundScope,
        )

        refreshRepository(repository)

        assertThat(visitedStore.observations.map(VisitedObservation::source))
            .containsExactly(VisitedPlaceSource.DEVICE_LOCATION)
        assertThat(visitedStore.observations.single().city).isEqualTo("Malaga")
    }

    @Test
    fun `refresh falls back to ip visit when device location is unavailable`() = runTest {
        val settings = AppSettings(
            visitedPlacesEnabled = true,
            publicIpGeolocationEnabled = true,
            useCurrentLocationForVisitedPlaces = true,
        )
        val visitedStore = FakeVisitedHistoryStore()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(settings.toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(),
            visitedHistoryStore = visitedStore,
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null),
            applicationScope = backgroundScope,
        )

        refreshRepository(repository)

        assertThat(visitedStore.observations.map(VisitedObservation::source))
            .containsExactly(VisitedPlaceSource.PUBLIC_IP_GEOLOCATION)
    }

    @Test
    fun `refresh carries both ip and device location into travel context`() = runTest {
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(AppSettings().toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(),
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(
                ResolvedVisitedPlace(
                    city = "Paris",
                    region = "Ile-de-France",
                    country = "France",
                    countryCode = "FR",
                    latitude = 48.8566,
                    longitude = 2.3522,
                ),
            ),
            applicationScope = backgroundScope,
        )

        refreshRepository(repository)

        val travelContext = repository.snapshot.first { it.lastRefresh != null }.travelContext
        assertThat(travelContext.publicIp).isEqualTo("1.2.3.4")
        assertThat(travelContext.country).isEqualTo("Finland")
        assertThat(travelContext.timeZoneId).isEqualTo("Europe/Helsinki")
        assertThat(travelContext.deviceCity).isEqualTo("Paris")
        assertThat(travelContext.deviceCountry).isEqualTo("France")
        assertThat(travelContext.deviceLatitude).isEqualTo(48.8566)
    }

    @Test
    fun `refresh keeps last known ip context when lookup fails`() = runTest {
        val freeIpApiService = SequenceFreeIpApiService(
            responses = listOf(
                Result.success(
                    FreeIpApiResponse(
                        ipAddress = "1.2.3.4",
                        cityName = "Helsinki",
                        regionName = "Uusimaa",
                        countryName = "Finland",
                        countryCode = "FI",
                        latitude = 60.1699,
                        longitude = 24.9384,
                        timeZones = listOf("Europe/Helsinki"),
                    ),
                ),
                Result.failure(IllegalStateException("lookup failed")),
            ),
        )
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(AppSettings().toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(),
            freeIpApiService = freeIpApiService,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null),
            applicationScope = backgroundScope,
        )

        refreshRepository(repository)
        refreshRepository(repository)

        val travelContext = repository.snapshot.first { it.lastRefresh != null }.travelContext
        assertThat(travelContext.publicIp).isEqualTo("1.2.3.4")
        assertThat(travelContext.city).isEqualTo("Helsinki")
        assertThat(travelContext.country).isEqualTo("Finland")
    }

    @Test
    fun `refresh falls back to ipify plus freeip by-address lookup when current lookup fails`() = runTest {
        val freeIpApiService = SequenceFreeIpApiService(
            responses = listOf(
                Result.failure(IllegalStateException("lookup me failed")),
            ),
            responsesByAddress = mapOf(
                "198.51.100.12" to Result.success(
                    FreeIpApiResponse(
                        ipAddress = "198.51.100.12",
                        cityName = "Paris",
                        regionName = "Ile-de-France",
                        countryName = "France",
                        countryCode = "FR",
                        latitude = 48.8566,
                        longitude = 2.3522,
                        timeZones = listOf("Europe/Paris"),
                    ),
                ),
            ),
        )
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(AppSettings().toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(),
            freeIpApiService = freeIpApiService,
            ipifyService = FakeIpifyService(ip = "198.51.100.12"),
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null),
            applicationScope = backgroundScope,
        )

        refreshRepository(repository)

        val travelContext = repository.snapshot.first { it.lastRefresh != null }.travelContext
        assertThat(travelContext.publicIp).isEqualTo("198.51.100.12")
        assertThat(travelContext.city).isEqualTo("Paris")
        assertThat(travelContext.country).isEqualTo("France")
        assertThat(travelContext.timeZoneId).isEqualTo("Europe/Paris")
    }

    @Test
    fun `refresh resolves local price level from ip country context in europe`() = runTest {
        val localInfoProvider = FakeLocalInfoProvider()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(
                FakeAppSettingsDataStore(
                    AppSettings(localInfoEnabled = true).toProto(),
                ),
            ),
            fuelPriceProvider = FakeFuelPriceProvider(),
            localInfoProvider = localInfoProvider,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null),
            applicationScope = backgroundScope,
        )

        refreshRepository(repository)

        val localInfo = repository.snapshot.first { it.lastRefresh != null }.localInfo
        assertThat(localInfo.status).isEqualTo(LocalInfoStatus.READY)
        assertThat(localInfo.localPriceLevel.summaryBand).isEqualTo(LocalPriceSummaryBand.MEDIUM)
        assertThat(localInfoProvider.requests.single().countryCode).isEqualTo("FI")
        assertThat(localInfoProvider.requests.single().latitude).isEqualTo(60.1699)
    }

    @Test
    fun `refresh marks local price configuration required in us without token`() = runTest {
        val localInfoProvider = FakeLocalInfoProvider()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(
                FakeAppSettingsDataStore(
                    AppSettings(localInfoEnabled = true, publicIpGeolocationEnabled = false).toProto(),
                ),
            ),
            fuelPriceProvider = FakeFuelPriceProvider(),
            localInfoProvider = localInfoProvider,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(
                ResolvedVisitedPlace(
                    city = "Seattle",
                    region = "Washington",
                    country = "United States",
                    countryCode = "US",
                    latitude = 47.61,
                    longitude = -122.33,
                ),
            ),
            applicationScope = backgroundScope,
        )

        refreshRepository(repository)

        val localInfo = repository.snapshot.first { it.lastRefresh != null }.localInfo
        assertThat(localInfo.status).isEqualTo(LocalInfoStatus.PARTIAL)
        assertThat(localInfo.localPriceLevel.status).isEqualTo(LocalPriceLevelStatus.CONFIGURATION_REQUIRED)
    }

    @Test
    fun `refresh returns us local price row when token and current location exist`() = runTest {
        val localInfoProvider = FakeLocalInfoProvider()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(
                FakeAppSettingsDataStore(
                    AppSettings(localInfoEnabled = true, publicIpGeolocationEnabled = false).toProto(),
                ),
            ),
            fuelPriceProvider = FakeFuelPriceProvider(),
            localInfoProvider = localInfoProvider,
            providerCredentialStore = FakeProviderCredentialStore(
                ProviderCredentialSettings(hudUserApiToken = "hud-token-123", reliefWebAppName = "NomadDashboardTests"),
            ),
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(
                ResolvedVisitedPlace(
                    city = "Seattle",
                    region = "Washington",
                    country = "United States",
                    countryCode = "US",
                    latitude = 47.61,
                    longitude = -122.33,
                ),
            ),
            applicationScope = backgroundScope,
        )

        refreshRepository(repository)

        val localInfo = repository.snapshot.first { it.lastRefresh != null }.localInfo
        assertThat(localInfo.status).isEqualTo(LocalInfoStatus.PARTIAL)
        assertThat(localInfo.localPriceLevel.summaryBand).isEqualTo(LocalPriceSummaryBand.LIMITED)
        assertThat(localInfo.localPriceLevel.rows.single().kind).isEqualTo(LocalPriceIndicatorKind.RENT_ONE_BEDROOM)
    }

    @Test
    fun `refresh marks local price location required with no country context`() = runTest {
        val localInfoProvider = FakeLocalInfoProvider()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(
                FakeAppSettingsDataStore(
                    AppSettings(localInfoEnabled = true, publicIpGeolocationEnabled = false).toProto(),
                ),
            ),
            fuelPriceProvider = FakeFuelPriceProvider(),
            localInfoProvider = localInfoProvider,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null, hasLocationPermission = false),
            applicationScope = backgroundScope,
        )

        refreshRepository(repository)

        val localInfo = repository.snapshot.first { it.lastRefresh != null }.localInfo
        assertThat(localInfo.status).isEqualTo(LocalInfoStatus.LOCATION_REQUIRED)
    }

    @Test
    fun `refresh marks local price unsupported for unsupported country`() = runTest {
        val localInfoProvider = FakeLocalInfoProvider()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(
                FakeAppSettingsDataStore(
                    AppSettings(localInfoEnabled = true, publicIpGeolocationEnabled = false).toProto(),
                ),
            ),
            fuelPriceProvider = FakeFuelPriceProvider(),
            localInfoProvider = localInfoProvider,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(
                ResolvedVisitedPlace(
                    city = "Tokyo",
                    region = "Tokyo",
                    country = "Japan",
                    countryCode = "JP",
                    latitude = 35.68,
                    longitude = 139.76,
                ),
            ),
            applicationScope = backgroundScope,
        )

        refreshRepository(repository)

        val localInfo = repository.snapshot.first { it.lastRefresh != null }.localInfo
        assertThat(localInfo.status).isEqualTo(LocalInfoStatus.UNSUPPORTED)
    }

    @Test
    fun `update provider credentials clears us local price cache when hud token changes`() = runTest {
        val localPriceProvider = FakeLocalPriceLevelProvider()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(
                FakeAppSettingsDataStore(
                    AppSettings(localInfoEnabled = true).toProto(),
                ),
            ),
            fuelPriceProvider = FakeFuelPriceProvider(),
            localPriceLevelProvider = localPriceProvider,
            providerCredentialStore = FakeProviderCredentialStore(
                ProviderCredentialSettings(hudUserApiToken = "old-token", reliefWebAppName = "NomadDashboardTests"),
            ),
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null),
            applicationScope = backgroundScope,
        )

        repository.updateProviderCredentials { current ->
            current.copy(hudUserApiToken = "new-token")
        }
        advanceUntilIdle()

        assertThat(localPriceProvider.clearUsCacheCount).isEqualTo(1)
    }

    @Test
    fun `free ip model decodes string timeZones`() {
        val response = TestJson.decodeFromString<FreeIpApiResponse>(
            """
            {
              "ipAddress": "203.0.113.42",
              "countryName": "Spain",
              "timeZones": "Europe/Madrid"
            }
            """.trimIndent(),
        )

        assertThat(response.ipAddress).isEqualTo("203.0.113.42")
        assertThat(response.timeZones).containsExactly("Europe/Madrid")
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

                override suspend fun currentLocation(): DeviceLocationSnapshot = error("should not be called")
            },
            applicationScope = backgroundScope,
        )

        refreshRepository(repository)

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

        refreshRepository(repository)

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

        refreshRepository(repository)

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

        refreshRepository(repository)

        assertThat(fuelPriceProvider.requests).isEmpty()
        assertThat(repository.snapshot.first { it.lastRefresh != null }.fuelPrices.status)
            .isEqualTo(FuelPriceStatus.UNAVAILABLE)
    }

    @Test
    fun `refresh uses device location first for weather lookup when enabled`() = runTest {
        val settings = AppSettings(
            publicIpGeolocationEnabled = false,
            useCurrentLocationForWeather = true,
        )
        val openMeteoService = FakeOpenMeteoService(
            response = OpenMeteoResponse(
                current = OpenMeteoCurrent(
                    temperatureCelsius = 19.4,
                    apparentTemperatureCelsius = 18.7,
                    precipitationProbability = 15,
                    windSpeedKph = 12.3,
                    windDirectionDegrees = 270.0,
                ),
                daily = OpenMeteoDaily(
                    dates = listOf("2026-04-07"),
                    minTemperatures = listOf(13.0),
                    maxTemperatures = listOf(21.0),
                    rainChance = listOf(20),
                    weatherCodes = listOf(1),
                ),
            ),
        )
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(settings.toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(),
            openMeteoService = openMeteoService,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(
                ResolvedVisitedPlace(
                    city = "Paris",
                    region = "Ile-de-France",
                    country = "France",
                    countryCode = "FR",
                    latitude = 48.8566,
                    longitude = 2.3522,
                ),
            ),
            applicationScope = backgroundScope,
        )

        refreshRepository(repository)

        val request = openMeteoService.requests.first { it.current.contains("temperature_2m") }
        val snapshot = repository.snapshot.first { it.lastRefresh != null }.weather

        assertThat(request.latitude).isWithin(0.001).of(48.8566)
        assertThat(request.longitude).isWithin(0.001).of(2.3522)
        assertThat(snapshot.currentTemperatureCelsius).isWithin(0.001).of(19.4)
        assertThat(snapshot.summary).isEqualTo("Today tops out at 21°C")
    }

    @Test
    fun `refresh retains connectivity history without inventing missing throughput`() = runTest {
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(AppSettings().toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(),
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null),
            telemetryReader = FakeTelemetryReader(
                connectivitySnapshots = listOf(
                    ConnectivitySnapshot(
                        internetState = InternetState.ONLINE,
                        isOnline = true,
                        latencyMs = 34.0,
                        downloadMbps = null,
                        uploadMbps = null,
                        wifiName = "Test Wi-Fi",
                    ),
                    ConnectivitySnapshot(
                        internetState = InternetState.ONLINE,
                        isOnline = true,
                        latencyMs = 41.0,
                        downloadMbps = 86.4,
                        uploadMbps = 18.2,
                        wifiName = "Test Wi-Fi",
                    ),
                ),
            ),
            metricPointDao = FakeMetricPointDao(),
            applicationScope = backgroundScope,
        )

        refreshRepository(repository)
        refreshRepository(repository)

        val connectivity = repository.snapshot.first { it.lastRefresh != null }.connectivity

        assertThat(connectivity.downloadHistoryMbps.map { it.value }).containsExactly(86.4).inOrder()
        assertThat(connectivity.uploadHistoryMbps.map { it.value }).containsExactly(18.2).inOrder()
        assertThat(connectivity.latencyHistoryMs.map { it.value }).containsExactly(34.0, 41.0).inOrder()
    }

    @Test
    fun `refresh retains battery history and richer power diagnostics`() = runTest {
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(AppSettings().toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(),
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null),
            telemetryReader = FakeTelemetryReader(
                powerSnapshots = listOf(
                    PowerSnapshot(
                        batteryPercent = 63,
                        charging = false,
                        statusLabel = "On Battery",
                        batteryHealthSummary = "Good",
                        powerSourceLabel = "Battery",
                        temperatureCelsius = 30.1,
                    ),
                    PowerSnapshot(
                        batteryPercent = 61,
                        charging = false,
                        statusLabel = "On Battery",
                        batteryHealthSummary = "Good",
                        powerSourceLabel = "Battery",
                        temperatureCelsius = 31.0,
                        dischargeWatts = 5.4,
                    ),
                ),
            ),
            metricPointDao = FakeMetricPointDao(),
            applicationScope = backgroundScope,
        )

        refreshRepository(repository)
        refreshRepository(repository)

        val power = repository.snapshot.first { it.lastRefresh != null }.power

        assertThat(power.statusLabel).isEqualTo("On Battery")
        assertThat(power.batteryHealthSummary).isEqualTo("Good")
        assertThat(power.batteryPercentHistory.map { it.value }).containsExactly(63.0, 61.0).inOrder()
    }

    @Test
    fun `refresh falls back to ip weather lookup when device weather location is unavailable`() = runTest {
        val settings = AppSettings(
            publicIpGeolocationEnabled = true,
            useCurrentLocationForWeather = true,
        )
        val openMeteoService = FakeOpenMeteoService()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(settings.toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(),
            openMeteoService = openMeteoService,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(value = null),
            applicationScope = backgroundScope,
        )

        refreshRepository(repository)

        val request = openMeteoService.requests.first { it.current.contains("temperature_2m") }
        assertThat(request.latitude).isWithin(0.001).of(60.1699)
        assertThat(request.longitude).isWithin(0.001).of(24.9384)
    }

    @Test
    fun `refresh shows weather permission guidance when no weather location source resolves`() = runTest {
        val settings = AppSettings(
            publicIpGeolocationEnabled = false,
            useCurrentLocationForWeather = true,
        )
        val openMeteoService = FakeOpenMeteoService()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(settings.toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(),
            openMeteoService = openMeteoService,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(
                value = null,
                hasLocationPermission = false,
            ),
            applicationScope = backgroundScope,
        )

        refreshRepository(repository)

        val weather = repository.snapshot.first { it.lastRefresh != null }.weather
        assertThat(weather.summary).isEqualTo("Grant location permission for weather.")
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

        refreshRepository(repository)

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

        refreshRepository(repository)

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

        refreshRepository(repository)

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

        refreshRepository(repository)

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

        refreshRepository(repository)

        val snapshot = repository.snapshot.first { it.lastRefresh != null }
        assertThat(snapshot.timeTracking.headline).isEqualTo("Running")
        assertThat(snapshot.timeTracking.detail).contains("capture since")
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

        refreshRepository(repository)

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

        refreshRepository(repository)

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

        refreshRepository(repository)

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
                    "date": {"created": "${java.time.Instant.now()}"},
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

        refreshRepository(repository)
        responseState.value = "__INVALID_JSON__"

        val forced = async { repository.refreshCard(com.iloapps.nomaddashboard.core.model.DashboardCardId.TRAVEL_ALERTS) }
        advanceUntilIdle()
        forced.await()

        val securityState = repository.snapshot.first { it.lastRefresh != null }
            .travelAlerts
            .state(com.iloapps.nomaddashboard.core.model.TravelAlertKind.SECURITY)

        assertThat(securityState?.status).isEqualTo(TravelAlertSignalStatus.STALE)
        assertThat(securityState?.signal?.summary).contains("nearby security bulletin")
    }

    @Test
    fun `refresh uses device coordinates when reverse geocoding is unavailable`() = runTest {
        val settings = AppSettings(
            publicIpGeolocationEnabled = true,
            useCurrentLocationForWeather = true,
            fuelPricesEnabled = true,
            emergencyCareEnabled = true,
        )
        val openMeteoService = FakeOpenMeteoService()
        val fuelPriceProvider = FakeFuelPriceProvider()
        val emergencyCareProvider = FakeEmergencyCareProvider()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(settings.toProto())),
            fuelPriceProvider = fuelPriceProvider,
            openMeteoService = openMeteoService,
            emergencyCareProvider = emergencyCareProvider,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(
                value = null,
                coordinatesOnly = 48.8566 to 2.3522,
            ),
            applicationScope = backgroundScope,
        )

        refreshRepository(repository)

        val weatherRequest = openMeteoService.requests.first { it.current.contains("temperature_2m") }
        val fuelRequest = fuelPriceProvider.requests.single()
        val emergencyRequest = emergencyCareProvider.requests.single()
        val snapshot = repository.snapshot.first { it.lastRefresh != null }

        assertThat(weatherRequest.latitude).isWithin(0.001).of(48.8566)
        assertThat(weatherRequest.longitude).isWithin(0.001).of(2.3522)
        assertThat(fuelRequest.latitude).isWithin(0.001).of(48.8566)
        assertThat(fuelRequest.countryCode).isEqualTo("FI")
        assertThat(emergencyRequest.latitude).isWithin(0.001).of(48.8566)
        assertThat(emergencyRequest.locationSource).isEqualTo(EmergencyCareLocationSource.DEVICE)
        assertThat(snapshot.startupLocation.phase).isEqualTo(StartupLocationBootstrapPhase.USING_DEVICE_LOCATION)
    }

    @Test
    fun `refresh exposes checking startup state before ip fallback completes`() = runTest {
        val settings = AppSettings(
            publicIpGeolocationEnabled = true,
            useCurrentLocationForWeather = true,
        )
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(settings.toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(),
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(
                value = null,
                delayMillis = 5_000,
            ),
            applicationScope = backgroundScope,
        )

        val refresh = async { repository.refresh() }
        runCurrent()

        val checkingSnapshot = repository.snapshot.value
        assertThat(checkingSnapshot.isRefreshing).isTrue()
        assertThat(checkingSnapshot.startupLocation.isChecking).isTrue()
        assertThat(checkingSnapshot.startupLocation.phase)
            .isEqualTo(StartupLocationBootstrapPhase.CHECKING_DEVICE_LOCATION)

        advanceTimeBy(4_000)
        advanceUntilIdle()
        refresh.await()

        val finalSnapshot = repository.snapshot.first { it.lastRefresh != null }
        assertThat(finalSnapshot.startupLocation.phase)
            .isEqualTo(StartupLocationBootstrapPhase.FALLING_BACK_TO_IP_LOCATION)
        assertThat(finalSnapshot.travelContext.countryCode).isEqualTo("FI")
    }

    @Test
    fun `refresh without ip fallback ends with unavailable location states after startup wait`() = runTest {
        val settings = AppSettings(
            publicIpGeolocationEnabled = false,
            useCurrentLocationForWeather = true,
            fuelPricesEnabled = true,
            emergencyCareEnabled = true,
            localInfoEnabled = true,
        )
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(settings.toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(),
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(
                value = null,
                delayMillis = 5_000,
            ),
            applicationScope = backgroundScope,
        )

        val refresh = async { repository.refresh() }
        runCurrent()
        assertThat(repository.snapshot.value.startupLocation.isChecking).isTrue()

        advanceTimeBy(4_000)
        advanceUntilIdle()
        refresh.await()

        val snapshot = repository.snapshot.first { it.lastRefresh != null }
        assertThat(snapshot.startupLocation.phase)
            .isEqualTo(StartupLocationBootstrapPhase.NO_LOCATION_SOURCE_AVAILABLE)
        assertThat(snapshot.weather.summary).isEqualTo("Weather data unavailable")
        assertThat(snapshot.fuelPrices.status).isEqualTo(FuelPriceStatus.UNAVAILABLE)
        assertThat(snapshot.emergencyCare.status).isEqualTo(EmergencyCareStatus.UNAVAILABLE)
        assertThat(snapshot.localInfo.status).isEqualTo(LocalInfoStatus.LOCATION_REQUIRED)
        assertThat(snapshot.travelAlerts.state(com.iloapps.nomaddashboard.core.model.TravelAlertKind.ADVISORY)?.reason)
            .isEqualTo(TravelAlertUnavailableReason.COUNTRY_REQUIRED)
    }

    @Test
    fun `refresh runs as a single flight when concurrent callers overlap`() = runTest {
        val settings = AppSettings(
            publicIpGeolocationEnabled = true,
            useCurrentLocationForWeather = true,
        )
        val openMeteoService = FakeOpenMeteoService()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(settings.toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(),
            openMeteoService = openMeteoService,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(
                value = null,
                delayMillis = 5_000,
            ),
            applicationScope = backgroundScope,
        )

        val firstRefresh = async { repository.refresh() }
        val secondRefresh = async { repository.refresh() }
        runCurrent()

        advanceTimeBy(4_000)
        advanceUntilIdle()
        firstRefresh.await()
        secondRefresh.await()

        assertThat(openMeteoService.requests.count { it.current.contains("temperature_2m") }).isEqualTo(1)
        assertThat(repository.snapshot.first { it.lastRefresh != null }.startupLocation.phase)
            .isEqualTo(StartupLocationBootstrapPhase.FALLING_BACK_TO_IP_LOCATION)
    }

    @Test
    fun `ready cards publish while fuel is suspended`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val fuel = object : FuelPriceProvider {
            override suspend fun prices(request: FuelSearchRequest, credentials: FuelProviderCredentials): FuelPriceSnapshot {
                gate.await()
                return FuelPriceSnapshot(status = FuelPriceStatus.NO_STATIONS_FOUND, fetchedAt = Instant.now())
            }
        }
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(AppSettings(fuelPricesEnabled = true).toProto())),
            fuelPriceProvider = fuel, visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null), applicationScope = backgroundScope,
        )
        val refresh = async { repository.refresh() }
        runCurrent()
        assertThat(repository.snapshot.value.power.batteryPercent).isEqualTo(80)
        assertThat(repository.snapshot.value.weather.fetchedAt).isNotNull()
        assertThat(repository.snapshot.value.isRefreshing).isTrue()
        assertThat(refresh.isCompleted).isFalse()
        gate.complete(Unit)
        advanceUntilIdle()
        refresh.await()
        assertThat(repository.snapshot.value.isRefreshing).isFalse()
    }

    @Test
    fun `device result publishes without waiting for stalled public ip`() = runTest {
        val service = object : FreeIpApiService {
            override suspend fun lookupMe(): FreeIpApiResponse = kotlinx.coroutines.awaitCancellation()
            override suspend fun lookup(ipAddress: String): FreeIpApiResponse = error("No fallback after cancellation")
        }
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(AppSettings(useCurrentLocationForWeather = true).toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(), freeIpApiService = service,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(
                ResolvedVisitedPlace("Malaga", "Andalusia", "Spain", "ES", 36.72, -4.42), delayMillis = 800),
            applicationScope = backgroundScope,
        )
        val refresh = async { repository.refresh() }
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(repository.snapshot.value.weather.fetchedAt).isNotNull()
        assertThat(repository.snapshot.value.travelContext.deviceCountryCode).isEqualTo("ES")
        assertThat(refresh.isCompleted).isTrue()
        refresh.await()
    }

    @Test
    fun `cancelled refresh releases coalesced callers and permits retry`() = runTest {
        var hold = true
        val fuel = object : FuelPriceProvider {
            override suspend fun prices(request: FuelSearchRequest, credentials: FuelProviderCredentials): FuelPriceSnapshot {
                if (hold) kotlinx.coroutines.awaitCancellation()
                return FuelPriceSnapshot(status = FuelPriceStatus.NO_STATIONS_FOUND, fetchedAt = Instant.now())
            }
        }
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(AppSettings(fuelPricesEnabled = true).toProto())),
            fuelPriceProvider = fuel, visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null), applicationScope = backgroundScope,
        )
        val first = async { repository.refresh() }
        runCurrent()
        val coalesced = async { repository.refresh() }
        runCurrent()
        first.cancel()
        runCurrent()
        assertThat(first.isCancelled).isTrue()
        assertThat(coalesced.isCancelled).isTrue()
        assertThat(repository.snapshot.value.isRefreshing).isFalse()
        hold = false
        refreshRepository(repository)
        assertThat(repository.snapshot.value.fuelPrices.fetchedAt).isNotNull()
    }

    @Test
    fun `low data defers fuel until explicit card refresh`() = runTest {
        val fuel = FakeFuelPriceProvider()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(AppSettings(fuelPricesEnabled = true,
                resourceMode = com.iloapps.nomaddashboard.core.model.ResourceMode.LOW_DATA).toProto())),
            fuelPriceProvider = fuel, visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null), applicationScope = backgroundScope,
        )
        refreshRepository(repository)
        assertThat(fuel.requests).isEmpty()
        assertThat(repository.snapshot.value.lowImpactMode).isTrue()
        val refresh = async { repository.refreshCard(com.iloapps.nomaddashboard.core.model.DashboardCardId.FUEL_PRICES) }
        advanceUntilIdle()
        refresh.await()
        assertThat(fuel.requests).hasSize(1)
    }

    @Test
    fun `refresh inside section ttl reuses successful fuel result`() = runTest {
        val fuel = FakeFuelPriceProvider(FuelPriceSnapshot(status = FuelPriceStatus.NO_STATIONS_FOUND, fetchedAt = Instant.now()))
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(AppSettings(fuelPricesEnabled = true).toProto())),
            fuelPriceProvider = fuel, visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null), applicationScope = backgroundScope,
        )
        refreshRepository(repository)
        refreshRepository(repository)
        assertThat(fuel.requests).hasSize(1)
    }

    @Test
    fun `failed ip refresh never records cached country as a fresh visit`() = runTest {
        val service = SequenceFreeIpApiService(listOf(
            Result.success(FreeIpApiResponse(ipAddress = "1.2.3.4", countryName = "Finland", countryCode = "FI", cityName = "Helsinki")),
            Result.failure(java.io.IOException("offline")),
        ))
        val history = FakeVisitedHistoryStore()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(AppSettings().toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(), freeIpApiService = service, visitedHistoryStore = history,
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null), applicationScope = backgroundScope,
        )
        refreshRepository(repository)
        refreshRepository(repository)
        assertThat(history.observations).hasSize(1)
    }

    @Test
    fun `refresh captures current and hourly uv and keeps the next 24 hours of hourly slots`() = runTest {
        val now = Instant.now()
        val firstHour = now.truncatedTo(java.time.temporal.ChronoUnit.HOURS).minusSeconds(3 * 3_600)
        val hours = (0 until 48).map { firstHour.plusSeconds(it * 3_600L) }
        val openMeteoService = FakeOpenMeteoService(
            response = OpenMeteoResponse(
                timezone = "UTC",
                utcOffsetSeconds = 0,
                current = OpenMeteoCurrent(temperatureCelsius = 20.0, uvIndex = 6.6),
                hourly = OpenMeteoHourly(
                    times = hours.map { java.time.LocalDateTime.ofInstant(it, java.time.ZoneOffset.UTC).toString() },
                    temperatures = hours.map { 20.0 },
                    rainChance = hours.map { 10 },
                    weatherCodes = hours.map { 1 },
                    windSpeedsKph = hours.map { 15.0 },
                    windDirectionDegrees = hours.map { 270.0 },
                    uvIndexes = hours.indices.map { if (it == 5) 8.4 else if (it == 6) null else it / 10.0 },
                ),
            ),
        )
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(AppSettings().toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(),
            openMeteoService = openMeteoService,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null),
            applicationScope = backgroundScope,
        )

        refreshRepository(repository)

        val request = openMeteoService.requests.first { it.current.contains("temperature_2m") }
        assertThat(request.current.split(",")).contains("uv_index")
        assertThat(request.hourly.split(",")).contains("uv_index")
        val weather = repository.snapshot.first { it.lastRefresh != null }.weather
        assertThat(weather.uvIndex).isEqualTo(7)
        // Every hour from the current one through the next 24 h, each with its own UV.
        val slots = weather.hourlyForecast
        assertThat(slots.first().at).isAtLeast(now.truncatedTo(java.time.temporal.ChronoUnit.HOURS))
        assertThat(slots.first().at).isAtMost(now.plusSeconds(3_600))
        assertThat(slots.last().at).isAtMost(now.plusSeconds(25 * 3_600))
        assertThat(slots.last().at).isGreaterThan(now.plusSeconds(23 * 3_600))
        assertThat(slots.map { it.at }.zipWithNext { a, b -> b.epochSecond - a.epochSecond }.toSet()).containsExactly(3_600L)
        val expectedUv = hours.indices.associate { hours[it] to when (it) {
            5 -> 8
            6 -> null
            else -> Math.round(it / 10.0).toInt()
        } }
        slots.forEach { assertThat(it.uvIndex).isEqualTo(expectedUv.getValue(it.at)) }
    }

    @Test
    fun `air quality is fetched for the device coordinate when weather location and air quality are on`() = runTest {
        val airQuality = FakeAirQualityProvider()
        val cache = FakeDashboardSectionCacheDao()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(
                AppSettings(publicIpGeolocationEnabled = false, useCurrentLocationForWeather = true).toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(), airQualityProvider = airQuality,
            dashboardSectionCacheDao = cache, visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(parisPlace), applicationScope = backgroundScope,
        )

        refreshRepository(repository)

        assertThat(airQuality.requests).containsExactly(48.8566 to 2.3522)
        val published = repository.snapshot.value.airQuality
        assertThat(published?.europeanAqi).isEqualTo(42)
        assertThat(cache.all().map { it.sectionId }).contains(DashboardSectionId.AIR_QUALITY)
    }

    @Test
    fun `air quality is not fetched without device weather location consent`() = runTest {
        val airQuality = FakeAirQualityProvider()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(
                AppSettings(publicIpGeolocationEnabled = false, useCurrentLocationForWeather = false).toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(), airQualityProvider = airQuality,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(parisPlace), applicationScope = backgroundScope,
        )

        refreshRepository(repository)

        assertThat(airQuality.requests).isEmpty()
        assertThat(repository.snapshot.value.airQuality).isNull()
    }

    @Test
    fun `air quality is not fetched when its own setting is off`() = runTest {
        val airQuality = FakeAirQualityProvider()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(
                AppSettings(useCurrentLocationForWeather = true, airQualityEnabled = false).toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(), airQualityProvider = airQuality,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(parisPlace), applicationScope = backgroundScope,
        )

        refreshRepository(repository)

        assertThat(airQuality.requests).isEmpty()
        assertThat(repository.snapshot.value.airQuality).isNull()
    }

    @Test
    fun `air quality never uses the public ip location when there is no device fix`() = runTest {
        val airQuality = FakeAirQualityProvider()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(
                AppSettings(publicIpGeolocationEnabled = true, useCurrentLocationForWeather = true).toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(), airQualityProvider = airQuality,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null), applicationScope = backgroundScope,
        )

        refreshRepository(repository)

        assertThat(airQuality.requests).isEmpty()
        assertThat(repository.snapshot.value.airQuality).isNull()
    }

    @Test
    fun `air quality is still fetched in low data mode`() = runTest {
        val airQuality = FakeAirQualityProvider()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(AppSettings(
                publicIpGeolocationEnabled = false, useCurrentLocationForWeather = true,
                resourceMode = com.iloapps.nomaddashboard.core.model.ResourceMode.LOW_DATA).toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(), airQualityProvider = airQuality,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(parisPlace), applicationScope = backgroundScope,
        )

        refreshRepository(repository)

        assertThat(repository.snapshot.value.lowImpactMode).isTrue()
        assertThat(airQuality.requests).hasSize(1)
    }

    @Test
    fun `air quality inside its refresh interval is reused and a weather card refresh forces it`() = runTest {
        val airQuality = FakeAirQualityProvider()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(
                AppSettings(publicIpGeolocationEnabled = false, useCurrentLocationForWeather = true).toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(), airQualityProvider = airQuality,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(parisPlace), applicationScope = backgroundScope,
        )

        refreshRepository(repository)
        refreshRepository(repository)
        assertThat(airQuality.requests).hasSize(1)

        val weatherRefresh = async { repository.refreshCard(com.iloapps.nomaddashboard.core.model.DashboardCardId.WEATHER) }
        advanceUntilIdle()
        weatherRefresh.await()
        assertThat(airQuality.requests).hasSize(2)

        val fuelRefresh = async { repository.refreshCard(com.iloapps.nomaddashboard.core.model.DashboardCardId.FUEL_PRICES) }
        advanceUntilIdle()
        fuelRefresh.await()
        assertThat(airQuality.requests).hasSize(2)
    }

    @Test
    fun `a failed air quality refresh keeps the last value for the same grid point`() = runTest {
        val airQuality = FakeAirQualityProvider()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(
                AppSettings(publicIpGeolocationEnabled = false, useCurrentLocationForWeather = true).toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(), airQualityProvider = airQuality,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(parisPlace), applicationScope = backgroundScope,
        )
        refreshRepository(repository)
        val first = repository.snapshot.value.airQuality
        assertThat(first).isNotNull()

        airQuality.failure = IllegalStateException("provider down")
        val refresh = async { repository.refreshCard(com.iloapps.nomaddashboard.core.model.DashboardCardId.WEATHER) }
        advanceUntilIdle()
        refresh.await()

        assertThat(airQuality.requests).hasSize(2)
        assertThat(repository.snapshot.value.airQuality).isEqualTo(first)
    }

    @Test
    fun `a failed air quality refresh never shows another location's value`() = runTest {
        val airQuality = FakeAirQualityProvider()
        val device = MutableDeviceLocationProvider(parisPlace)
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(
                AppSettings(publicIpGeolocationEnabled = false, useCurrentLocationForWeather = true).toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(), airQualityProvider = airQuality,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = device, applicationScope = backgroundScope,
        )
        refreshRepository(repository)
        assertThat(repository.snapshot.value.airQuality).isNotNull()

        device.value = ResolvedVisitedPlace("Madrid", "Madrid", "Spain", "ES", 40.4168, -3.7038)
        airQuality.failure = IllegalStateException("provider down")
        val refresh = async { repository.refreshCard(com.iloapps.nomaddashboard.core.model.DashboardCardId.WEATHER) }
        advanceUntilIdle()
        refresh.await()

        assertThat(repository.snapshot.value.airQuality).isNull()
    }

    @Test
    fun `warm start restores cached air quality only while air quality is active`() = runTest {
        val cache = FakeDashboardSectionCacheDao()
        val snapshot = fakeAirQualitySnapshot(48.9, 2.4)
        cache.upsert(snapshot.toCacheEntity(TestJson))

        val active = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(
                AppSettings(useCurrentLocationForWeather = true).toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(), dashboardSectionCacheDao = cache,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null), applicationScope = backgroundScope,
        )
        active.warmStart()
        assertThat(active.snapshot.value.airQuality).isEqualTo(snapshot)

        val inactive = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(
                AppSettings(useCurrentLocationForWeather = false).toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(), dashboardSectionCacheDao = cache,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null), applicationScope = backgroundScope,
        )
        inactive.warmStart()
        assertThat(inactive.snapshot.value.airQuality).isNull()
    }

    @Test
    fun `turning air quality on fetches it right away`() = runTest {
        val airQuality = FakeAirQualityProvider()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(AppSettings(
                publicIpGeolocationEnabled = false, useCurrentLocationForWeather = true, airQualityEnabled = false).toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(), airQualityProvider = airQuality,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(parisPlace), applicationScope = backgroundScope,
        )
        refreshRepository(repository)
        assertThat(airQuality.requests).isEmpty()

        val update = async { repository.updateSettings { it.copy(airQualityEnabled = true) } }
        advanceUntilIdle()
        update.await()

        assertThat(airQuality.requests).hasSize(1)
        assertThat(repository.snapshot.value.airQuality).isNotNull()
    }

    @Test
    fun `turning device location weather on fetches air quality when it is enabled`() = runTest {
        val airQuality = FakeAirQualityProvider()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(AppSettings(
                publicIpGeolocationEnabled = false, useCurrentLocationForWeather = false, airQualityEnabled = true).toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(), airQualityProvider = airQuality,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(parisPlace), applicationScope = backgroundScope,
        )

        val update = async { repository.updateSettings { it.copy(useCurrentLocationForWeather = true) } }
        advanceUntilIdle()
        update.await()

        assertThat(airQuality.requests).hasSize(1)
    }

    @Test
    fun `changing unrelated settings does not refresh`() = runTest {
        val airQuality = FakeAirQualityProvider()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(AppSettings(
                publicIpGeolocationEnabled = false, useCurrentLocationForWeather = true).toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(), airQualityProvider = airQuality,
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(parisPlace), applicationScope = backgroundScope,
        )

        repository.updateSettings { it.copy(weatherForecastExpanded = false) }
        repository.updateSettings { it.copy(airQualityEnabled = false) }

        assertThat(airQuality.requests).isEmpty()
    }

    @Test
    fun `turning air quality off removes its cached row`() = runTest {
        val cache = FakeDashboardSectionCacheDao()
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(
                AppSettings(publicIpGeolocationEnabled = false, useCurrentLocationForWeather = true).toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(), airQualityProvider = FakeAirQualityProvider(),
            dashboardSectionCacheDao = cache, visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(parisPlace), applicationScope = backgroundScope,
        )
        refreshRepository(repository)
        assertThat(cache.all().map { it.sectionId }).contains(DashboardSectionId.AIR_QUALITY)

        repository.updateSettings { it.copy(useCurrentLocationForWeather = false) }

        assertThat(cache.all().map { it.sectionId }).doesNotContain(DashboardSectionId.AIR_QUALITY)
        assertThat(cache.all().map { it.sectionId }).contains(DashboardSectionId.WEATHER)
    }

    @Test
    fun `turning air quality off clears the published value`() = runTest {
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(
                AppSettings(publicIpGeolocationEnabled = false, useCurrentLocationForWeather = true).toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(), airQualityProvider = FakeAirQualityProvider(),
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(parisPlace), applicationScope = backgroundScope,
        )
        refreshRepository(repository)
        assertThat(repository.snapshot.value.airQuality).isNotNull()

        repository.updateSettings { it.copy(airQualityEnabled = false) }

        assertThat(repository.snapshot.value.airQuality).isNull()
    }

    @Test
    fun `a failed latency probe keeps a still valid reading across refreshes`() = runTest {
        val reader = LatencyScriptedTelemetryReader(
            passive = onlineWifi("Cafe", latency = null),
            full = onlineWifi("Cafe", latency = 40.0, measuredAt = clock.instant()),
        )
        val repository = latencyRepository(reader, AppSettings(), backgroundScope)

        refreshRepository(repository)
        assertThat(repository.snapshot.value.connectivity.latencyMs).isEqualTo(40.0)

        // The next probe fails and the passive read has no latency, yet the 40 ms reading is recent.
        reader.full = onlineWifi("Cafe", latency = null)
        refreshRepository(repository)

        val connectivity = repository.snapshot.value.connectivity
        assertThat(connectivity.latencyMs).isEqualTo(40.0)
        assertThat(connectivity.latencyMeasuredAt).isNotNull()
        assertThat(connectivity.latencyHistoryMs.map { it.value }).containsExactly(40.0)
    }

    @Test
    fun `latency is cleared when the device goes offline and when the network changes`() = runTest {
        val reader = LatencyScriptedTelemetryReader(
            passive = onlineWifi("Cafe", latency = null),
            full = onlineWifi("Cafe", latency = 40.0, measuredAt = clock.instant()),
        )
        val repository = latencyRepository(reader, AppSettings(), backgroundScope)
        refreshRepository(repository)
        assertThat(repository.snapshot.value.connectivity.latencyMs).isEqualTo(40.0)

        reader.passive = onlineWifi("Hotel", latency = null)
        reader.full = onlineWifi("Hotel", latency = null)
        refreshRepository(repository)
        assertThat(repository.snapshot.value.connectivity.latencyMs).isNull()
        assertThat(repository.snapshot.value.connectivity.jitterMs).isNull()

        reader.passive = onlineWifi("Hotel", latency = null)
        reader.full = onlineWifi("Hotel", latency = 30.0, measuredAt = clock.instant())
        refreshRepository(repository)
        assertThat(repository.snapshot.value.connectivity.latencyMs).isEqualTo(30.0)

        reader.passive = ConnectivitySnapshot(internetState = InternetState.OFFLINE)
        refreshRepository(repository)
        assertThat(repository.snapshot.value.connectivity.latencyMs).isNull()
    }

    @Test
    fun `a latency reading older than five minutes is not carried forward`() = runTest {
        val reader = LatencyScriptedTelemetryReader(
            passive = onlineWifi("Cafe", latency = null),
            full = onlineWifi("Cafe", latency = 40.0, measuredAt = clock.instant().minusSeconds(600)),
        )
        val repository = latencyRepository(reader, AppSettings(), backgroundScope)
        refreshRepository(repository)

        reader.full = onlineWifi("Cafe", latency = null)
        refreshRepository(repository)

        assertThat(repository.snapshot.value.connectivity.latencyMs).isNull()
    }

    @Test
    fun `jitter comes from successive latency readings and is unknown with one`() = runTest {
        val reader = LatencyScriptedTelemetryReader(
            passive = onlineWifi("Cafe", latency = null),
            full = onlineWifi("Cafe", latency = 40.0, measuredAt = clock.instant()),
        )
        val repository = latencyRepository(reader, AppSettings(), backgroundScope)

        refreshRepository(repository)
        assertThat(repository.snapshot.value.connectivity.jitterMs).isNull()

        clock.advance(Duration.ofSeconds(10))
        reader.full = onlineWifi("Cafe", latency = 52.0, measuredAt = clock.instant())
        refreshRepository(repository)
        assertThat(repository.snapshot.value.connectivity.jitterMs).isEqualTo(12.0)

        clock.advance(Duration.ofSeconds(10))
        reader.full = onlineWifi("Cafe", latency = 46.0, measuredAt = clock.instant())
        refreshRepository(repository)
        // Mean of |52 - 40| and |46 - 52|.
        assertThat(repository.snapshot.value.connectivity.jitterMs).isEqualTo(9.0)
    }

    @Test
    fun `low data still measures latency but at most once a minute`() = runTest {
        val reader = LatencyScriptedTelemetryReader(
            passive = onlineWifi("Cafe", latency = null),
            full = onlineWifi("Cafe", latency = 99.0),
            probe = 35.0,
        )
        val repository = latencyRepository(
            reader,
            AppSettings(resourceMode = com.iloapps.nomaddashboard.core.model.ResourceMode.LOW_DATA),
            backgroundScope,
        )

        refreshRepository(repository)
        refreshRepository(repository)
        refreshRepository(repository)

        assertThat(reader.fullReads).isEqualTo(0)
        assertThat(reader.probes).isEqualTo(1)
        assertThat(repository.snapshot.value.connectivity.latencyMs).isEqualTo(35.0)
    }

    @Test
    fun `probe if stale reuses a recent reading and updates only latency fields`() = runTest {
        val reader = LatencyScriptedTelemetryReader(
            passive = onlineWifi("Cafe", latency = null),
            full = onlineWifi("Cafe", latency = null),
            probe = 28.0,
        )
        val repository = latencyRepository(reader, AppSettings(), backgroundScope)
        val before = repository.snapshot.value.connectivity

        repository.probeLatencyIfStale()
        repository.probeLatencyIfStale()

        assertThat(reader.probes).isEqualTo(1)
        val after = repository.snapshot.value.connectivity
        assertThat(after.latencyMs).isEqualTo(28.0)
        assertThat(after.latencyMeasuredAt).isNotNull()
        assertThat(after.latencyProbeHost).isEqualTo("1.1.1.1")
        assertThat(after.latencyHistoryMs.map { it.value }).containsExactly(28.0)
        assertThat(
            after.copy(
                latencyMs = null,
                latencyMeasuredAt = null,
                latencyProbeHost = null,
                latencyHistoryMs = before.latencyHistoryMs,
                jitterMs = null,
            ),
        ).isEqualTo(before.copy(latencyMs = null, latencyMeasuredAt = null, latencyProbeHost = null, jitterMs = null))
    }

    @Test
    fun `probe if stale never probes while offline`() = runTest {
        val reader = LatencyScriptedTelemetryReader(
            passive = ConnectivitySnapshot(internetState = InternetState.OFFLINE),
            full = ConnectivitySnapshot(internetState = InternetState.OFFLINE),
            probe = 28.0,
        )
        val repository = latencyRepository(reader, AppSettings(), backgroundScope)

        repository.probeLatencyIfStale()

        assertThat(reader.probes).isEqualTo(0)
        assertThat(repository.snapshot.value.connectivity.latencyMs).isNull()
    }

    @Test
    fun `probe if stale measures again once the reading is older than the allowed age`() = runTest {
        val reader = LatencyScriptedTelemetryReader(
            passive = onlineWifi("Cafe", latency = null),
            full = onlineWifi("Cafe", latency = null),
            probe = 28.0,
        )
        val repository = latencyRepository(reader, AppSettings(), backgroundScope)

        repository.probeLatencyIfStale(Duration.ZERO)
        repository.probeLatencyIfStale(Duration.ZERO)

        assertThat(reader.probes).isEqualTo(2)
    }

    /** The time the latency tests run at; it only moves when a test advances it, so readings never share a millisecond by chance. */
    private val clock = SteppingClock(Instant.parse("2026-10-12T10:00:00Z"))

    private fun latencyRepository(
        reader: TelemetryReader,
        settings: AppSettings,
        scope: CoroutineScope,
    ) = repository(
        settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(settings.toProto())),
        fuelPriceProvider = FakeFuelPriceProvider(),
        visitedHistoryStore = FakeVisitedHistoryStore(),
        visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null),
        telemetryReader = reader,
        metricPointDao = FakeMetricPointDao(),
        applicationScope = scope,
        clock = clock,
    )

    private fun onlineWifi(name: String, latency: Double?, measuredAt: Instant? = null) = ConnectivitySnapshot(
        internetState = InternetState.ONLINE,
        isOnline = true,
        latencyMs = latency,
        latencyMeasuredAt = measuredAt,
        wifiName = name,
    )

    private val parisPlace = ResolvedVisitedPlace(
        city = "Paris",
        region = "Ile-de-France",
        country = "France",
        countryCode = "FR",
        latitude = 48.8566,
        longitude = 2.3522,
    )

    private fun repository(
        settingsDataSource: NomadSettingsDataSource,
        fuelPriceProvider: FuelPriceProvider,
        freeIpApiService: FreeIpApiService = FakeFreeIpApiService(),
        ipifyService: IpifyService = FakeIpifyService(),
        openMeteoService: OpenMeteoService = FakeOpenMeteoService(),
        openMeteoMarineService: OpenMeteoMarineService = FakeOpenMeteoMarineService(),
        airQualityProvider: AirQualityProvider = FakeAirQualityProvider(),
        localPriceLevelProvider: LocalPriceLevelProvider = FakeLocalPriceLevelProvider(),
        localInfoProvider: LocalInfoProvider = FakeLocalInfoProvider(),
        emergencyCareProvider: EmergencyCareProvider = FakeEmergencyCareProvider(),
        providerCredentialStore: ProviderCredentialStore = FakeProviderCredentialStore(
            ProviderCredentialSettings(reliefWebAppName = "NomadDashboardTests"),
        ),
        visitedHistoryStore: FakeVisitedHistoryStore,
        dashboardSectionCacheDao: DashboardSectionCacheDao = FakeDashboardSectionCacheDao(),
        metricPointDao: MetricPointDao = FakeMetricPointDao(),
        visitedDeviceLocationProvider: VisitedDeviceLocationProvider,
        applicationScope: CoroutineScope,
        telemetryReader: TelemetryReader = FakeTelemetryReader(),
        timeTrackingRepository: TimeTrackingRepository = FakeTimeTrackingRepository(),
        smartravellerAdvisoryProvider: SmartravellerAdvisoryProvider = smartravellerProvider(),
        reliefWebSecurityProvider: ReliefWebSecurityProvider = reliefWebProvider(),
        neighborCountryResolver: BundledNeighborCountryResolver = BundledNeighborCountryResolver.fromRecords(
            mapOf("FI" to listOf("SE", "NO"), "DE" to listOf("PL", "FR")),
        ),
        clock: Clock = Clock.systemUTC(),
    ): DefaultNomadDashboardRepository =
        DefaultNomadDashboardRepository(
            settingsDataSource = settingsDataSource,
            telemetryReader = telemetryReader,
            freeIpApiService = freeIpApiService,
            ipifyService = ipifyService,
            openMeteoService = openMeteoService,
            openMeteoMarineService = openMeteoMarineService,
            airQualityProvider = airQualityProvider,
            localPriceLevelProvider = localPriceLevelProvider,
            localInfoProvider = localInfoProvider,
            fuelPriceProvider = fuelPriceProvider,
            emergencyCareProvider = emergencyCareProvider,
            timeTrackingRepository = timeTrackingRepository,
            visitedHistoryStore = visitedHistoryStore,
            dashboardSectionCacheDao = dashboardSectionCacheDao,
            metricPointDao = metricPointDao,
            visitedDeviceLocationProvider = visitedDeviceLocationProvider,
            providerCredentialStore = providerCredentialStore,
            smartravellerAdvisoryProvider = smartravellerAdvisoryProvider,
            reliefWebSecurityProvider = reliefWebSecurityProvider,
            neighborCountryResolver = neighborCountryResolver,
            json = TestJson,
            applicationScope = applicationScope,
            ioDispatcher = applicationScope.coroutineContext[kotlin.coroutines.ContinuationInterceptor] as kotlinx.coroutines.CoroutineDispatcher,
            clock = clock,
        )

    private fun smartravellerProvider(
    ): SmartravellerAdvisoryProvider =
        SmartravellerAdvisoryProvider(
            service = object : SmartravellerService {
                override suspend fun destinations(): Response<ResponseBody> =
                    Response.success(
                        """
                        <html>
                          <body>
                            <table>
                              <tr>
                                <th>Destination</th>
                                <th>Region</th>
                                <th>Overall Advice Level</th>
                                <th>Updated</th>
                              </tr>
                              <tr>
                                <td><a href="/destinations/finland">Finland</a></td>
                                <td>Europe</td>
                                <td>Exercise normal safety precautions</td>
                                <td>07 Apr 2026</td>
                              </tr>
                              <tr>
                                <td><a href="/destinations/germany">Germany</a></td>
                                <td>Europe</td>
                                <td>Exercise normal safety precautions</td>
                                <td>07 Apr 2026</td>
                              </tr>
                              <tr>
                                <td><a href="/destinations/france">France</a></td>
                                <td>Europe</td>
                                <td>Exercise a high degree of caution</td>
                                <td>07 Apr 2026</td>
                              </tr>
                              <tr>
                                <td><a href="/destinations/poland">Poland</a></td>
                                <td>Europe</td>
                                <td>Exercise normal safety precautions</td>
                                <td>07 Apr 2026</td>
                              </tr>
                            </table>
                          </body>
                        </html>
                        """.trimIndent().toResponseBody("text/html".toMediaType()),
                    )

                override suspend fun destinationsExport(): Response<ResponseBody> =
                    Response.error(404, "{}".toResponseBody("application/json".toMediaType()))

                override suspend fun destinationPage(url: String): Response<ResponseBody> =
                    Response.success(
                        """
                        <html>
                          <body>
                            <p>Exercise a high degree of caution in France due to the threat of terrorism.</p>
                          </body>
                        </html>
                        """.trimIndent().toResponseBody("text/html".toMediaType()),
                    )
            },
            countryNameResolver = CountryNameResolver(),
            json = TestJson,
            browserFetcher = object : SmartravellerBrowserFetcher {
                override suspend fun destinationsHtml(): String = error("browser fallback should not be used")
            },
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

private suspend fun TestScope.refreshRepository(
    repository: DefaultNomadDashboardRepository,
) {
    val refresh = async { repository.refresh() }
    advanceUntilIdle()
    refresh.await()
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

private class FakeTelemetryReader(
    private val connectivitySnapshots: List<ConnectivitySnapshot> = listOf(
        ConnectivitySnapshot(
            internetState = InternetState.ONLINE,
            isOnline = true,
            wifiName = "Test Wi-Fi",
        ),
    ),
    private val powerSnapshots: List<PowerSnapshot> = listOf(
        PowerSnapshot(
            batteryPercent = 80,
            charging = false,
            statusLabel = "On Battery",
            batteryHealthSummary = "Healthy",
            powerSourceLabel = "Battery",
        ),
    ),
) : TelemetryReader {
    private var connectivityIndex = 0
    private var powerIndex = 0

    override suspend fun connectivity(previousTrafficSample: TrafficSample?): Pair<ConnectivitySnapshot, TrafficSample> =
        connectivitySnapshots.getOrElse((connectivityIndex - 1).coerceAtLeast(0)) { connectivitySnapshots.last() } to
            TrafficSample(0, 0, connectivityIndex.toLong())

    override suspend fun passiveConnectivity(previousTrafficSample: TrafficSample?): Pair<ConnectivitySnapshot, TrafficSample> {
        val snapshot = connectivitySnapshots.getOrElse(connectivityIndex) { connectivitySnapshots.last() }
        connectivityIndex += 1
        return snapshot to TrafficSample(
            rxBytes = 0,
            txBytes = 0,
            capturedAtMillis = connectivityIndex.toLong(),
        )
    }

    override suspend fun power(): PowerSnapshot {
        val snapshot = powerSnapshots.getOrElse(powerIndex) { powerSnapshots.last() }
        powerIndex += 1
        return snapshot
    }
}

private class FakeMetricPointDao : MetricPointDao {
    private val points = mutableListOf<MetricPointEntity>()
    private var nextId = 1L

    override fun observeByKind(kind: String): Flow<List<MetricPointEntity>> =
        MutableStateFlow(recentByKindSync(kind = kind, limit = Int.MAX_VALUE))

    override suspend fun recentByKind(kind: String, limit: Int): List<MetricPointEntity> =
        recentByKindSync(kind = kind, limit = limit)

    override suspend fun upsert(point: MetricPointEntity) {
        if (point.id == 0L) {
            insert(point)
            return
        }
        points.removeAll { it.id == point.id }
        points += point
    }

    override suspend fun insert(point: MetricPointEntity): Long {
        val storedPoint = point.copy(id = nextId++)
        points += storedPoint
        return storedPoint.id
    }

    override suspend fun trimToLatest(kind: String, keepCount: Int) {
        val retainedIds = recentByKindSync(kind = kind, limit = keepCount).map(MetricPointEntity::id).toSet()
        points.removeAll { it.kind == kind && it.id !in retainedIds }
    }

    private fun recentByKindSync(kind: String, limit: Int): List<MetricPointEntity> =
        points.filter { it.kind == kind }
            .sortedByDescending(MetricPointEntity::timestampEpochMillis)
            .take(limit)
}

private class FakeDashboardSectionCacheDao : DashboardSectionCacheDao {
    private val entries = linkedMapOf<String, DashboardSectionCacheEntity>()

    override suspend fun all(): List<DashboardSectionCacheEntity> = entries.values.toList()

    override suspend fun upsert(entity: DashboardSectionCacheEntity) {
        entries[entity.sectionId] = entity
    }

    override suspend fun delete(sectionId: String) {
        entries.remove(sectionId)
    }
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
            timeZones = listOf("Europe/Helsinki"),
        )

    override suspend fun lookup(ipAddress: String): FreeIpApiResponse =
        lookupMe().copy(ipAddress = ipAddress)
}

private class SequenceFreeIpApiService(
    private val responses: List<Result<FreeIpApiResponse>>,
    private val responsesByAddress: Map<String, Result<FreeIpApiResponse>> = emptyMap(),
) : FreeIpApiService {
    private var index = 0

    override suspend fun lookupMe(): FreeIpApiResponse {
        val response = responses.getOrElse(index) { responses.last() }
        index += 1
        return response.getOrThrow()
    }

    override suspend fun lookup(ipAddress: String): FreeIpApiResponse =
        responsesByAddress[ipAddress]?.getOrThrow()
            ?: error("No by-address response configured for $ipAddress")
}

private class FakeIpifyService(
    private val ip: String? = "198.51.100.12",
) : IpifyService {
    override suspend fun lookupIp(format: String): IpifyResponse = IpifyResponse(ip = ip)
}

private class FakeOpenMeteoService(
    private val response: OpenMeteoResponse = OpenMeteoResponse(),
) : OpenMeteoService {
    data class Request(
        val latitude: Double,
        val longitude: Double,
        val current: String,
        val hourly: String,
        val daily: String,
        val forecastDays: Int,
        val timezone: String,
    )

    val requests = mutableListOf<Request>()

    override suspend fun forecast(
        latitude: Double,
        longitude: Double,
        current: String,
        hourly: String,
        daily: String,
        forecastDays: Int,
        timezone: String,
    ): OpenMeteoResponse {
        requests += Request(
            latitude = latitude,
            longitude = longitude,
            current = current,
            hourly = hourly,
            daily = daily,
            forecastDays = forecastDays,
            timezone = timezone,
        )
        return response
    }
}

private fun fakeAirQualitySnapshot(latitude: Double, longitude: Double) = AirQualitySnapshot(
    latitude = latitude,
    longitude = longitude,
    sourceName = "Open-Meteo",
    europeanAqi = 42,
    mainPollutant = null,
    pm2_5 = null,
    pm10 = null,
    ozone = null,
    nitrogenDioxide = null,
    forecastSlots = emptyList(),
    fetchedAt = Instant.ofEpochMilli(1_700_000_000_000),
)

private class FakeAirQualityProvider : AirQualityProvider {
    val requests = mutableListOf<Pair<Double, Double>>()
    var failure: Throwable? = null

    override suspend fun airQuality(latitude: Double, longitude: Double): AirQualitySnapshot {
        requests += latitude to longitude
        failure?.let { throw it }
        val grid = com.iloapps.nomaddashboard.core.travel.environment.AirQualityGridPoint.privacyRounded(latitude, longitude)!!
        return fakeAirQualitySnapshot(grid.latitude, grid.longitude).copy(fetchedAt = Instant.now())
    }
}

private class MutableDeviceLocationProvider(var value: ResolvedVisitedPlace?) : VisitedDeviceLocationProvider {
    override fun hasLocationPermission(): Boolean = true

    override suspend fun currentLocation(): DeviceLocationSnapshot =
        value?.let { place ->
            DeviceLocationSnapshot(
                status = DeviceLocationResolutionStatus.PLACE_RESOLVED,
                latitude = place.latitude,
                longitude = place.longitude,
                city = place.city,
                region = place.region,
                country = place.country,
                countryCode = place.countryCode,
            )
        } ?: DeviceLocationSnapshot(status = DeviceLocationResolutionStatus.NO_FIX)
}

private class SteppingClock(private var instant: Instant) : Clock() {
    fun advance(duration: Duration) {
        instant = instant.plus(duration)
    }

    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = instant
}

private class LatencyScriptedTelemetryReader(
    var passive: ConnectivitySnapshot,
    var full: ConnectivitySnapshot,
    var probe: Double? = null,
) : TelemetryReader {
    var probes = 0
    var fullReads = 0

    override suspend fun connectivity(previousTrafficSample: TrafficSample?): Pair<ConnectivitySnapshot, TrafficSample> {
        fullReads += 1
        return full to TrafficSample(0, 0, fullReads.toLong())
    }

    override suspend fun passiveConnectivity(previousTrafficSample: TrafficSample?): Pair<ConnectivitySnapshot, TrafficSample> =
        passive to TrafficSample(0, 0, 0)

    override suspend fun latencyProbe(): LatencyProbeResult? {
        probes += 1
        return probe?.let { LatencyProbeResult(it, "1.1.1.1") }
    }

    override suspend fun power(): PowerSnapshot = PowerSnapshot(batteryPercent = 80, statusLabel = "On Battery")
}

private class FakeOpenMeteoMarineService(
    private val response: OpenMeteoMarineResponse = OpenMeteoMarineResponse(),
) : OpenMeteoMarineService {
    override suspend fun forecast(
        latitude: Double,
        longitude: Double,
        hourly: String,
        forecastDays: Int,
        timezone: String,
    ): OpenMeteoMarineResponse = response
}

private class FakeVisitedDeviceLocationProvider(
    private val value: ResolvedVisitedPlace?,
    private val hasLocationPermission: Boolean = true,
    private val coordinatesOnly: Pair<Double, Double>? = null,
    private val delayMillis: Long = 0,
) : VisitedDeviceLocationProvider {
    override fun hasLocationPermission(): Boolean = hasLocationPermission

    override suspend fun currentLocation(): DeviceLocationSnapshot {
        if (delayMillis > 0) {
            delay(delayMillis)
        }
        if (hasLocationPermission.not()) {
            return DeviceLocationSnapshot(status = DeviceLocationResolutionStatus.PERMISSION_MISSING)
        }
        value?.let { place ->
            return DeviceLocationSnapshot(
                status = DeviceLocationResolutionStatus.PLACE_RESOLVED,
                latitude = place.latitude,
                longitude = place.longitude,
                city = place.city,
                region = place.region,
                country = place.country,
                countryCode = place.countryCode,
            )
        }
        coordinatesOnly?.let { (latitude, longitude) ->
            return DeviceLocationSnapshot(
                status = DeviceLocationResolutionStatus.COORDINATES_ONLY,
                latitude = latitude,
                longitude = longitude,
            )
        }
        return DeviceLocationSnapshot(status = DeviceLocationResolutionStatus.NO_FIX)
    }
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

private class FakeLocalPriceLevelProvider : LocalPriceLevelProvider {
    val requests = mutableListOf<LocalPriceLevelRequest>()
    val tokens = mutableListOf<String?>()
    var clearUsCacheCount = 0

    override suspend fun prices(
        request: LocalPriceLevelRequest,
        hudUserApiToken: String?,
    ): LocalPriceLevelSnapshot {
        requests += request
        tokens += hudUserApiToken

        val countryCode = request.countryCode
        return when {
            countryCode == null -> LocalPriceLevelSnapshot(
                status = LocalPriceLevelStatus.LOCATION_REQUIRED,
                sources = listOf("Eurostat", "HUD USER", "US Census Geocoder"),
                detail = "Allow current location or external IP location to estimate the local price level.",
            )

            countryCode == "US" && hudUserApiToken.isNullOrBlank() -> LocalPriceLevelSnapshot(
                status = LocalPriceLevelStatus.CONFIGURATION_REQUIRED,
                countryCode = "US",
                countryName = request.countryName ?: "United States",
                sources = listOf("HUD USER", "US Census Geocoder"),
                detail = "Add a HUD USER API token in Settings to show the US 1-bedroom rent benchmark.",
            )

            countryCode == "US" -> LocalPriceLevelSnapshot(
                status = LocalPriceLevelStatus.PARTIAL,
                summaryBand = LocalPriceSummaryBand.LIMITED,
                countryCode = "US",
                countryName = request.countryName ?: "United States",
                rows = listOf(
                    LocalPriceIndicatorRow(
                        kind = LocalPriceIndicatorKind.RENT_ONE_BEDROOM,
                        value = "${'$'}1,900/mo",
                        detail = "Metro benchmark · Seattle metro · 2024",
                        precision = LocalPricePrecision.METRO_BENCHMARK,
                        source = "HUD USER",
                    ),
                ),
                sources = listOf("HUD USER", "US Census Geocoder"),
                fetchedAt = Instant.parse("2026-04-08T08:00:00Z"),
                detail = "US v1 currently shows the HUD 1-bedroom rent benchmark only.",
            )

            countryCode in setOf("FI", "EL", "ES", "FR", "DE") -> LocalPriceLevelSnapshot(
                status = LocalPriceLevelStatus.READY,
                summaryBand = LocalPriceSummaryBand.MEDIUM,
                countryCode = countryCode,
                countryName = request.countryName,
                rows = listOf(
                    LocalPriceIndicatorRow(
                        kind = LocalPriceIndicatorKind.MEAL_OUT,
                        value = "Moderate",
                        detail = "4% below EU average · Country fallback · 2024",
                        precision = LocalPricePrecision.COUNTRY_FALLBACK,
                        source = "Eurostat",
                    ),
                    LocalPriceIndicatorRow(
                        kind = LocalPriceIndicatorKind.GROCERIES,
                        value = "Moderate",
                        detail = "2% below EU average · Country fallback · 2024",
                        precision = LocalPricePrecision.COUNTRY_FALLBACK,
                        source = "Eurostat",
                    ),
                    LocalPriceIndicatorRow(
                        kind = LocalPriceIndicatorKind.OVERALL,
                        value = "Moderate",
                        detail = "1% above EU average · Country fallback · 2024",
                        precision = LocalPricePrecision.COUNTRY_FALLBACK,
                        source = "Eurostat",
                    ),
                ),
                sources = listOf("Eurostat"),
                fetchedAt = Instant.parse("2026-04-08T08:00:00Z"),
                detail = "Meal out and groceries use country-level Eurostat price indices.",
            )

            else -> LocalPriceLevelSnapshot(
                status = LocalPriceLevelStatus.UNSUPPORTED,
                countryCode = countryCode,
                countryName = request.countryName,
                sources = listOf("Eurostat", "HUD USER", "US Census Geocoder"),
                fetchedAt = Instant.parse("2026-04-08T08:00:00Z"),
                detail = "Local price level is only supported in Europe and the United States right now.",
            )
        }
    }

    override suspend fun clearUsCache() {
        clearUsCacheCount += 1
    }
}

private class FakeLocalInfoProvider : LocalInfoProvider {
    val requests = mutableListOf<LocalInfoRequest>()
    val tokens = mutableListOf<String?>()

    override suspend fun localInfo(
        request: LocalInfoRequest,
        hudUserApiToken: String?,
    ): com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot {
        requests += request
        tokens += hudUserApiToken

        val countryCode = request.countryCode
        return when {
            countryCode == null -> com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot(
                status = LocalInfoStatus.LOCATION_REQUIRED,
                detail = "Allow location or enable IP-based location to show Local Info.",
            )

            countryCode == "US" && hudUserApiToken.isNullOrBlank() -> com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot(
                status = LocalInfoStatus.PARTIAL,
                locality = request.locality,
                region = request.region,
                countryCode = "US",
                countryName = request.countryName ?: "United States",
                localPriceLevel = FakeLocalPriceLevelProvider().prices(
                    request = LocalPriceLevelRequest(
                        latitude = request.latitude,
                        longitude = request.longitude,
                        countryCode = "US",
                        countryName = request.countryName ?: "United States",
                        locality = request.locality,
                    ),
                    hudUserApiToken = null,
                ),
            )

            countryCode == "US" -> com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot(
                status = LocalInfoStatus.PARTIAL,
                locality = request.locality,
                region = request.region,
                countryCode = "US",
                countryName = request.countryName ?: "United States",
                localPriceLevel = FakeLocalPriceLevelProvider().prices(
                    request = LocalPriceLevelRequest(
                        latitude = request.latitude,
                        longitude = request.longitude,
                        countryCode = "US",
                        countryName = request.countryName ?: "United States",
                        locality = request.locality,
                    ),
                    hudUserApiToken = hudUserApiToken,
                ),
            )

            countryCode in setOf("FI", "EL", "ES", "FR", "DE") -> com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot(
                status = LocalInfoStatus.READY,
                locality = request.locality,
                region = request.region,
                countryCode = countryCode,
                countryName = request.countryName,
                localPriceLevel = FakeLocalPriceLevelProvider().prices(
                    request = LocalPriceLevelRequest(
                        latitude = request.latitude,
                        longitude = request.longitude,
                        countryCode = countryCode,
                        countryName = request.countryName,
                        locality = request.locality,
                    ),
                    hudUserApiToken = hudUserApiToken,
                ),
            )

            else -> com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot(
                status = LocalInfoStatus.UNSUPPORTED,
                locality = request.locality,
                region = request.region,
                countryCode = countryCode,
                countryName = request.countryName,
                detail = "Local context is available, but some Local Info sources do not cover this location yet.",
            )
        }
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
    override val pendingEntries: Flow<List<TimeTrackingRecord>> = MutableStateFlow(emptyList())
    override val activeEntry: Flow<TimeTrackingRecord?> = MutableStateFlow(active)
    override val report: Flow<TimeTrackingReportSnapshot> = MutableStateFlow(TimeTrackingReportSnapshot())

    override suspend fun currentActiveEntry(): TimeTrackingRecord? = activeEntry.first()

    override suspend fun syncTracking(now: Instant) = Unit

    override suspend fun createProject(name: String): CreateProjectResult =
        CreateProjectResult.Created(
            TimeTrackingProject(id = UUID.randomUUID(), name = name.trim()),
        )

    override suspend fun startTracking(): StartTrackingResult = StartTrackingResult.Started

    override suspend fun stopTracking(): StopTrackingResult = StopTrackingResult.NotTracking

    override suspend fun reportInterruption(now: Instant): ReportInterruptionResult =
        ReportInterruptionResult.Recorded

    override suspend fun allocateTrackedTime(projectId: UUID): AllocateTrackedTimeResult =
        AllocateTrackedTimeResult.NothingToAllocate

    override suspend fun updateEntry(
        entryId: UUID,
        startAt: Instant,
        endAt: Instant,
    ): UpdateTimeTrackingEntryResult = UpdateTimeTrackingEntryResult.Updated
}

private val TestJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private val JsonMediaType = "application/json".toMediaType()

private class FakeVisitedHistoryStore : VisitedHistoryStore {
    private val _visitedPlaces = MutableStateFlow<List<VisitedPlace>>(emptyList())
    private val _visitedCountryDays = MutableStateFlow<List<VisitedCountryDay>>(emptyList())
    private val _visitedPlaceEvents = MutableStateFlow<List<VisitedPlaceEvent>>(emptyList())
    val observations = mutableListOf<VisitedObservation>()

    override val visitedPlaces: Flow<List<VisitedPlace>> = _visitedPlaces
    override val visitedCountryDays: Flow<List<VisitedCountryDay>> = _visitedCountryDays
    override val visitedPlaceEvents: Flow<List<VisitedPlaceEvent>> = _visitedPlaceEvents

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
        _visitedPlaceEvents.value = observations.mapIndexed { index, it ->
            VisitedPlaceEvent(
                id = "event-$index",
                city = it.city,
                region = it.region,
                country = it.country,
                countryCode = it.countryCode,
                latitude = it.latitude,
                longitude = it.longitude,
                source = it.source,
                firstObservedAt = it.observedAt,
                lastObservedAt = it.observedAt,
                observedDay = LocalDate.ofInstant(it.observedAt, java.time.ZoneId.systemDefault()),
            )
        }
    }

    override suspend fun clearHistory() {
        observations.clear()
        _visitedPlaces.value = emptyList()
        _visitedCountryDays.value = emptyList()
        _visitedPlaceEvents.value = emptyList()
    }
}
