package com.iloapps.nomaddashboard.core.data.timetracking

import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
import com.iloapps.nomaddashboard.core.model.TimeTrackingRecord
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface TimeTrackingRepository {
    val projects: Flow<List<TimeTrackingProject>>
    val recentEntries: Flow<List<TimeTrackingRecord>>
    val activeEntry: Flow<TimeTrackingRecord?>

    suspend fun currentActiveEntry(): TimeTrackingRecord?
    suspend fun createProject(name: String): CreateProjectResult
    suspend fun startTracking(projectId: UUID): StartTrackingResult
    suspend fun stopTracking(): StopTrackingResult
}

sealed interface CreateProjectResult {
    data class Created(val project: TimeTrackingProject) : CreateProjectResult

    data class Existing(val project: TimeTrackingProject) : CreateProjectResult

    data object InvalidName : CreateProjectResult
}

sealed interface StartTrackingResult {
    data object Started : StartTrackingResult

    data object AlreadyTracking : StartTrackingResult

    data object MissingProject : StartTrackingResult
}

sealed interface StopTrackingResult {
    data object Stopped : StopTrackingResult

    data object NotTracking : StopTrackingResult
}
