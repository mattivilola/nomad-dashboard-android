package com.iloapps.nomaddashboard.core.data.insights

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.data.companion.CompanionRepository
import com.iloapps.nomaddashboard.core.data.repository.NomadDashboardRepository
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.CompanionImportPreview
import com.iloapps.nomaddashboard.core.model.CompanionPreferences
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.InternetState
import com.iloapps.nomaddashboard.core.model.NomadCompanionData
import com.iloapps.nomaddashboard.core.model.OfflineEssential
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.model.ProviderCredentialSettings
import com.iloapps.nomaddashboard.core.model.SurfSpotConfiguration
import com.iloapps.nomaddashboard.core.model.TimezonePlanner
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedPlaceEvent
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import com.iloapps.nomaddashboard.core.model.WeatherHourlyForecastSlot
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import com.iloapps.nomaddashboard.core.model.WorkplaceVisit
import com.iloapps.nomaddashboard.core.travel.arrival.ArrivalEvent
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpFormats
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpKind
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpEngine
import com.iloapps.nomaddashboard.core.travel.health.HealthLevel
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmCalendar
import com.iloapps.nomaddashboard.core.travel.rhythm.LunisolarCalendars
import com.iloapps.nomaddashboard.core.travel.status.StatusLinePresentation
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardInsightsRepositoryTest {
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val paris = ZoneId.of("Europe/Paris")

    private class Fixture(
        val dashboard: FakeDashboard,
        val companion: FakeCompanion,
        val environment: CountingEnvironment,
        val clock: MutableClock,
        val repository: DefaultDashboardInsightsRepository,
    )

    private fun TestScope.fixture(
        snapshot: DashboardSnapshot = DashboardSnapshot(),
        settings: AppSettings = AppSettings(),
        countryDays: List<VisitedCountryDay> = emptyList(),
    ): Fixture {
        val dashboard = FakeDashboard(snapshot, settings, countryDays)
        val companion = FakeCompanion()
        val environment = CountingEnvironment(HeadsUpFormats(locale = Locale.UK), paris)
        val clock = MutableClock(now)
        val repository = DefaultDashboardInsightsRepository(
            dashboard = dashboard,
            companion = companion,
            headsUpEngine = HeadsUpEngine(LocalRhythmCalendar(NoLunisolarCalendars)),
            environment = environment,
            ticker = PeriodicInsightsTicker(Duration.ofSeconds(60)),
            clock = clock,
            computeDispatcher = StandardTestDispatcher(testScheduler),
            scope = backgroundScope,
        )
        return Fixture(dashboard, companion, environment, clock, repository)
    }

    private fun windy(kph: Double = 62.0) = DashboardSnapshot(
        connectivity = ConnectivitySnapshot(
            internetState = InternetState.ONLINE,
            isOnline = true,
            latencyMs = 24.0,
            latencyMeasuredAt = now.minusSeconds(10),
            wifiName = "Nomad Hub",
        ),
        power = PowerSnapshot(batteryPercent = 80, statusLabel = "On Battery", minutesRemaining = 300),
        weather = WeatherSnapshot(
            currentTemperatureCelsius = 20.0,
            windSpeedKph = kph,
            windDirectionDegrees = 270.0,
            fetchedAt = now,
        ),
        travelContext = TravelContextSnapshot(deviceCountryCode = "ES"),
    )

    @Test
    fun `combines heads up health status line arrival and home time zone`() = runTest {
        val today = now.atZone(paris).toLocalDate()
        val fixture = fixture(
            snapshot = windy(),
            countryDays = listOf(day(today.minusDays(1), "FR"), day(today, "ES")),
        )
        fixture.companion.data.value = NomadCompanionData(preferences = CompanionPreferences(homeTimeZoneId = "Europe/Helsinki"))

        val insights = fixture.repository.insights.first()

        assertThat(insights.headsUp.map { it.kind }).contains(HeadsUpKind.STRONG_WIND)
        assertThat(insights.health.network.level).isEqualTo(HealthLevel.READY)
        assertThat(insights.statusLine?.rule).isEqualTo(StatusLinePresentation.Rule.WEATHER)
        assertThat(insights.arrival).isEqualTo(ArrivalEvent("ES", "FR", today))
        assertThat(insights.homeTimeZoneId).isEqualTo("Europe/Helsinki")
        assertThat(insights.zone).isEqualTo(paris)
        assertThat(insights.evaluatedAt).isEqualTo(now)
    }

    @Test
    fun `re-evaluates when the snapshot settings or home time zone change`() = runTest {
        val fixture = fixture(snapshot = windy(kph = 10.0))
        val seen = mutableListOf<DashboardInsights>()
        backgroundScope.launch { fixture.repository.insights.collect { seen += it } }
        runCurrent()
        assertThat(seen.last().headsUp).isEmpty()

        fixture.dashboard.snapshotState.value = windy(kph = 62.0)
        runCurrent()
        assertThat(seen.last().headsUp.map { it.kind }).containsExactly(HeadsUpKind.STRONG_WIND)

        fixture.dashboard.settingsState.value = AppSettings(statusShowsHeadsUp = true)
        runCurrent()
        assertThat(seen.last().statusLine?.rule).isEqualTo(StatusLinePresentation.Rule.HEADS_UP)

        fixture.companion.data.value = NomadCompanionData(preferences = CompanionPreferences(homeTimeZoneId = "Asia/Tokyo"))
        runCurrent()
        assertThat(seen.last().homeTimeZoneId).isEqualTo("Asia/Tokyo")
    }

    @Test
    fun `time relative items re-evaluate every sixty seconds`() = runTest {
        // Rain is 7 hours away, so it is not a heads-up yet; two hours later it is within the 6 hour window.
        val snapshot = windy(kph = 10.0).let {
            it.copy(
                weather = it.weather.copy(
                    rainChancePercent = 10,
                    hourlyForecast = listOf(
                        WeatherHourlyForecastSlot(
                            at = now.plusSeconds(7 * 3_600),
                            summary = "Rain",
                            temperatureCelsius = 15.0,
                            rainChancePercent = 90,
                            windSpeedKph = 10.0,
                            windDirectionDegrees = 270.0,
                        ),
                    ),
                ),
            )
        }
        val fixture = fixture(snapshot = snapshot)
        val seen = mutableListOf<DashboardInsights>()
        backgroundScope.launch { fixture.repository.insights.collect { seen += it } }
        runCurrent()
        assertThat(seen).hasSize(1)
        assertThat(seen.last().headsUp.map { it.kind }).doesNotContain(HeadsUpKind.RAIN_SOON)

        fixture.clock.advance(Duration.ofHours(2))
        advanceTimeBy(59_000)
        runCurrent()
        assertThat(seen).hasSize(1)

        advanceTimeBy(2_000)
        runCurrent()
        assertThat(seen).hasSize(2)
        assertThat(seen.last().evaluatedAt).isEqualTo(now.plusSeconds(2 * 3_600))
        assertThat(seen.last().headsUp.map { it.kind }).contains(HeadsUpKind.RAIN_SOON)

        advanceTimeBy(3 * 60_000)
        runCurrent()
        assertThat(seen).hasSize(5)
    }

    @Test
    fun `collectors share one evaluation`() = runTest {
        val fixture = fixture(snapshot = windy())
        val first = mutableListOf<DashboardInsights>()
        val second = mutableListOf<DashboardInsights>()
        backgroundScope.launch { fixture.repository.insights.collect { first += it } }
        backgroundScope.launch { fixture.repository.insights.collect { second += it } }
        runCurrent()

        assertThat(first).hasSize(1)
        assertThat(second).hasSize(1)
        assertThat(fixture.environment.zoneCalls).isEqualTo(1)
    }

    @Test
    fun `latency readings older than five minutes are ignored`() = runTest {
        val stale = windy().copy(
            connectivity = windy().connectivity.copy(latencyMs = 400.0, latencyMeasuredAt = now.minusSeconds(600)),
        )
        val fresh = windy().copy(
            connectivity = windy().connectivity.copy(latencyMs = 400.0, latencyMeasuredAt = now.minusSeconds(60)),
        )

        val staleInsights = fixture(snapshot = stale).repository.insights.first()
        val freshInsights = fixture(snapshot = fresh).repository.insights.first()

        assertThat(staleInsights.snapshot.connectivity.latencyMs).isNull()
        assertThat(staleInsights.health.network.level).isEqualTo(HealthLevel.UNAVAILABLE)
        assertThat(freshInsights.snapshot.connectivity.latencyMs).isEqualTo(400.0)
        assertThat(freshInsights.health.network.level).isEqualTo(HealthLevel.ATTENTION)
    }

    @Test
    fun `an unvalidated network reads as a caution`() = runTest {
        val captive = windy().copy(
            connectivity = ConnectivitySnapshot(internetState = InternetState.CAPTIVE_OR_UNVALIDATED),
        )

        val insights = fixture(snapshot = captive).repository.insights.first()

        assertThat(insights.health.overall.level).isEqualTo(HealthLevel.CAUTION)
        assertThat(insights.health.overall.reason).isEqualTo("Network needs sign-in or isn't verified")
    }

    @Test
    fun `acknowledging an arrival stores the country and time and clears the event`() = runTest {
        val today = now.atZone(paris).toLocalDate()
        val fixture = fixture(
            snapshot = windy(),
            countryDays = listOf(day(today.minusDays(1), "FR"), day(today, "ES")),
        )
        val seen = mutableListOf<DashboardInsights>()
        backgroundScope.launch { fixture.repository.insights.collect { seen += it } }
        runCurrent()
        assertThat(seen.last().arrival?.countryCode).isEqualTo("ES")

        fixture.repository.acknowledgeArrival("es")
        runCurrent()

        val saved = fixture.dashboard.settingsState.value
        assertThat(saved.arrivalBriefingAcknowledgedCountryCode).isEqualTo("ES")
        assertThat(saved.arrivalBriefingAcknowledgedAt).isEqualTo(now)
        assertThat(seen.last().arrival).isNull()
    }

    @Test
    fun `acknowledging something that is not a country code changes nothing`() = runTest {
        val fixture = fixture()

        fixture.repository.acknowledgeArrival("not a country")

        assertThat(fixture.dashboard.settingsState.value).isEqualTo(AppSettings())
    }

    @Test
    fun `probing latency is delegated to the dashboard repository`() = runTest {
        val fixture = fixture()

        fixture.repository.probeLatencyIfStale()
        fixture.repository.probeLatencyIfStale(Duration.ofSeconds(5))

        assertThat(fixture.dashboard.probeAges).containsExactly(Duration.ofSeconds(60), Duration.ofSeconds(5)).inOrder()
    }

    private fun day(date: LocalDate, code: String) = VisitedCountryDay(
        date = date,
        country = code,
        countryCode = code,
        source = VisitedPlaceSource.DEVICE_LOCATION,
        isInferred = false,
    )
}

private object NoLunisolarCalendars : LunisolarCalendars {
    override fun hijriYear(date: LocalDate): Int = 1448
    override fun ummAlQuraToGregorian(hijriYear: Int, month: Int, day: Int): LocalDate? = null
    override fun hebrewToGregorian(hebrewYear: Int, month: Int, day: Int): LocalDate? = null
}

private class CountingEnvironment(private val formats: HeadsUpFormats, private val zone: ZoneId) : InsightsEnvironment {
    var zoneCalls = 0

    override fun formats(): HeadsUpFormats = formats

    override fun zone(): ZoneId {
        zoneCalls += 1
        return zone
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

private class FakeDashboard(
    snapshot: DashboardSnapshot,
    settings: AppSettings,
    countryDays: List<VisitedCountryDay>,
) : NomadDashboardRepository {
    val snapshotState = MutableStateFlow(snapshot)
    val settingsState = MutableStateFlow(settings)
    private val countryDaysState = MutableStateFlow(countryDays)
    val probeAges = mutableListOf<Duration>()

    override val settings: Flow<AppSettings> = settingsState
    override val providerCredentials: Flow<ProviderCredentialSettings> = MutableStateFlow(ProviderCredentialSettings())
    override val snapshot: StateFlow<DashboardSnapshot> = snapshotState
    override val visitedPlaces: Flow<List<VisitedPlace>> = MutableStateFlow(emptyList())
    override val visitedCountryDays: Flow<List<VisitedCountryDay>> = countryDaysState
    override val visitedPlaceEvents: Flow<List<VisitedPlaceEvent>> = MutableStateFlow(emptyList())

    override suspend fun warmStart() = Unit
    override suspend fun refresh() = Unit
    override suspend fun clearVisitedHistory() = Unit
    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settingsState.value = transform(settingsState.value)
    }

    override suspend fun updateProviderCredentials(transform: (ProviderCredentialSettings) -> ProviderCredentialSettings) = Unit
    override suspend fun resolveSurfSpotFromCurrentLocation(): SurfSpotConfiguration? = null
    override suspend fun probeLatencyIfStale(maximumAge: Duration) {
        probeAges += maximumAge
    }
}

private class FakeCompanion : CompanionRepository {
    override val data = MutableStateFlow(NomadCompanionData())
    override val storageError = MutableStateFlow<String?>(null)

    override suspend fun update(transform: (NomadCompanionData) -> NomadCompanionData) {
        data.value = transform(data.value)
    }

    override suspend fun saveEssential(item: OfflineEssential) = Unit
    override suspend fun savePlanner(planner: TimezonePlanner) = Unit
    override suspend fun saveVisit(visit: WorkplaceVisit) = Unit
    override suspend fun exportBackup(): String = ""
    override suspend fun previewImport(serialized: String): CompanionImportPreview = error("unused")
    override suspend fun mergeImport(serialized: String): CompanionImportPreview = error("unused")
}
