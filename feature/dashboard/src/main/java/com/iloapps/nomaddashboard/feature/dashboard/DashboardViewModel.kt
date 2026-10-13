package com.iloapps.nomaddashboard.feature.dashboard

import com.iloapps.nomaddashboard.core.model.withDashboardLayoutReset
import com.iloapps.nomaddashboard.core.model.withCardWidthToggled
import com.iloapps.nomaddashboard.core.model.withCardOrder
import com.iloapps.nomaddashboard.core.model.withCardCollapsed
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iloapps.nomaddashboard.core.data.insights.DashboardInsights
import com.iloapps.nomaddashboard.core.data.insights.DashboardInsightsRepository
import com.iloapps.nomaddashboard.core.data.insights.InsightsEnvironment
import com.iloapps.nomaddashboard.core.data.repository.NomadDashboardRepository
import com.iloapps.nomaddashboard.core.data.timetracking.AllocateTrackedTimeResult
import com.iloapps.nomaddashboard.core.data.timetracking.ReportInterruptionResult
import com.iloapps.nomaddashboard.core.data.timetracking.StartTrackingResult
import com.iloapps.nomaddashboard.core.data.timetracking.StopTrackingResult
import com.iloapps.nomaddashboard.core.data.timetracking.TimeTrackingRepository
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.ResourceMode
import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
import com.iloapps.nomaddashboard.core.model.TimeTrackingReportSnapshot
import com.iloapps.nomaddashboard.core.model.TimeTrackingRecord
import com.iloapps.nomaddashboard.core.model.TravelProfile
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.isAutomaticallyTracked
import com.iloapps.nomaddashboard.core.travel.arrival.ArrivalBriefingBuilder
import com.iloapps.nomaddashboard.core.travel.arrival.ArrivalBriefingEvents
import com.iloapps.nomaddashboard.core.travel.arrival.ArrivalBriefingPresentation
import com.iloapps.nomaddashboard.core.travel.arrival.ArrivalEvent
import com.iloapps.nomaddashboard.core.travel.glance.GlanceOverview
import com.iloapps.nomaddashboard.core.travel.glance.GlanceOverviewBuilder
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmCalendar
import com.iloapps.nomaddashboard.core.travel.safety.CurrentCountry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardTimeTrackingUiState(
    val projects: List<TimeTrackingProject> = emptyList(),
    val pendingEntries: List<TimeTrackingRecord> = emptyList(),
    val activeEntry: TimeTrackingRecord? = null,
    val report: TimeTrackingReportSnapshot = TimeTrackingReportSnapshot(),
    val message: String? = null,
)

data class DashboardUiState(
    val snapshot: DashboardSnapshot = DashboardSnapshot(),
    val settings: AppSettings = AppSettings(),
    val timeTracking: DashboardTimeTrackingUiState = DashboardTimeTrackingUiState(),
    /** The recorded days per country, which the Stays summary counts. */
    val countryDays: List<VisitedCountryDay> = emptyList(),
    /**
     * The top of the dashboard: header, verdict, clocks, vital tiles, heads-up items and links. The
     * view model builds it from the shared insights every minute; the default is the overview of
     * [snapshot] with no heads-up items, so a state made from a snapshot alone is always complete.
     */
    val glance: GlanceOverview = GlanceOverviewBuilder().build(
        snapshot = snapshot,
        settings = settings,
        headsUp = emptyList(),
        homeTimeZoneId = null,
        now = Instant.now(),
        zone = ZoneId.systemDefault(),
    ),
    /** The one-time "Welcome to ..." card for a border just crossed, or null when there is none to show. */
    val arrival: ArrivalBriefingPresentation? = null,
    /**
     * The briefing of the current country that "Show arrival briefing" would re-open, or null when the
     * stay is past the arrival window or the country is unknown. Hides the menu item.
     */
    val reopenableArrival: ArrivalEvent? = null,
)

sealed interface DashboardEffect {
    data object StartTrackingService : DashboardEffect

    data object StopTrackingService : DashboardEffect

    data object InterruptionReported : DashboardEffect
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: NomadDashboardRepository,
    private val timeTrackingRepository: TimeTrackingRepository,
    private val insightsRepository: DashboardInsightsRepository,
    private val environment: InsightsEnvironment,
    private val localRhythm: LocalRhythmCalendar,
) : ViewModel() {
    private val timeTrackingMessage = MutableStateFlow<String?>(null)
    private val _effects = MutableSharedFlow<DashboardEffect>()

    /** A briefing the user asked for from the menu; it wins over detection while it matches the current country. */
    private val reopenedArrival = MutableStateFlow<ArrivalEvent?>(null)

    val effects = _effects.asSharedFlow()

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(
            repository.snapshot,
            repository.settings,
            timeTrackingRepository.projects,
            timeTrackingRepository.pendingEntries,
        ) { snapshot, settings, projects, pendingEntries ->
            DashboardUiState(
                snapshot = snapshot,
                settings = settings,
                timeTracking = DashboardTimeTrackingUiState(
                    projects = projects,
                    pendingEntries = pendingEntries,
                    activeEntry = null,
                ),
            )
        },
        timeTrackingRepository.activeEntry,
    ) { state, activeEntry ->
        state.copy(
            timeTracking = state.timeTracking.copy(activeEntry = activeEntry),
        )
    }.combine(timeTrackingRepository.report) { state, report ->
        state.copy(
            timeTracking = state.timeTracking.copy(report = report),
        )
    }.combine(timeTrackingMessage) { state, message ->
        state.copy(
            timeTracking = state.timeTracking.copy(message = message),
        )
    }.combine(repository.visitedCountryDays) { state, countryDays ->
        state.copy(countryDays = countryDays)
    }.combine(combine(insightsRepository.insights, reopenedArrival, ::Pair)) { state, (insights, reopened) ->
        state.withInsights(insights, reopened)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    init {
        viewModelScope.launch {
            repository.warmStart()
            timeTrackingRepository.syncTracking()
            repository.refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            timeTrackingRepository.syncTracking()
            repository.refresh()
        }
    }

    fun refreshCard(card: DashboardCardId) {
        viewModelScope.launch { repository.refreshCard(card) }
    }

    /**
     * The dashboard came back to the foreground: takes a latency reading when the last one is old, so
     * the verdict and the Internet tile describe the connection now rather than minutes ago.
     */
    fun onResume() {
        viewModelScope.launch { insightsRepository.probeLatencyIfStale() }
    }

    /** Hides the arrival briefing for [countryCode] until the next border crossing. */
    fun acknowledgeArrival(countryCode: String) {
        reopenedArrival.value = null
        viewModelScope.launch { insightsRepository.acknowledgeArrival(countryCode) }
    }

    /** Re-opens the briefing of the current country, also after it was acknowledged or switched off. */
    fun showArrivalBriefing() {
        reopenedArrival.value = uiState.value.reopenableArrival
    }

    /**
     * Saves the data-usage choice made in the verdict strip (Auto, Normal or Low Data) and refreshes,
     * as Settings does: the policy that follows from the mode is decided when the dashboard refreshes.
     */
    fun setResourceMode(mode: ResourceMode) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(resourceMode = mode) }
            repository.refresh()
        }
    }

    /** Folds a card to its one-line summary or expands it; the choice is saved in the settings. */
    fun setCardCollapsed(card: DashboardCardId, collapsed: Boolean) {
        updateSettings { it.withCardCollapsed(card, collapsed) }
    }

    /** Saves the card order chosen by drag or by the move actions. */
    fun setCardOrder(order: List<DashboardCardId>) {
        updateSettings { it.withCardOrder(order) }
    }

    fun toggleCardWidth(card: DashboardCardId) {
        updateSettings { it.withCardWidthToggled(card) }
    }

    /** Restores the current travel profile's recommended order with wide, expanded cards. */
    fun resetDashboardLayout() {
        updateSettings(AppSettings::withDashboardLayoutReset)
    }

    fun applyTravelProfile(profile: TravelProfile) {
        updateSettings { it.applyTravelProfile(profile) }
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.updateSettings(transform) }
    }

    fun startTracking() {
        viewModelScope.launch {
            when (timeTrackingRepository.startTracking()) {
                StartTrackingResult.Started -> {
                    timeTrackingMessage.value = "Capture resumed."
                    _effects.emit(DashboardEffect.StartTrackingService)
                }

                StartTrackingResult.AlreadyTracking -> {
                    timeTrackingMessage.value = "Capture is already running."
                    _effects.emit(DashboardEffect.StartTrackingService)
                }
            }
        }
    }

    fun stopTracking() {
        viewModelScope.launch {
            when (timeTrackingRepository.stopTracking()) {
                StopTrackingResult.Stopped -> {
                    timeTrackingMessage.value = "Capture paused."
                    _effects.emit(DashboardEffect.StopTrackingService)
                }

                StopTrackingResult.NotTracking -> {
                    _effects.emit(DashboardEffect.StopTrackingService)
                }
            }
        }
    }

    fun allocateTrackedTime(projectId: UUID) {
        viewModelScope.launch {
            when (val result = timeTrackingRepository.allocateTrackedTime(projectId)) {
                is AllocateTrackedTimeResult.Allocated -> {
                    val active = timeTrackingRepository.currentActiveEntry()
                    timeTrackingMessage.value = if (active != null) {
                        "Allocated ${result.entryCount} segment${result.entryCount.pluralSuffix()} and resumed."
                    } else {
                        "Allocated ${result.entryCount} segment${result.entryCount.pluralSuffix()}."
                    }
                    _effects.emit(
                        if (active != null) DashboardEffect.StartTrackingService else DashboardEffect.StopTrackingService,
                    )
                }

                AllocateTrackedTimeResult.NothingToAllocate -> {
                    timeTrackingMessage.value = "Nothing to allocate yet."
                }

                AllocateTrackedTimeResult.MissingProject -> {
                    timeTrackingMessage.value = "That project is no longer available."
                }
            }
        }
    }

    fun reportInterruption() {
        viewModelScope.launch {
            when (timeTrackingRepository.reportInterruption()) {
                ReportInterruptionResult.Recorded -> {
                    timeTrackingMessage.value = "Interruption reported."
                    _effects.emit(DashboardEffect.InterruptionReported)
                }

                ReportInterruptionResult.TrackingDisabled -> {
                    timeTrackingMessage.value = "Turn on time tracking before reporting interruptions."
                }
            }
        }
    }

    /**
     * The state with the glance overview and the arrival briefing built from [insights], in the
     * device's current locale and 12/24-hour setting.
     */
    private fun DashboardUiState.withInsights(insights: DashboardInsights, reopened: ArrivalEvent?): DashboardUiState {
        val formats = environment.formats()
        val glance = GlanceOverviewBuilder(formats.locale, formats.clockTime).build(
            snapshot = insights.snapshot,
            settings = settings,
            headsUp = insights.headsUp,
            homeTimeZoneId = insights.homeTimeZoneId,
            now = insights.evaluatedAt,
            zone = insights.zone,
        )
        val travelContext = insights.snapshot.travelContext
        val currentCountry = CurrentCountry.code(
            deviceCountryCode = travelContext.deviceCountryCode,
            ipCountryCode = travelContext.countryCode,
            localInfoCountryCode = insights.snapshot.localInfo.countryCode,
        )
        val arrival = ArrivalBriefingEvents.event(reopened, insights.arrival, currentCountry)?.let { event ->
            val briefing = ArrivalBriefingBuilder(localRhythm, formats.locale).build(
                event = event,
                snapshot = insights.snapshot,
                settings = settings,
                homeTimeZoneId = insights.homeTimeZoneId,
                now = insights.evaluatedAt,
                zone = insights.zone,
            )
            ArrivalBriefingPresentation.of(
                briefing = briefing,
                previousCountryCode = event.previousCountryCode,
                now = insights.evaluatedAt,
                zone = insights.zone,
                formats = formats,
            )
        }
        val reopenable = ArrivalBriefingEvents.reopened(countryDays, currentCountry, insights.evaluatedAt, insights.zone)
        return copy(glance = glance, arrival = arrival, reopenableArrival = reopenable)
    }

    fun trackingModeLabel(state: DashboardTimeTrackingUiState): String = when {
        state.activeEntry != null && state.activeEntry.entry.isAutomaticallyTracked() -> "Auto"
        state.activeEntry != null -> "Manual"
        state.pendingEntries.isNotEmpty() -> "Paused"
        else -> "Ready"
    }
}

private fun Int.pluralSuffix(): String = if (this == 1) "" else "s"
