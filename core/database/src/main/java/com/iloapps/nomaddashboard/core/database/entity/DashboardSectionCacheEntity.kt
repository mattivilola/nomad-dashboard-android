package com.iloapps.nomaddashboard.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dashboard_section_cache")
data class DashboardSectionCacheEntity(
    @PrimaryKey val sectionId: String,
    val payloadJson: String,
    val fetchedAtEpochMillis: Long,
)
