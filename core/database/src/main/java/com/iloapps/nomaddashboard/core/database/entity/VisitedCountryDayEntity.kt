package com.iloapps.nomaddashboard.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import java.time.LocalDate

@Entity(tableName = "visited_country_days")
data class VisitedCountryDayEntity(
    @PrimaryKey val dateIso: String,
    val country: String,
    val countryCode: String?,
    val source: VisitedPlaceSource,
    val isInferred: Boolean,
)

fun VisitedCountryDayEntity.toExternalModel(): VisitedCountryDay =
    VisitedCountryDay(
        date = LocalDate.parse(dateIso),
        country = country,
        countryCode = countryCode,
        source = source,
        isInferred = isInferred,
    )

fun VisitedCountryDay.toEntity(): VisitedCountryDayEntity =
    VisitedCountryDayEntity(
        dateIso = date.toString(),
        country = country,
        countryCode = countryCode,
        source = source,
        isInferred = isInferred,
    )
