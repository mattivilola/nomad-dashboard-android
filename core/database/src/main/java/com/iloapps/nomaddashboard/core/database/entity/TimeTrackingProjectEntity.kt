package com.iloapps.nomaddashboard.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
import java.util.UUID

@Entity(tableName = "time_tracking_projects")
data class TimeTrackingProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isArchived: Boolean,
) {
    fun toModel(): TimeTrackingProject =
        TimeTrackingProject(
            id = UUID.fromString(id),
            name = name,
            isArchived = isArchived,
        )
}

fun TimeTrackingProject.toEntity(): TimeTrackingProjectEntity =
    TimeTrackingProjectEntity(
        id = id.toString(),
        name = name,
        isArchived = isArchived,
    )
