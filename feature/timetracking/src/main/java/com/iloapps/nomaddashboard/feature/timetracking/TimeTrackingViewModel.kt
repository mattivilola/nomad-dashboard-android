package com.iloapps.nomaddashboard.feature.timetracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iloapps.nomaddashboard.core.data.repository.NomadDashboardRepository
import com.iloapps.nomaddashboard.core.data.timetracking.CreateProjectResult
import com.iloapps.nomaddashboard.core.data.timetracking.StartTrackingResult
import com.iloapps.nomaddashboard.core.data.timetracking.StopTrackingResult
import com.iloapps.nomaddashboard.core.data.timetracking.TimeTrackingRepository
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
import com.iloapps.nomaddashboard.core.model.TimeTrackingRecord
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
    val recentEntries: List<TimeTrackingRecord> = emptyList(),
    val activeEntry: TimeTrackingRecord? = null,
    val selectedProjectId: UUID? = null,
    val draftProjectName: String = "",
    val message: String? = null,
)

sealed interface TimeTrackingEffect {
    data object StartService : TimeTrackingEffect

    data object StopService : TimeTrackingEffect
}

private data class TrackingInputs(
    val selectedProjectId: String?,
    val draftProjectName: String,
    val message: String?,
)

@HiltViewModel
class TimeTrackingViewModel @Inject constructor(
    private val dashboardRepository: NomadDashboardRepository,
    private val timeTrackingRepository: TimeTrackingRepository,
) : ViewModel() {
    private val selectedProjectId = MutableStateFlow<String?>(null)
    private val draftProjectName = MutableStateFlow("")
    private val message = MutableStateFlow<String?>(null)
    private val _effects = MutableSharedFlow<TimeTrackingEffect>()

    val effects = _effects.asSharedFlow()

    private val inputs = combine(
        selectedProjectId,
        draftProjectName,
        message,
        ::TrackingInputs,
    )

    val uiState: StateFlow<TimeTrackingUiState> = combine(
        dashboardRepository.settings,
        timeTrackingRepository.projects,
        timeTrackingRepository.recentEntries,
        timeTrackingRepository.activeEntry,
        inputs,
    ) { settings, projects, recentEntries, activeEntry, inputs ->
        val selected = activeEntry?.project?.id
            ?: projects.firstOrNull { it.id.toString() == inputs.selectedProjectId }?.id
            ?: projects.firstOrNull()?.id
        TimeTrackingUiState(
            settings = settings,
            projects = projects,
            recentEntries = recentEntries,
            activeEntry = activeEntry,
            selectedProjectId = selected,
            draftProjectName = inputs.draftProjectName,
            message = inputs.message,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TimeTrackingUiState(),
    )

    fun selectProject(projectId: UUID) {
        selectedProjectId.value = projectId.toString()
        message.value = null
    }

    fun updateDraftProjectName(value: String) {
        draftProjectName.value = value
        message.value = null
    }

    fun createProject() {
        viewModelScope.launch {
            when (val result = timeTrackingRepository.createProject(draftProjectName.value)) {
                is CreateProjectResult.Created -> {
                    selectedProjectId.value = result.project.id.toString()
                    draftProjectName.value = ""
                    message.value = null
                }

                is CreateProjectResult.Existing -> {
                    selectedProjectId.value = result.project.id.toString()
                    draftProjectName.value = ""
                    message.value = "Project already exists. Selected ${result.project.name}."
                }

                CreateProjectResult.InvalidName -> {
                    message.value = "Enter a project name before adding it."
                }
            }
        }
    }

    fun startTracking() {
        val projectId = uiState.value.selectedProjectId
        if (projectId == null) {
            message.value = "Create or select a project before starting tracking."
            return
        }

        viewModelScope.launch {
            when (timeTrackingRepository.startTracking(projectId)) {
                StartTrackingResult.Started -> {
                    message.value = null
                    _effects.emit(TimeTrackingEffect.StartService)
                }

                StartTrackingResult.AlreadyTracking -> {
                    message.value = "An active time-tracking session is already running."
                    _effects.emit(TimeTrackingEffect.StartService)
                }

                StartTrackingResult.MissingProject -> {
                    message.value = "The selected project is no longer available."
                }
            }
            dashboardRepository.refresh()
        }
    }

    fun stopTracking() {
        viewModelScope.launch {
            when (timeTrackingRepository.stopTracking()) {
                StopTrackingResult.Stopped -> {
                    message.value = null
                    _effects.emit(TimeTrackingEffect.StopService)
                }

                StopTrackingResult.NotTracking -> {
                    _effects.emit(TimeTrackingEffect.StopService)
                }
            }
            dashboardRepository.refresh()
        }
    }
}
