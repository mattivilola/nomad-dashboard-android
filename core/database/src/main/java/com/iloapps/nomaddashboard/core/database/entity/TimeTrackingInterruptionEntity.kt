package com.iloapps.nomaddashboard.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iloapps.nomaddashboard.core.model.TimeTrackingInterruption
import java.time.Instant
import java.util.UUID

@Entity(tableName = "time_tracking_interruptions")
data class TimeTrackingInterruptionEntity(
    @PrimaryKey val id: String,
    val entryId: String?,
    val occurredAtEpochMillis: Long,
) {
    fun toModel(): TimeTrackingInterruption =
        TimeTrackingInterruption(
            id = UUID.fromString(id),
            entryId = entryId?.let(UUID::fromString),
            occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis),
        )
}

fun TimeTrackingInterruption.toEntity(): TimeTrackingInterruptionEntity =
    TimeTrackingInterruptionEntity(
        id = id.toString(),
        entryId = entryId?.toString(),
        occurredAtEpochMillis = occurredAt.toEpochMilli(),
    )
