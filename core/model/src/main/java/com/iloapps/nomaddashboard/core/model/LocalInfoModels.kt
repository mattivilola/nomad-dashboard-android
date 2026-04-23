package com.iloapps.nomaddashboard.core.model

import java.time.Instant
import java.time.LocalDate

enum class LocalInfoStatus {
    OFF,
    CHECKING,
    READY,
    PARTIAL,
    LOCATION_REQUIRED,
    UNSUPPORTED,
    UNAVAILABLE,
}

enum class LocalHolidayPhase {
    TODAY,
    TOMORROW,
    NEXT,
    ON_BREAK,
}

data class HolidayPeriod(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate = startDate,
)

data class LocalHolidayStatus(
    val phase: LocalHolidayPhase,
    val period: HolidayPeriod,
)

data class HolidaySourceAttribution(
    val name: String,
    val url: String? = null,
)

data class LocalInfoSnapshot(
    val status: LocalInfoStatus = LocalInfoStatus.OFF,
    val locality: String? = null,
    val region: String? = null,
    val countryCode: String? = null,
    val countryName: String? = null,
    val timezone: String? = null,
    val matchedSubdivisionCode: String? = null,
    val matchedSubdivisionName: String? = null,
    val publicHoliday: LocalHolidayStatus? = null,
    val schoolHoliday: LocalHolidayStatus? = null,
    val localPriceLevel: LocalPriceLevelSnapshot = LocalPriceLevelSnapshot(),
    val sources: List<HolidaySourceAttribution> = emptyList(),
    val fetchedAt: Instant? = null,
    val detail: String? = "Enable Local Info in Settings.",
    val note: String? = null,
    val isRefreshing: Boolean = false,
)
