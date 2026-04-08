package com.iloapps.nomaddashboard.feature.timetracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iloapps.nomaddashboard.core.data.repository.NomadDashboardRepository
import com.iloapps.nomaddashboard.core.data.timetracking.AllocateTrackedTimeResult
import com.iloapps.nomaddashboard.core.data.timetracking.CreateProjectResult
import com.iloapps.nomaddashboard.core.data.timetracking.StartTrackingResult
import com.iloapps.nomaddashboard.core.data.timetracking.StopTrackingResult
import com.iloapps.nomaddashboard.core.data.timetracking.TimeTrackingRepository
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
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

data class TimeTrackingUiState(
    val settings: AppSettings = AppSettings(),
    val projects: List<TimeTrackingProject> = emptyList(),
    val pendingEntries: List<TimeTrackingRecord> = emptyList(),
    val recentEntries: List<TimeTrackingRecord> = emptyList(),
    val activeEntry: TimeTrackingRecord? = null,
    val draftProjectName: String = "",
    val message: String? = null,
)

sealed interface TimeTrackingEffect {
    data object StartService : TimeTrackingEffect

    data object StopService : TimeTrackingEffect
}

private data class TrackingInputs(
    val draftProjectName: String,
    val message: String?,
)

private data class TrackingSnapshot(
    val settings: AppSettings,
    val projects: List<TimeTrackingProject>,
    val pendingEntries: List<TimeTrackingRecord>,
    val recentEntries: List<TimeTrackingRecord>,
    val activeEntry: TimeTrackingRecord?,
)

@HiltViewModel
class TimeTrackingViewModel @Inject constructor(
    private val dashboardRepository: NomadDashboardRepository,
    private val timeTrackingRepository: TimeTrackingRepository,
) : ViewModel() {
    private val draftProjectName = MutableStateFlow("")
    private val message = MutableStateFlow<String?>(null)
    private val _effects = MutableSharedFlow<TimeTrackingEffect>()

    val effects = _effects.asSharedFlow()

    private val inputs = combine(
        draftProjectName,
        message,
        ::TrackingInputs,
    )

    private val trackingSnapshot = combine(
        dashboardRepository.settings,
        timeTrackingRepository.projects,
        timeTrackingRepository.pendingEntries,
        timeTrackingRepository.recentEntries,
        timeTrackingRepository.activeEntry,
    ) { settings, projects, pendingEntries, recentEntries, activeEntry ->
        TrackingSnapshot(
            settings = settings,
            projects = projects,
            pendingEntries = pendingEntries,
            recentEntries = recentEntries,
            activeEntry = activeEntry,
        )
    }

    val uiState: StateFlow<TimeTrackingUiState> = combine(
        trackingSnapshot,
        inputs,
    ) { snapshot, inputs ->
        TimeTrackingUiState(
            settings = snapshot.settings,
            projects = snapshot.projects,
            pendingEntries = snapshot.pendingEntries,
            recentEntries = snapshot.recentEntries,
            activeEntry = snapshot.activeEntry,
            draftProjectName = inputs.draftProjectName,
            message = inputs.message,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TimeTrackingUiState(),
    )

    init {
        viewModelScope.launch {
            timeTrackingRepository.syncTracking()
        }
    }

    fun updateDraftProjectName(value: String) {
        draftProjectName.value = value
        message.value = null
    }

    fun createProject() {
        viewModelScope.launch {
            when (val result = timeTrackingRepository.createProject(draftProjectName.value)) {
                is CreateProjectResult.Created -> {
                    draftProjectName.value = ""
                    message.value = "${result.project.name} is ready for quick allocation."
                }

                is CreateProjectResult.Existing -> {
                    draftProjectName.value = ""
                    message.value = "${result.project.name} already exists."
                }

                CreateProjectResult.InvalidName -> {
                    message.value = "Enter a project name before adding it."
                }
            }
        }
    }

    fun startTracking() {
        viewModelScope.launch {
            when (timeTrackingRepository.startTracking()) {
                StartTrackingResult.Started -> {
                    message.value = "Unallocated capture resumed."
                    _effects.emit(TimeTrackingEffect.StartService)
                }

                StartTrackingResult.AlreadyTracking -> {
                    message.value = "Capture is already running."
                    _effects.emit(TimeTrackingEffect.StartService)
                }
            }
            dashboardRepository.refresh()
        }
    }

    fun stopTracking() {
        viewModelScope.launch {
            when (timeTrackingRepository.stopTracking()) {
                StopTrackingResult.Stopped -> {
                    message.value = "Capture paused. Allocate the buffer when you're ready."
                    _effects.emit(TimeTrackingEffect.StopService)
                }

                StopTrackingResult.NotTracking -> {
                    _effects.emit(TimeTrackingEffect.StopService)
                }
            }
            dashboardRepository.refresh()
        }
    }

    fun allocateTrackedTime(projectId: UUID) {
        viewModelScope.launch {
            when (val result = timeTrackingRepository.allocateTrackedTime(projectId)) {
                is AllocateTrackedTimeResult.Allocated -> {
                    val restarted = timeTrackingRepository.currentActiveEntry() != null
                    message.value = if (restarted) {
                        "Allocated ${result.entryCount} segment${result.entryCount.pluralSuffix()} and restarted capture."
                    } else {
                        "Allocated ${result.entryCount} segment${result.entryCount.pluralSuffix()}."
                    }
                    _effects.emit(if (restarted) TimeTrackingEffect.StartService else TimeTrackingEffect.StopService)
                }

                AllocateTrackedTimeResult.NothingToAllocate -> {
                    message.value = "No tracked time is waiting for allocation."
                }

                AllocateTrackedTimeResult.MissingProject -> {
                    message.value = "That project is no longer available."
                }
            }
            dashboardRepository.refresh()
        }
    }

    fun statusLabel(state: TimeTrackingUiState): String = when {
        state.activeEntry != null && state.activeEntry.entry.isAutomaticallyTracked() -> "Auto"
        state.activeEntry != null -> "Manual"
        state.pendingEntries.isNotEmpty() -> "Paused"
        else -> "Ready"
    }
}

private fun Int.pluralSuffix(): String = if (this == 1) "" else "s"
