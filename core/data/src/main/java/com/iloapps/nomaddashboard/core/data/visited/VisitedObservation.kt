package com.iloapps.nomaddashboard.core.data.visited

import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import java.time.Instant

data class VisitedObservation(
    val city: String?,
    val region: String?,
    val country: String,
    val countryCode: String?,
    val latitude: Double?,
    val longitude: Double?,
    val source: VisitedPlaceSource,
    val observedAt: Instant,
)
