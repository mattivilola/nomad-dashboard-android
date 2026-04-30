package com.iloapps.nomaddashboard.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iloapps.nomaddashboard.core.model.VisitedPlaceEvent
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "visited_place_events")
data class VisitedPlaceEventEntity(
    @PrimaryKey val id: String,
    val city: String?,
    val region: String?,
    val country: String,
    val countryCode: String?,
    val latitude: Double?,
    val longitude: Double?,
    val source: VisitedPlaceSource,
    val firstObservedAtEpochMillis: Long,
    val lastObservedAtEpochMillis: Long,
    val observedDayIso: String,
)

fun VisitedPlaceEventEntity.toExternalModel(): VisitedPlaceEvent =
    VisitedPlaceEvent(
        id = id,
        city = city,
        region = region,
        country = country,
        countryCode = countryCode,
        latitude = latitude,
        longitude = longitude,
        source = source,
        firstObservedAt = Instant.ofEpochMilli(firstObservedAtEpochMillis),
        lastObservedAt = Instant.ofEpochMilli(lastObservedAtEpochMillis),
        observedDay = LocalDate.parse(observedDayIso),
    )

fun VisitedPlaceEvent.toEntity(): VisitedPlaceEventEntity =
    VisitedPlaceEventEntity(
        id = id,
        city = city,
        region = region,
        country = country,
        countryCode = countryCode,
        latitude = latitude,
        longitude = longitude,
        source = source,
        firstObservedAtEpochMillis = firstObservedAt.toEpochMilli(),
        lastObservedAtEpochMillis = lastObservedAt.toEpochMilli(),
        observedDayIso = observedDay.toString(),
    )
