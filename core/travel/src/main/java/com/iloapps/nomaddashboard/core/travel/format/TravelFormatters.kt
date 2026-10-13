package com.iloapps.nomaddashboard.core.travel.format

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** A formatted number and its unit kept apart, so a layout can draw the unit smaller than the number. */
data class FormattedMetric(
    val number: String,
    val unit: String? = null,
) {
    /** "96.0 Mbps": the same text as the plain string formatters. */
    val text: String
        get() = unit?.let { "$number $it" } ?: number
}

/**
 * Shared value formatters, ported from the macOS `NomadFormatters` and `TravelUnitFormatting` so both
 * apps print the same strings. Numbers use a fixed decimal point (as macOS `String(format:)` does);
 * only currency follows the locale.
 */
object TravelFormatters {
    const val Unavailable = "n/a"

    fun megabitsPerSecond(value: Double?): FormattedMetric =
        value?.let { FormattedMetric(fixed(it, 1), "Mbps") } ?: FormattedMetric(Unavailable)

    fun latency(value: Double?): FormattedMetric =
        value?.let { FormattedMetric(fixed(it, 0), "ms") } ?: FormattedMetric(Unavailable)

    fun watts(value: Double?): FormattedMetric =
        value?.let { FormattedMetric(fixed(it, 1), "W") } ?: FormattedMetric(Unavailable)

    fun percentage(value: Double?): String = value?.let { "${fixed(it, 0)}%" } ?: Unavailable

    /** Temperature rounded to whole degrees with a degree sign ("19°", "-3°"); never "-0°". */
    fun temperature(celsius: Double?): String {
        if (celsius == null || celsius.isFinite().not()) return Unavailable
        val rounded = Math.round(celsius)
        return "${if (rounded == 0L) 0L else rounded}°"
    }

    fun kilometersPerHour(value: Double?): String = value?.let { "${fixed(it, 0)} km/h" } ?: Unavailable

    fun metersPerSecond(kilometersPerHour: Double?): String =
        kilometersPerHour?.let { "${fixed(it / 3.6, 1)} m/s" } ?: Unavailable

    /** Distance with one decimal below 10 km and whole kilometres from 10 km up. */
    fun kilometers(value: Double?): String = kilometersMetric(value).text

    /** [kilometers] with the number and the unit kept apart, so the unit can be drawn smaller. */
    fun kilometersMetric(value: Double?): FormattedMetric =
        value?.let { FormattedMetric(fixed(it, if (it >= 10) 0 else 1), "km") } ?: FormattedMetric(Unavailable)

    /** "3h 28m" or "13m". */
    fun minutes(value: Int?): String {
        if (value == null || value < 0) return Unavailable
        val hours = value / 60
        val minutes = value % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    /** 8-point compass label for a meteorological bearing in degrees (normalized to 0..<360). */
    fun compassDirection(degrees: Double): String {
        val normalized = degrees % 360
        val positive = if (normalized >= 0) normalized else normalized + 360
        val index = ((positive + 22.5) / 45.0).toInt() % 8
        return listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[index]
    }

    /**
     * Pump price in the locale's currency style with three decimals ("1,429 €"). The "/L" unit is not
     * part of the string: pass it separately so it can be drawn smaller.
     */
    fun fuelPrice(value: Double, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val formatter = NumberFormat.getCurrencyInstance(locale)
        val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
            ?: return "${fixed(value, 3)} $currencyCode"
        formatter.currency = currency
        formatter.minimumFractionDigits = 3
        formatter.maximumFractionDigits = 3
        return formatter.format(value)
    }

    const val FuelPriceUnit = "/L"

    private fun fixed(value: Double, decimals: Int): String = String.format(Locale.ROOT, "%.${decimals}f", value)
}
