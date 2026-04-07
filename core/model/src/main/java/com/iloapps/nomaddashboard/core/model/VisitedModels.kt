package com.iloapps.nomaddashboard.core.model

import java.time.Instant
import java.time.LocalDate

enum class VisitedPlaceSource {
    DEVICE_LOCATION,
    PUBLIC_IP_GEOLOCATION,
}

data class VisitedPlace(
    val city: String?,
    val region: String?,
    val country: String,
    val countryCode: String?,
    val latitude: Double?,
    val longitude: Double?,
    val source: VisitedPlaceSource,
    val firstVisitedAt: Instant,
    val lastVisitedAt: Instant,
)

data class VisitedCountryDay(
    val date: LocalDate,
    val country: String,
    val countryCode: String?,
)

