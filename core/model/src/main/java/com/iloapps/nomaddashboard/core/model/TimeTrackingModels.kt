package com.iloapps.nomaddashboard.core.model

import java.time.Instant
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

val TimeTrackingOtherProjectId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
const val TimeTrackingOtherProjectName = "Other"
val TimeTrackingFocusLossPerInterruption: Duration = Duration.ofMinutes(23)

data class TimeTrackingProject(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val isArchived: Boolean = false,
)

enum class TimeTrackingBucket {
    UNALLOCATED_AUTO,
    UNALLOCATED_MANUAL,
    ALLOCATED_PROJECT,
    ALLOCATED_OTHER,
}

data class TimeTrackingEntry(
    val id: UUID = UUID.randomUUID(),
    val projectId: UUID,
    val startAt: Instant,
    val endAt: Instant? = null,
    val bucket: String = TimeTrackingBucket.UNALLOCATED_MANUAL.name,
)

data class TimeTrackingRecord(
    val entry: TimeTrackingEntry,
    val project: TimeTrackingProject,
)

data class TimeTrackingInterruption(
    val id: UUID = UUID.randomUUID(),
    val entryId: UUID? = null,
    val occurredAt: Instant,
)

data class TimeTrackingProjectReport(
    val project: TimeTrackingProject,
    val reportedDuration: Duration = Duration.ZERO,
    val interruptionCount: Int = 0,
    val estimatedFocusLoss: Duration = Duration.ZERO,
    val estimatedFocusTime: Duration = Duration.ZERO,
)

data class TimeTrackingDayReport(
    val date: LocalDate,
    val interruptionCount: Int = 0,
    val estimatedFocusLoss: Duration = Duration.ZERO,
    val allocatedDuration: Duration = Duration.ZERO,
    val estimatedFocusTime: Duration = Duration.ZERO,
    val projectReports: List<TimeTrackingProjectReport> = emptyList(),
)

data class TimeTrackingReportSnapshot(
    val interruptionsToday: Int = 0,
    val lastInterruptionAt: Instant? = null,
    val todaysEstimatedFocusLoss: Duration = Duration.ZERO,
    val todaysAllocatedDuration: Duration = Duration.ZERO,
    val todaysEstimatedFocusTime: Duration = Duration.ZERO,
    val dayReports: List<TimeTrackingDayReport> = emptyList(),
)

fun TimeTrackingEntry.isUnallocated(): Boolean =
    bucket == TimeTrackingBucket.UNALLOCATED_AUTO.name || bucket == TimeTrackingBucket.UNALLOCATED_MANUAL.name

fun TimeTrackingEntry.isAutomaticallyTracked(): Boolean =
    bucket == TimeTrackingBucket.UNALLOCATED_AUTO.name

fun TimeTrackingEntry.isOtherAllocation(): Boolean =
    bucket == TimeTrackingBucket.ALLOCATED_OTHER.name
