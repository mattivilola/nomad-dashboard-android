package com.iloapps.nomaddashboard.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iloapps.nomaddashboard.core.data.repository.NomadDashboardRepository
import com.iloapps.nomaddashboard.core.data.timetracking.AllocateTrackedTimeResult
import com.iloapps.nomaddashboard.core.data.timetracking.ReportInterruptionResult
import com.iloapps.nomaddashboard.core.data.timetracking.StartTrackingResult
import com.iloapps.nomaddashboard.core.data.timetracking.StopTrackingResult
import com.iloapps.nomaddashboard.core.data.timetracking.TimeTrackingRepository
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
import com.iloapps.nomaddashboard.core.model.TimeTrackingReportSnapshot
import com.iloapps.nomaddashboard.core.model.TimeTrackingRecord
import com.iloapps.nomaddashboard.core.model.isAutomaticallyTracked
import dagger.hilt.android.lifecycle.HiltViewModel
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
) : ViewModel() {
    private val timeTrackingMessage = MutableStateFlow<String?>(null)
    private val _effects = MutableSharedFlow<DashboardEffect>()

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    init {
        viewModelScope.launch {
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
            repository.refresh()
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
            repository.refresh()
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
            repository.refresh()
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
            repository.refresh()
        }
    }

    fun trackingModeLabel(state: DashboardTimeTrackingUiState): String = when {
        state.activeEntry != null && state.activeEntry.entry.isAutomaticallyTracked() -> "Auto"
        state.activeEntry != null -> "Manual"
        state.pendingEntries.isNotEmpty() -> "Paused"
        else -> "Ready"
    }
}

private fun Int.pluralSuffix(): String = if (this == 1) "" else "s"
