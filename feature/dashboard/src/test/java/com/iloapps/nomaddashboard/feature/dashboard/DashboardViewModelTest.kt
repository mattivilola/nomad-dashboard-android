package com.iloapps.nomaddashboard.feature.dashboard

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.data.insights.DashboardInsights
import com.iloapps.nomaddashboard.core.data.insights.DashboardInsightsRepository
import com.iloapps.nomaddashboard.core.data.insights.InsightsEnvironment
import com.iloapps.nomaddashboard.core.data.repository.NomadDashboardRepository
import com.iloapps.nomaddashboard.core.data.timetracking.AllocateTrackedTimeResult
import com.iloapps.nomaddashboard.core.data.timetracking.CreateProjectResult
import com.iloapps.nomaddashboard.core.data.timetracking.ReportInterruptionResult
import com.iloapps.nomaddashboard.core.data.timetracking.StartTrackingResult
import com.iloapps.nomaddashboard.core.data.timetracking.StopTrackingResult
import com.iloapps.nomaddashboard.core.data.timetracking.TimeTrackingRepository
import com.iloapps.nomaddashboard.core.data.timetracking.UpdateTimeTrackingEntryResult
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.InternetState
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.model.ProviderCredentialSettings
import com.iloapps.nomaddashboard.core.model.ResourceMode
import com.iloapps.nomaddashboard.core.model.SurfSpotConfiguration
import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
import com.iloapps.nomaddashboard.core.model.TimeTrackingRecord
import com.iloapps.nomaddashboard.core.model.TimeTrackingReportSnapshot
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import com.iloapps.nomaddashboard.core.model.VisitedPlaceEvent
import com.iloapps.nomaddashboard.core.travel.arrival.ArrivalEvent
import com.iloapps.nomaddashboard.core.travel.daylight.ClockTimeFormatter
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpFormats
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpItem
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpKind
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpSeverity
import com.iloapps.nomaddashboard.core.travel.health.DashboardHealthEvaluator
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmCalendar
import com.iloapps.nomaddashboard.core.travel.rhythm.LunisolarCalendars
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    // 2026-10-12 09:30 UTC: 11:30 in Madrid, 12:30 in Helsinki.
    private val now = Instant.parse("2026-10-12T09:30:00Z")
    private val madrid = ZoneId.of("Europe/Madrid")

    private val dashboard = FakeDashboardRepository()
    private val timeTracking = FakeTimeTrackingRepository()
    private val insights = FakeInsightsRepository(insightsOf(DashboardSnapshot()))
    private var clock: ClockTimeFormatter = ClockTimeFormatter.Hour24
    private val environment = object : InsightsEnvironment {
        override fun formats() = HeadsUpFormats(locale = Locale.UK, clockTime = clock)

        override fun zone(): ZoneId = madrid
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun insightsOf(
        snapshot: DashboardSnapshot,
        headsUp: List<HeadsUpItem> = emptyList(),
        arrival: ArrivalEvent? = null,
        homeTimeZoneId: String? = "Europe/Helsinki",
    ) = DashboardInsights(
        snapshot = snapshot,
        headsUp = headsUp,
        health = DashboardHealthEvaluator.evaluate(snapshot.connectivity, snapshot.power),
        statusLine = null,
        arrival = arrival,
        homeTimeZoneId = homeTimeZoneId,
        zone = madrid,
        evaluatedAt = now,
    )

    private fun viewModel() = DashboardViewModel(
        repository = dashboard,
        timeTrackingRepository = timeTracking,
        insightsRepository = insights,
        environment = environment,
        localRhythm = LocalRhythmCalendar(NoLunisolarCalendars),
    )

    /** The state flow only runs while it has a subscriber, as it does on screen. */
    private fun DashboardViewModel.collecting(block: DashboardViewModel.() -> Unit) = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { uiState.collect {} }
        block()
    }

    private val online = ConnectivitySnapshot(
        internetState = InternetState.ONLINE,
        isOnline = true,
        latencyMs = 28.0,
        downloadMbps = 96.0,
        uploadMbps = 19.2,
        wifiName = "Nomad Hub",
    )

    @Test
    fun `the glance is built from the shared insights`() {
        val snapshot = DashboardSnapshot(
            connectivity = online,
            power = PowerSnapshot(batteryPercent = 78, statusLabel = "On Battery", powerSourceLabel = "Battery"),
            travelContext = TravelContextSnapshot(deviceCity = "Valencia", deviceCountry = "Spain", city = "Madrid", country = "Spain"),
            lastRefresh = now,
        )
        insights.current.value = insightsOf(
            snapshot,
            headsUp = listOf(
                HeadsUpItem(
                    kind = HeadsUpKind.STRONG_WIND,
                    severity = HeadsUpSeverity.CAUTION,
                    title = "Wind up to 45 km/h",
                    statusText = "Wind 45",
                    detail = "From W around 16:00",
                    relatedCard = DashboardCardId.WEATHER,
                ),
            ),
        )

        viewModel().collecting {
            val glance = uiState.value.glance

            assertThat(glance.header.headline).isEqualTo("Valencia")
            assertThat(glance.verdict.title).isEqualTo("Ready to work")
            assertThat(glance.headsUp.map { it.title }).containsExactly("Wind up to 45 km/h")
            assertThat(glance.headsUp.single().relatedCard).isEqualTo(DashboardCardId.WEATHER)
            assertThat(glance.tiles.map { it.title }).containsExactly("Internet", "Power", "VPN", "Weather").inOrder()
            assertThat(glance.clocks.here.text).isEqualTo("Here Valencia 11:30")
            assertThat(glance.clocks.home?.text).isEqualTo("Home Helsinki 12:30")
        }
    }

    @Test
    fun `the glance follows the devices clock format`() {
        clock = ClockTimeFormatter { time: ZonedDateTime -> "${time.hour % 12}:%02d ${if (time.hour < 12) "AM" else "PM"}".format(time.minute) }
        insights.current.value = insightsOf(DashboardSnapshot(connectivity = online, lastRefresh = now))

        viewModel().collecting {
            assertThat(uiState.value.glance.refresh.statusText).isEqualTo("Last refresh 11:30 AM")
            assertThat(uiState.value.glance.clocks.here.time).isEqualTo("11:30 AM")
        }
    }

    @Test
    fun `existing state fields keep the repository snapshot and settings`() {
        dashboard.settings.value = AppSettings(projectTimeTrackingEnabled = true)
        dashboard.snapshot.value = DashboardSnapshot(connectivity = online)

        viewModel().collecting {
            assertThat(uiState.value.settings.projectTimeTrackingEnabled).isTrue()
            assertThat(uiState.value.snapshot.connectivity.wifiName).isEqualTo("Nomad Hub")
        }
    }

    @Test
    fun `no arrival means no briefing card`() = viewModel().collecting {
        assertThat(uiState.value.arrival).isNull()
    }

    @Test
    fun `an arrival becomes a welcome briefing for the new country`() {
        val snapshot = DashboardSnapshot(
            connectivity = online,
            travelContext = TravelContextSnapshot(deviceCity = "Tangier", deviceCountry = "Morocco", deviceCountryCode = "MA", timeZoneId = "Africa/Casablanca"),
        )
        insights.current.value = insightsOf(snapshot, arrival = ArrivalEvent("MA", "ES", LocalDate.of(2026, 10, 12)))

        viewModel().collecting {
            val arrival = uiState.value.arrival!!

            assertThat(arrival.countryCode).isEqualTo("MA")
            assertThat(arrival.title).isEqualTo("Welcome to Morocco")
            assertThat(arrival.subtitle).isEqualTo("Arrived from Spain")
            assertThat(arrival.rows.map { it.id }).contains("emergency")
        }
    }

    private fun countryDay(daysAgo: Long, code: String) = VisitedCountryDay(
        date = LocalDate.of(2026, 10, 12).minusDays(daysAgo),
        country = code,
        countryCode = code,
        source = VisitedPlaceSource.DEVICE_LOCATION,
        isInferred = false,
    )

    private val moroccoSnapshot = DashboardSnapshot(
        connectivity = online,
        travelContext = TravelContextSnapshot(deviceCity = "Tangier", deviceCountry = "Morocco", deviceCountryCode = "MA", timeZoneId = "Africa/Casablanca"),
    )

    @Test
    fun `an acknowledged briefing can be shown again from the menu`() {
        dashboard.visitedCountryDays.value = listOf(countryDay(5, "ES"), countryDay(1, "MA"))
        insights.current.value = insightsOf(moroccoSnapshot, arrival = null)

        viewModel().collecting {
            assertThat(uiState.value.arrival).isNull()
            assertThat(uiState.value.reopenableArrival?.countryCode).isEqualTo("MA")

            showArrivalBriefing()

            val arrival = uiState.value.arrival!!
            assertThat(arrival.title).isEqualTo("Welcome to Morocco")
            assertThat(arrival.subtitle).isEqualTo("Arrived from Spain")

            acknowledgeArrival("MA")

            assertThat(uiState.value.arrival).isNull()
        }
    }

    @Test
    fun `there is nothing to show again past the arrival window or without a country`() {
        dashboard.visitedCountryDays.value = (0L..10L).map { countryDay(it, "MA") }
        insights.current.value = insightsOf(moroccoSnapshot)

        viewModel().collecting {
            assertThat(uiState.value.reopenableArrival).isNull()
            showArrivalBriefing()
            assertThat(uiState.value.arrival).isNull()
        }
    }

    @Test
    fun `a briefing shown again is dropped once the traveller is in another country`() {
        dashboard.visitedCountryDays.value = listOf(countryDay(5, "ES"), countryDay(1, "MA"))
        insights.current.value = insightsOf(moroccoSnapshot)

        viewModel().collecting {
            showArrivalBriefing()
            assertThat(uiState.value.arrival).isNotNull()

            insights.current.value = insightsOf(
                DashboardSnapshot(
                    connectivity = online,
                    travelContext = TravelContextSnapshot(deviceCountryCode = "ES", deviceCountry = "Spain"),
                ),
            )

            assertThat(uiState.value.arrival).isNull()
        }
    }

    @Test
    fun `got it acknowledges the arrival of that country`() = viewModel().collecting {
        acknowledgeArrival("MA")

        assertThat(insights.acknowledged).containsExactly("MA")
    }

    @Test
    fun `resuming probes a stale latency reading`() = viewModel().collecting {
        onResume()

        assertThat(insights.probeCount).isEqualTo(1)
    }

    @Test
    fun `choosing a data usage mode saves it and refreshes`() = viewModel().collecting {
        val refreshesBefore = dashboard.refreshCount

        setResourceMode(ResourceMode.LOW_DATA)

        assertThat(dashboard.settings.value.resourceMode).isEqualTo(ResourceMode.LOW_DATA)
        assertThat(dashboard.refreshCount).isEqualTo(refreshesBefore + 1)
        assertThat(uiState.value.settings.resourceMode).isEqualTo(ResourceMode.LOW_DATA)
    }

    @Test
    fun `the time tracking actions of the header reach the repository`() = viewModel().collecting {
        startTracking()
        stopTracking()
        reportInterruption()
        val project = TimeTrackingProject(name = "Acme")
        allocateTrackedTime(project.id)

        assertThat(timeTracking.calls).containsExactly("start", "stop", "interruption", "allocate:${project.id}").inOrder()
    }
}

private object NoLunisolarCalendars : LunisolarCalendars {
    override fun hijriYear(date: LocalDate): Int = 1448

    override fun ummAlQuraToGregorian(hijriYear: Int, month: Int, day: Int): LocalDate? = null

    override fun hebrewToGregorian(hebrewYear: Int, month: Int, day: Int): LocalDate? = null
}

private class FakeInsightsRepository(initial: DashboardInsights) : DashboardInsightsRepository {
    val current = MutableStateFlow(initial)
    val acknowledged = mutableListOf<String>()
    var probeCount = 0

    override val insights: Flow<DashboardInsights> get() = current

    override suspend fun acknowledgeArrival(countryCode: String) {
        acknowledged += countryCode
    }

    override suspend fun probeLatencyIfStale(maximumAge: Duration) {
        probeCount += 1
    }
}

private class FakeDashboardRepository : NomadDashboardRepository {
    override val settings = MutableStateFlow(AppSettings())
    override val providerCredentials = MutableStateFlow(ProviderCredentialSettings())
    override val snapshot = MutableStateFlow(DashboardSnapshot())
    override val visitedPlaces: Flow<List<VisitedPlace>> = emptyFlow()
    override val visitedCountryDays = MutableStateFlow<List<VisitedCountryDay>>(emptyList())
    override val visitedPlaceEvents: Flow<List<VisitedPlaceEvent>> = emptyFlow()
    var refreshCount = 0

    override suspend fun warmStart() = Unit

    override suspend fun refresh() {
        refreshCount += 1
    }

    override suspend fun clearVisitedHistory() = Unit

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settings.update(transform)
    }

    override suspend fun updateProviderCredentials(transform: (ProviderCredentialSettings) -> ProviderCredentialSettings) {
        providerCredentials.update(transform)
    }

    override suspend fun resolveSurfSpotFromCurrentLocation(): SurfSpotConfiguration? = null
}

private class FakeTimeTrackingRepository : TimeTrackingRepository {
    override val projects: Flow<List<TimeTrackingProject>> = MutableStateFlow(emptyList())
    override val recentEntries: Flow<List<TimeTrackingRecord>> = MutableStateFlow(emptyList())
    override val pendingEntries: Flow<List<TimeTrackingRecord>> = MutableStateFlow(emptyList())
    override val activeEntry: Flow<TimeTrackingRecord?> = MutableStateFlow(null)
    override val report: Flow<TimeTrackingReportSnapshot> = MutableStateFlow(TimeTrackingReportSnapshot())
    val calls = mutableListOf<String>()

    override suspend fun currentActiveEntry(): TimeTrackingRecord? = null

    override suspend fun syncTracking(now: Instant) = Unit

    override suspend fun createProject(name: String): CreateProjectResult = CreateProjectResult.InvalidName

    override suspend fun startTracking(): StartTrackingResult {
        calls += "start"
        return StartTrackingResult.Started
    }

    override suspend fun stopTracking(): StopTrackingResult {
        calls += "stop"
        return StopTrackingResult.Stopped
    }

    override suspend fun reportInterruption(now: Instant): ReportInterruptionResult {
        calls += "interruption"
        return ReportInterruptionResult.Recorded
    }

    override suspend fun allocateTrackedTime(projectId: UUID): AllocateTrackedTimeResult {
        calls += "allocate:$projectId"
        return AllocateTrackedTimeResult.Allocated(1)
    }

    override suspend fun updateEntry(entryId: UUID, startAt: Instant, endAt: Instant): UpdateTimeTrackingEntryResult =
        UpdateTimeTrackingEntryResult.Updated
}
