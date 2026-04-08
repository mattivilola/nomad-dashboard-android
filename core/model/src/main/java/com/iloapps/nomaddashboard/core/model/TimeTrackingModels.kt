package com.iloapps.nomaddashboard.core.model

import java.time.Instant
import java.util.UUID

val TimeTrackingOtherProjectId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
const val TimeTrackingOtherProjectName = "Other"

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

fun TimeTrackingEntry.isUnallocated(): Boolean =
    bucket == TimeTrackingBucket.UNALLOCATED_AUTO.name || bucket == TimeTrackingBucket.UNALLOCATED_MANUAL.name

fun TimeTrackingEntry.isAutomaticallyTracked(): Boolean =
    bucket == TimeTrackingBucket.UNALLOCATED_AUTO.name

fun TimeTrackingEntry.isOtherAllocation(): Boolean =
    bucket == TimeTrackingBucket.ALLOCATED_OTHER.name
