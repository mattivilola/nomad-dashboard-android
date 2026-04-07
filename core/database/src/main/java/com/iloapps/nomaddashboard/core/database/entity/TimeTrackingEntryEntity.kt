package com.iloapps.nomaddashboard.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "time_tracking_entries")
data class TimeTrackingEntryEntity(
    @PrimaryKey val id: String,
    val startAtEpochMillis: Long,
    val endAtEpochMillis: Long?,
    val bucket: String,
)

