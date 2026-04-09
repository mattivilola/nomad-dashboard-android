package com.iloapps.nomaddashboard.core.data.repository

import androidx.datastore.core.DataStore
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
import com.iloapps.nomaddashboard.core.data.location.ResolvedVisitedPlace
import com.iloapps.nomaddashboard.core.data.location.VisitedDeviceLocationProvider
import com.iloapps.nomaddashboard.core.data.localinfo.LocalInfoProvider
import com.iloapps.nomaddashboard.core.data.localinfo.LocalInfoRequest
import com.iloapps.nomaddashboard.core.data.localprice.LocalPriceLevelProvider
import com.iloapps.nomaddashboard.core.data.localprice.LocalPriceLevelRequest
import com.iloapps.nomaddashboard.core.data.monitor.TelemetryReader
import com.iloapps.nomaddashboard.core.data.monitor.TrafficSample
import com.iloapps.nomaddashboard.core.data.travelalerts.BundledNeighborCountryResolver
import com.iloapps.nomaddashboard.core.data.travelalerts.CountryNameResolver
import com.iloapps.nomaddashboard.core.data.travelalerts.ReliefWebSecurityProvider
import com.iloapps.nomaddashboard.core.data.travelalerts.SmartravellerAdvisoryProvider
import com.iloapps.nomaddashboard.core.data.travelalerts.SmartravellerBrowserFetcher
import com.iloapps.nomaddashboard.core.data.visited.VisitedHistoryStore
import com.iloapps.nomaddashboard.core.data.visited.VisitedObservation
import com.iloapps.nomaddashboard.core.database.dao.MetricPointDao
import com.iloapps.nomaddashboard.core.database.entity.MetricPointEntity
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
import com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorKind
import com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorRow
import com.iloapps.nomaddashboard.core.model.LocalPriceLevelSnapshot
import com.iloapps.nomaddashboard.core.model.LocalPriceLevelStatus
import com.iloapps.nomaddashboard.core.model.LocalPricePrecision
import com.iloapps.nomaddashboard.core.model.LocalPriceSummaryBand
import com.iloapps.nomaddashboard.core.model.LocalInfoStatus
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.model.ProviderCredentialSettings
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalStatus
import com.iloapps.nomaddashboard.core.model.TravelAlertUnavailableReason
import com.iloapps.nomaddashboard.core.model.TimeTrackingEntry
import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
import com.iloapps.nomaddashboard.core.model.TimeTrackingReportSnapshot
import com.iloapps.nomaddashboard.core.model.TimeTrackingRecord
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace
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
import com.iloapps.nomaddashboard.core.network.model.OpenMeteoMarineResponse
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

        repository.refresh()
        advanceUntilIdle()

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

        repository.refresh()
        advanceUntilIdle()
        repository.refresh()
        advanceUntilIdle()

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

        repository.refresh()
        advanceUntilIdle()

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

        repository.refresh()
        advanceUntilIdle()

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

        repository.refresh()
        advanceUntilIdle()

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

        repository.refresh()
        advanceUntilIdle()

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

        repository.refresh()
        advanceUntilIdle()

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

        repository.refresh()
        advanceUntilIdle()

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

        repository.refresh()
        advanceUntilIdle()

        val request = openMeteoService.requests.first { it.current.contains("temperature_2m") }
        val snapshot = repository.snapshot.first { it.lastRefresh != null }.weather

        assertThat(request.latitude).isWithin(0.001).of(48.8566)
        assertThat(request.longitude).isWithin(0.001).of(2.3522)
        assertThat(snapshot.currentTemperatureCelsius).isWithin(0.001).of(19.4)
        assertThat(snapshot.summary).isEqualTo("Today tops out at 21°C")
    }

    @Test
    fun `refresh retains connectivity history and defaults missing throughput to zero`() = runTest {
        val repository = repository(
            settingsDataSource = NomadSettingsDataSource(FakeAppSettingsDataStore(AppSettings().toProto())),
            fuelPriceProvider = FakeFuelPriceProvider(),
            visitedHistoryStore = FakeVisitedHistoryStore(),
            visitedDeviceLocationProvider = FakeVisitedDeviceLocationProvider(null),
            telemetryReader = FakeTelemetryReader(
                connectivitySnapshots = listOf(
                    ConnectivitySnapshot(
                        internetState = "Online",
                        isOnline = true,
                        latencyMs = 34.0,
                        downloadMbps = null,
                        uploadMbps = null,
                        wifiName = "Test Wi-Fi",
                    ),
                    ConnectivitySnapshot(
                        internetState = "Online",
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

        repository.refresh()
        advanceUntilIdle()
        repository.refresh()
        advanceUntilIdle()

        val connectivity = repository.snapshot.first { it.lastRefresh != null }.connectivity

        assertThat(connectivity.downloadHistoryMbps.map { it.value }).containsExactly(0.0, 86.4).inOrder()
        assertThat(connectivity.uploadHistoryMbps.map { it.value }).containsExactly(0.0, 18.2).inOrder()
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

        repository.refresh()
        advanceUntilIdle()
        repository.refresh()
        advanceUntilIdle()

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

        repository.refresh()
        advanceUntilIdle()

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

        repository.refresh()
        advanceUntilIdle()

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
        freeIpApiService: FreeIpApiService = FakeFreeIpApiService(),
        ipifyService: IpifyService = FakeIpifyService(),
        openMeteoService: OpenMeteoService = FakeOpenMeteoService(),
        openMeteoMarineService: OpenMeteoMarineService = FakeOpenMeteoMarineService(),
        localPriceLevelProvider: LocalPriceLevelProvider = FakeLocalPriceLevelProvider(),
        localInfoProvider: LocalInfoProvider = FakeLocalInfoProvider(),
        emergencyCareProvider: EmergencyCareProvider = FakeEmergencyCareProvider(),
        providerCredentialStore: ProviderCredentialStore = FakeProviderCredentialStore(
            ProviderCredentialSettings(reliefWebAppName = "NomadDashboardTests"),
        ),
        visitedHistoryStore: FakeVisitedHistoryStore,
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
    ): DefaultNomadDashboardRepository =
        DefaultNomadDashboardRepository(
            settingsDataSource = settingsDataSource,
            telemetryReader = telemetryReader,
            freeIpApiService = freeIpApiService,
            ipifyService = ipifyService,
            openMeteoService = openMeteoService,
            openMeteoMarineService = openMeteoMarineService,
            localPriceLevelProvider = localPriceLevelProvider,
            localInfoProvider = localInfoProvider,
            fuelPriceProvider = fuelPriceProvider,
            emergencyCareProvider = emergencyCareProvider,
            timeTrackingRepository = timeTrackingRepository,
            visitedHistoryStore = visitedHistoryStore,
            metricPointDao = metricPointDao,
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
            internetState = "Online",
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

    override suspend fun connectivity(previousTrafficSample: TrafficSample?): Pair<ConnectivitySnapshot, TrafficSample> {
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
    override suspend fun lookupIp(): IpifyResponse = IpifyResponse(ip = ip)
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
