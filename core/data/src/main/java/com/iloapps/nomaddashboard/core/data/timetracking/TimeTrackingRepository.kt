package com.iloapps.nomaddashboard.core.data.timetracking

import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
import com.iloapps.nomaddashboard.core.model.TimeTrackingRecord
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface TimeTrackingRepository {
    val projects: Flow<List<TimeTrackingProject>>
    val recentEntries: Flow<List<TimeTrackingRecord>>
    val pendingEntries: Flow<List<TimeTrackingRecord>>
    val activeEntry: Flow<TimeTrackingRecord?>

    suspend fun currentActiveEntry(): TimeTrackingRecord?
    suspend fun syncTracking(now: Instant = Instant.now())
    suspend fun createProject(name: String): CreateProjectResult
    suspend fun startTracking(): StartTrackingResult
    suspend fun stopTracking(): StopTrackingResult
    suspend fun allocateTrackedTime(projectId: UUID): AllocateTrackedTimeResult
    suspend fun updateEntry(
        entryId: UUID,
        startAt: Instant,
        endAt: Instant,
    ): UpdateTimeTrackingEntryResult
}

sealed interface CreateProjectResult {
    data class Created(val project: TimeTrackingProject) : CreateProjectResult

    data class Existing(val project: TimeTrackingProject) : CreateProjectResult

    data object InvalidName : CreateProjectResult
}

sealed interface StartTrackingResult {
    data object Started : StartTrackingResult

    data object AlreadyTracking : StartTrackingResult
}

sealed interface StopTrackingResult {
    data object Stopped : StopTrackingResult

    data object NotTracking : StopTrackingResult
}

sealed interface AllocateTrackedTimeResult {
    data class Allocated(val entryCount: Int) : AllocateTrackedTimeResult

    data object NothingToAllocate : AllocateTrackedTimeResult

    data object MissingProject : AllocateTrackedTimeResult
}

sealed interface UpdateTimeTrackingEntryResult {
    data object Updated : UpdateTimeTrackingEntryResult

    data object MissingEntry : UpdateTimeTrackingEntryResult

    data object InvalidRange : UpdateTimeTrackingEntryResult
}
