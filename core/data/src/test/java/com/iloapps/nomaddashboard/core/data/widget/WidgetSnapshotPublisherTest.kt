package com.iloapps.nomaddashboard.core.data.widget

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.data.insights.DashboardInsights
import com.iloapps.nomaddashboard.core.data.insights.DashboardInsightsRepository
import com.iloapps.nomaddashboard.core.data.repository.NomadDashboardRepository
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.InternetState
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.model.ProviderCredentialSettings
import com.iloapps.nomaddashboard.core.model.SurfSpotConfiguration
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedPlaceEvent
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpItem
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpKind
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpSeverity
import com.iloapps.nomaddashboard.core.travel.health.DashboardHealthEvaluator
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetSnapshotPublisherTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val start = Instant.parse("2026-10-12T12:00:00Z")
    private val clock = MutableClock(start)
    private val updater = CountingUpdater()

    private fun TestScope.store() = WidgetSnapshotStore(File(folder.root, "widgets"), StandardTestDispatcher(testScheduler))

    private fun TestScope.publisher(
        store: WidgetSnapshotStore = store(),
        insights: DashboardInsightsRepository = FakeInsights(MutableSharedFlow()),
        settings: Flow<AppSettings> = MutableStateFlow(AppSettings()),
        scope: kotlinx.coroutines.CoroutineScope = backgroundScope,
    ) = WidgetSnapshotPublisher(insights, FakeDashboard(settings), store, updater, clock, scope)

    private fun snapshot(
        liveReadings: Boolean = true,
        battery: Int = 80,
        publicIp: String? = "203.0.113.42",
    ) = DashboardSnapshot(
        localSignalsAt = start.takeIf { liveReadings },
        connectivity = ConnectivitySnapshot(
            internetState = InternetState.ONLINE,
            isOnline = true,
            latencyMs = 24.0,
            latencyMeasuredAt = start,
        ),
        power = PowerSnapshot(batteryPercent = battery, statusLabel = "On Battery", minutesRemaining = 300),
        travelContext = TravelContextSnapshot(
            publicIp = publicIp,
            city = "Rabat",
            countryCode = "MA",
            latitude = 34.0209,
            longitude = -6.8416,
            deviceCountryCode = "MA",
            deviceLatitude = 34.0209,
            deviceLongitude = -6.8416,
        ),
    )

    private fun insights(
        snapshot: DashboardSnapshot = snapshot(),
        headsUp: List<HeadsUpItem> = emptyList(),
    ) = DashboardInsights(
        snapshot = snapshot,
        headsUp = headsUp,
        health = DashboardHealthEvaluator.evaluate(snapshot.connectivity, snapshot.power),
        statusLine = null,
        arrival = null,
        homeTimeZoneId = null,
        zone = ZoneId.of("UTC"),
        evaluatedAt = clock.instant(),
    )

    private fun wind() = HeadsUpItem(
        kind = HeadsUpKind.STRONG_WIND,
        severity = HeadsUpSeverity.CAUTION,
        title = "Wind up to 45 km/h",
        statusText = "Wind 45",
        detail = null,
        relatedCard = DashboardCardId.WEATHER,
    )

    @Test
    fun `a placeholder dashboard with no local readings is not published`() = runTest {
        val store = store()

        val published = publisher(store).publish(insights(snapshot(liveReadings = false)), AppSettings())

        assertThat(published).isFalse()
        assertThat(store.load()).isNull()
        assertThat(updater.calls).isEqualTo(0)
    }

    @Test
    fun `the first snapshot is saved and the widgets are redrawn`() = runTest {
        val store = store()

        val published = publisher(store).publish(insights(headsUp = listOf(wind())), AppSettings())
        val saved = store.load()

        assertThat(published).isTrue()
        assertThat(updater.calls).isEqualTo(1)
        assertThat(saved?.placeName).isEqualTo("Rabat, Morocco")
        assertThat(saved?.emergency?.generalNumber).isEqualTo("19")
        assertThat(saved?.headsUp?.map { it.title }).containsExactly("Wind up to 45 km/h")
        assertThat(saved?.savedAt).isEqualTo(start)
    }

    @Test
    fun `the saved file holds no ip address and no coordinates`() = runTest {
        val directory = File(folder.root, "widgets")

        publisher().publish(insights(), AppSettings(citizenshipCountryCodes = listOf("FI")))
        val text = File(directory, WidgetSnapshotStore.FILE_NAME).readText()

        assertThat(text).doesNotContain("203.0.113.42")
        assertThat(text).doesNotContain("34.02")
        assertThat(text).doesNotContain("6.84")
        assertThat(text).doesNotContain("\"FI\"")
    }

    @Test
    fun `unchanged content is not saved again until the heartbeat`() = runTest {
        val publisher = publisher()
        publisher.publish(insights(), AppSettings())

        clock.advance(Duration.ofMinutes(10))
        assertThat(publisher.publish(insights(), AppSettings())).isFalse()

        clock.advance(Duration.ofMinutes(20))
        assertThat(publisher.publish(insights(), AppSettings())).isTrue()
        assertThat(updater.calls).isEqualTo(2)
    }

    @Test
    fun `changed content is saved after the minimum interval`() = runTest {
        val publisher = publisher()
        publisher.publish(insights(), AppSettings())

        clock.advance(Duration.ofSeconds(30))
        assertThat(publisher.publish(insights(headsUp = listOf(wind())), AppSettings())).isFalse()

        clock.advance(Duration.ofSeconds(31))
        assertThat(publisher.publish(insights(headsUp = listOf(wind())), AppSettings())).isTrue()
    }

    @Test
    fun `the saved snapshot is the reference after a restart`() = runTest {
        val store = store()
        publisher(store).publish(insights(), AppSettings())
        val flow = MutableSharedFlow<DashboardInsights>(replay = 1)
        flow.tryEmit(insights())

        publisher(store, insights = FakeInsights(flow)).start()
        runCurrent()

        // Same content as the saved file and no time has passed: nothing to write.
        assertThat(updater.calls).isEqualTo(1)
    }

    @Test
    fun `start publishes from the insights flow and throttles bursts`() = runTest {
        val flow = MutableSharedFlow<DashboardInsights>(replay = 1)
        val store = store()
        val publisher = publisher(store, insights = FakeInsights(flow))

        publisher.start()
        publisher.start()
        runCurrent()
        flow.emit(insights())
        runCurrent()
        assertThat(updater.calls).isEqualTo(1)

        clock.advance(Duration.ofMinutes(2))
        flow.emit(insights(headsUp = listOf(wind())))
        runCurrent()
        // Still inside the five second evaluation window of the first publish.
        assertThat(updater.calls).isEqualTo(1)

        advanceTimeBy(5_001)
        runCurrent()
        assertThat(updater.calls).isEqualTo(2)
        assertThat(store.load()?.headsUp).hasSize(1)
    }

    private class FakeInsights(override val insights: Flow<DashboardInsights>) : DashboardInsightsRepository {
        override suspend fun acknowledgeArrival(countryCode: String) = Unit
        override suspend fun probeLatencyIfStale(maximumAge: Duration) = Unit
    }

    private class FakeDashboard(override val settings: Flow<AppSettings>) : NomadDashboardRepository {
        override val providerCredentials: Flow<ProviderCredentialSettings> = MutableStateFlow(ProviderCredentialSettings())
        override val snapshot: StateFlow<DashboardSnapshot> = MutableStateFlow(DashboardSnapshot())
        override val visitedPlaces: Flow<List<VisitedPlace>> = MutableStateFlow(emptyList())
        override val visitedCountryDays: Flow<List<VisitedCountryDay>> = MutableStateFlow(emptyList())
        override val visitedPlaceEvents: Flow<List<VisitedPlaceEvent>> = MutableStateFlow(emptyList())

        override suspend fun warmStart() = Unit
        override suspend fun refresh() = Unit
        override suspend fun clearVisitedHistory() = Unit
        override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = Unit
        override suspend fun updateProviderCredentials(transform: (ProviderCredentialSettings) -> ProviderCredentialSettings) = Unit
        override suspend fun resolveSurfSpotFromCurrentLocation(): SurfSpotConfiguration? = null
    }

    private class CountingUpdater : WidgetUpdater {
        var calls = 0

        override suspend fun updateAll() {
            calls += 1
        }
    }

    private class MutableClock(private var instant: Instant) : Clock() {
        fun advance(duration: Duration) {
            instant = instant.plus(duration)
        }

        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant
    }
}
