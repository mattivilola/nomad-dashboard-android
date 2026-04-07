package com.iloapps.nomaddashboard.core.model

import java.time.Instant
import java.util.UUID

data class TimeTrackingProject(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val isArchived: Boolean = false,
)

enum class TimeTrackingBucket {
    OTHER,
    UNALLOCATED,
}

data class TimeTrackingEntry(
    val id: UUID = UUID.randomUUID(),
    val projectId: UUID,
    val startAt: Instant,
    val endAt: Instant? = null,
    val bucket: String = TimeTrackingBucket.UNALLOCATED.name,
)

data class TimeTrackingRecord(
    val entry: TimeTrackingEntry,
    val project: TimeTrackingProject,
)
