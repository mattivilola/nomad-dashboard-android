package com.iloapps.nomaddashboard.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "visited_country_days")
data class VisitedCountryDayEntity(
    @PrimaryKey val dateIso: String,
    val country: String,
    val countryCode: String?,
)

