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
    val sources: List<VisitedPlaceSource>,
    val firstVisitedAt: Instant,
    val lastVisitedAt: Instant,
) {
    val id: String
        get() = visitedPlaceStorageKey(countryCode = countryCode, country = country, city = city)

    val displayName: String
        get() = listOfNotNull(city?.takeIf(String::isNotBlank), country.takeIf(String::isNotBlank))
            .joinToString(", ")
            .ifBlank { country }

    val supportsMapPin: Boolean
        get() = city.isNullOrBlank().not() && latitude != null && longitude != null
}

data class VisitedCountryDay(
    val date: LocalDate,
    val country: String,
    val countryCode: String?,
    val source: VisitedPlaceSource,
    val isInferred: Boolean,
)

data class VisitedPlaceSummary(
    val citiesVisited: Int,
    val countriesVisited: Int,
    val latestVisitAt: Instant?,
)

data class VisitedCountryDaySummaryItem(
    val country: String,
    val countryCode: String?,
    val dayCount: Int,
    val percentage: Double,
) {
    val id: String
        get() = countryCode?.takeIf(String::isNotBlank)
            ?: country.normalizedVisitedKey().orEmpty()
}

data class VisitedCountryDayYearSummary(
    val year: Int,
    val totalTrackedDays: Int,
    val items: List<VisitedCountryDaySummaryItem>,
)

data class VisitedCountryDayMonthSummary(
    val year: Int,
    val month: Int,
    val totalTrackedDays: Int,
    val items: List<VisitedCountryDaySummaryItem>,
    val days: List<VisitedCountryDay>,
) {
    val id: String
        get() = "$year-$month"
}

fun visitedPlaceStorageKey(
    countryCode: String?,
    country: String,
    city: String?,
): String {
    val normalizedCountryCode = countryCode.normalizedCountryCode()
    val normalizedCountry = country.normalizedVisitedKey()
    val normalizedCity = city.normalizedVisitedKey() ?: "__country__"
    return normalizedCountryCode?.let { "$it|$normalizedCity" }
        ?: "${normalizedCountry ?: "__unknown__"}|$normalizedCity"
}

fun List<VisitedPlace>.visitedPlaceSummary(): VisitedPlaceSummary {
    val countryKeys = mapNotNull { place ->
        place.countryCode?.normalizedCountryCode()
            ?: place.country.normalizedVisitedKey()
    }

    return VisitedPlaceSummary(
        citiesVisited = count { it.supportsMapPin },
        countriesVisited = countryKeys.toSet().size,
        latestVisitAt = maxOfOrNull(VisitedPlace::lastVisitedAt),
    )
}

fun List<VisitedCountryDay>.availableYears(): List<Int> =
    map(VisitedCountryDay::date)
        .map(LocalDate::getYear)
        .toSet()
        .sortedDescending()

fun List<VisitedCountryDay>.yearSummary(year: Int): VisitedCountryDayYearSummary? {
    val entries = filter { it.date.year == year }
    if (entries.isEmpty()) {
        return null
    }

    return VisitedCountryDayYearSummary(
        year = year,
        totalTrackedDays = entries.size,
        items = entries.summaryItems(),
    )
}

fun List<VisitedCountryDay>.monthlySummaries(year: Int): List<VisitedCountryDayMonthSummary> {
    val entries = filter { it.date.year == year }
    if (entries.isEmpty()) {
        return emptyList()
    }

    return entries.groupBy { it.date.monthValue }
        .toSortedMap(compareByDescending { it })
        .map { (month, items) ->
            VisitedCountryDayMonthSummary(
                year = year,
                month = month,
                totalTrackedDays = items.size,
                items = items.summaryItems(),
                days = items.sortedBy(VisitedCountryDay::date),
            )
        }
}

private fun List<VisitedCountryDay>.summaryItems(): List<VisitedCountryDaySummaryItem> {
    val totalTrackedDays = size
    return groupBy { SummaryKey(it.country, it.countryCode.normalizedCountryCode()) }
        .map { (key, items) ->
            val dayCount = items.size
            VisitedCountryDaySummaryItem(
                country = key.country,
                countryCode = key.countryCode,
                dayCount = dayCount,
                percentage = dayCount.toDouble() / totalTrackedDays.toDouble(),
            )
        }
        .sortedWith(
            compareByDescending<VisitedCountryDaySummaryItem> { it.dayCount }
                .thenBy { it.country.lowercase() },
        )
}

private data class SummaryKey(
    val country: String,
    val countryCode: String?,
)

private fun String?.normalizedCountryCode(): String? =
    this?.trim()?.takeIf(String::isNotBlank)?.uppercase()

private fun String?.normalizedVisitedKey(): String? =
    this?.trim()
        ?.takeIf(String::isNotBlank)
        ?.lowercase()
        ?.let { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFD) }
        ?.replace("\\p{M}+".toRegex(), "")
