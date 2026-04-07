package com.iloapps.nomaddashboard.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iloapps.nomaddashboard.core.model.TimeTrackingEntry
import java.time.Instant
import java.util.UUID

@Entity(tableName = "time_tracking_entries")
data class TimeTrackingEntryEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val startAtEpochMillis: Long,
    val endAtEpochMillis: Long?,
    val bucket: String,
) {
    fun toModel(): TimeTrackingEntry =
        TimeTrackingEntry(
            id = UUID.fromString(id),
            projectId = UUID.fromString(projectId),
            startAt = Instant.ofEpochMilli(startAtEpochMillis),
            endAt = endAtEpochMillis?.let(Instant::ofEpochMilli),
            bucket = bucket,
        )
}

fun TimeTrackingEntry.toEntity(): TimeTrackingEntryEntity =
    TimeTrackingEntryEntity(
        id = id.toString(),
        projectId = projectId.toString(),
        startAtEpochMillis = startAt.toEpochMilli(),
        endAtEpochMillis = endAt?.toEpochMilli(),
        bucket = bucket,
    )
