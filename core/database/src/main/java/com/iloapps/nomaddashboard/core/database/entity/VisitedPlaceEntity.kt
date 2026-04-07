package com.iloapps.nomaddashboard.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "visited_places")
data class VisitedPlaceEntity(
    @PrimaryKey val id: String,
    val city: String?,
    val region: String?,
    val country: String,
    val countryCode: String?,
    val latitude: Double?,
    val longitude: Double?,
    val source: String,
    val firstVisitedAtEpochMillis: Long,
    val lastVisitedAtEpochMillis: Long,
)

