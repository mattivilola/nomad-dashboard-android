package com.iloapps.nomaddashboard.feature.timetracking.runtime

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.data.timetracking.AllocateTrackedTimeResult
import com.iloapps.nomaddashboard.core.data.timetracking.CreateProjectResult
import com.iloapps.nomaddashboard.core.data.timetracking.StartTrackingResult
import com.iloapps.nomaddashboard.core.data.timetracking.StopTrackingResult
import com.iloapps.nomaddashboard.core.data.timetracking.TimeTrackingRepository
import com.iloapps.nomaddashboard.core.data.timetracking.UpdateTimeTrackingEntryResult
import com.iloapps.nomaddashboard.core.model.TimeTrackingEntry
import com.iloapps.nomaddashboard.core.model.TimeTrackingOtherProjectId
import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
import com.iloapps.nomaddashboard.core.model.TimeTrackingRecord
import java.time.Instant
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

class TimeTrackingForegroundServiceRuntimeTest {
    @Test
    fun `notification body includes local start time and elapsed duration`() {
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val session = session(startAt = Instant.parse("2026-04-07T10:00:00Z"))

            assertThat(TimeTrackingNotificationFormatter.title(session)).isEqualTo("Unallocated timer running")
            assertThat(
                TimeTrackingNotificationFormatter.body(
                    session = session,
                    now = Instant.parse("2026-04-07T10:05:30Z"),
                ),
            ).isEqualTo("Started 10:00 · 00:05:30 elapsed")
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun `stop command delegates to repository stop flow`() = runBlocking {
        val repository = FakeTimeTrackingRepository()
        val handler = TimeTrackingServiceCommandHandler(repository)

        val result = handler.handle(TimeTrackingForegroundService.ACTION_STOP_TRACKING)

        assertThat(result).isEqualTo(TimeTrackingServiceCommand.StopService)
        assertThat(repository.stopCalls).isEqualTo(1)
    }

    private fun session(startAt: Instant): TimeTrackingRecord =
        TimeTrackingRecord(
            entry = TimeTrackingEntry(
                id = UUID.fromString("00000000-0000-0000-0000-000000000401"),
                projectId = TimeTrackingOtherProjectId,
                startAt = startAt,
            ),
            project = TimeTrackingProject(
                id = TimeTrackingOtherProjectId,
                name = "Other",
            ),
        )
}

private class FakeTimeTrackingRepository : TimeTrackingRepository {
    var stopCalls = 0

    override val projects: Flow<List<TimeTrackingProject>> = emptyFlow()
    override val recentEntries: Flow<List<TimeTrackingRecord>> = emptyFlow()
    override val pendingEntries: Flow<List<TimeTrackingRecord>> = emptyFlow()
    override val activeEntry: Flow<TimeTrackingRecord?> = emptyFlow()

    override suspend fun currentActiveEntry(): TimeTrackingRecord? = null

    override suspend fun syncTracking(now: Instant) = Unit

    override suspend fun createProject(name: String): CreateProjectResult = CreateProjectResult.InvalidName

    override suspend fun startTracking(): StartTrackingResult = StartTrackingResult.Started

    override suspend fun stopTracking(): StopTrackingResult {
        stopCalls += 1
        return StopTrackingResult.Stopped
    }

    override suspend fun allocateTrackedTime(projectId: UUID): AllocateTrackedTimeResult =
        AllocateTrackedTimeResult.NothingToAllocate

    override suspend fun updateEntry(
        entryId: UUID,
        startAt: Instant,
        endAt: Instant,
    ): UpdateTimeTrackingEntryResult = UpdateTimeTrackingEntryResult.Updated
}
