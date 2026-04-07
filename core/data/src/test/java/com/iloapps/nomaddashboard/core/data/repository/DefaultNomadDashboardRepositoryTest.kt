package com.iloapps.nomaddashboard.core.data.repository

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
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import com.iloapps.nomaddashboard.core.network.api.FreeIpApiService
import com.iloapps.nomaddashboard.core.network.api.OpenMeteoService
import com.iloapps.nomaddashboard.core.network.model.FreeIpApiResponse
import com.iloapps.nomaddashboard.core.network.model.OpenMeteoResponse
import java.time.LocalDate
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

    private fun repository(
        settingsDataSource: NomadSettingsDataSource,
        visitedHistoryStore: FakeVisitedHistoryStore,
        visitedDeviceLocationProvider: VisitedDeviceLocationProvider,
        applicationScope: CoroutineScope,
    ): DefaultNomadDashboardRepository =
        DefaultNomadDashboardRepository(
            settingsDataSource = settingsDataSource,
            telemetryReader = FakeTelemetryReader(),
            freeIpApiService = FakeFreeIpApiService(),
            openMeteoService = FakeOpenMeteoService(),
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
