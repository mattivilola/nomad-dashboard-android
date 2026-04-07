package com.iloapps.nomaddashboard.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "metric_points")
data class MetricPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val timestampEpochMillis: Long,
    val value: Double,
)

