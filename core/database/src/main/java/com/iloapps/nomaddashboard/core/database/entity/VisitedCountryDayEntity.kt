package com.iloapps.nomaddashboard.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedCountryDayOriginalObservation
import com.iloapps.nomaddashboard.core.model.VisitedCountryDayProvenance
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import java.time.LocalDate

@Entity(tableName = "visited_country_days")
data class VisitedCountryDayEntity(
    @PrimaryKey val dateIso: String,
    val country: String,
    val countryCode: String?,
    val source: VisitedPlaceSource,
    val isInferred: Boolean,
    val provenance: VisitedCountryDayProvenance = if (isInferred) VisitedCountryDayProvenance.INFERRED else VisitedCountryDayProvenance.OBSERVED,
    val originalCountry: String? = null,
    val originalCountryCode: String? = null,
    val originalSource: VisitedPlaceSource? = null,
    val originalIsInferred: Boolean? = null,
    val originalObservedAtEpochMillis: Long? = null,
)

fun VisitedCountryDayEntity.toExternalModel(): VisitedCountryDay =
    VisitedCountryDay(
        date = LocalDate.parse(dateIso),
        country = country,
        countryCode = countryCode,
        source = source,
        isInferred = isInferred,
        provenance = provenance,
        originalObservation = originalCountry?.let {
            VisitedCountryDayOriginalObservation(
                country = it,
                countryCode = originalCountryCode,
                source = originalSource ?: source,
                isInferred = originalIsInferred ?: false,
                observedAt = originalObservedAtEpochMillis?.let(java.time.Instant::ofEpochMilli),
            )
        },
    )

fun VisitedCountryDay.toEntity(): VisitedCountryDayEntity =
    VisitedCountryDayEntity(
        dateIso = date.toString(),
        country = country,
        countryCode = countryCode,
        source = source,
        isInferred = isInferred,
        provenance = provenance,
        originalCountry = originalObservation?.country,
        originalCountryCode = originalObservation?.countryCode,
        originalSource = originalObservation?.source,
        originalIsInferred = originalObservation?.isInferred,
        originalObservedAtEpochMillis = originalObservation?.observedAt?.toEpochMilli(),
    )
