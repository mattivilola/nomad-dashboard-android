package com.iloapps.nomaddashboard.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import java.time.Instant

@Entity(tableName = "visited_places")
data class VisitedPlaceEntity(
    @PrimaryKey val id: String,
    val city: String?,
    val region: String?,
    val country: String,
    val countryCode: String?,
    val latitude: Double?,
    val longitude: Double?,
    val sources: List<VisitedPlaceSource>,
    val firstVisitedAtEpochMillis: Long,
    val lastVisitedAtEpochMillis: Long,
)

fun VisitedPlaceEntity.toExternalModel(): VisitedPlace =
    VisitedPlace(
        city = city,
        region = region,
        country = country,
        countryCode = countryCode,
        latitude = latitude,
        longitude = longitude,
        sources = sources,
        firstVisitedAt = Instant.ofEpochMilli(firstVisitedAtEpochMillis),
        lastVisitedAt = Instant.ofEpochMilli(lastVisitedAtEpochMillis),
    )

fun VisitedPlace.toEntity(): VisitedPlaceEntity =
    VisitedPlaceEntity(
        id = id,
        city = city,
        region = region,
        country = country,
        countryCode = countryCode,
        latitude = latitude,
        longitude = longitude,
        sources = sources,
        firstVisitedAtEpochMillis = firstVisitedAt.toEpochMilli(),
        lastVisitedAtEpochMillis = lastVisitedAt.toEpochMilli(),
    )
