package com.iloapps.nomaddashboard.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

enum class LocalPriceLevelStatus {
    READY,
    PARTIAL,
    LOCATION_REQUIRED,
    CONFIGURATION_REQUIRED,
    UNSUPPORTED,
    UNAVAILABLE,
}

enum class LocalPriceSummaryBand {
    LOW,
    MEDIUM,
    HIGH,
    LIMITED,
}

enum class LocalPriceIndicatorKind {
    MEAL_OUT,
    GROCERIES,
    RENT_ONE_BEDROOM,
    OVERALL,
}

enum class LocalPricePrecision {
    COUNTRY_FALLBACK,
    COUNTY_BENCHMARK,
    METRO_BENCHMARK,
}

@Serializable
data class LocalPriceIndicatorRow(
    val kind: LocalPriceIndicatorKind,
    val value: String,
    val detail: String,
    val precision: LocalPricePrecision,
    val source: String,
)

data class LocalPriceLevelSnapshot(
    val status: LocalPriceLevelStatus = LocalPriceLevelStatus.UNAVAILABLE,
    val summaryBand: LocalPriceSummaryBand? = null,
    val countryCode: String? = null,
    val countryName: String? = null,
    val rows: List<LocalPriceIndicatorRow> = emptyList(),
    val sources: List<String> = emptyList(),
    val fetchedAt: Instant? = null,
    val detail: String? = "Local price signals unavailable.",
    val note: String? = null,
)
