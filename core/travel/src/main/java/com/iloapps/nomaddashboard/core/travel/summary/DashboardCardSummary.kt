package com.iloapps.nomaddashboard.core.travel.summary

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareStatus
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.model.HolidayPeriod
import com.iloapps.nomaddashboard.core.model.LocalInfoStatus
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.travel.format.TravelFormatters
import com.iloapps.nomaddashboard.core.travel.glance.trimmedNonEmpty
import com.iloapps.nomaddashboard.core.travel.health.PowerMetricsPresentation
import com.iloapps.nomaddashboard.core.travel.health.PowerState
import com.iloapps.nomaddashboard.core.travel.health.powerState
import com.iloapps.nomaddashboard.core.travel.safety.CurrentCountry
import com.iloapps.nomaddashboard.core.travel.safety.EmbassyOverviewBuilder
import com.iloapps.nomaddashboard.core.travel.safety.EmergencyNumberDirectory
import com.iloapps.nomaddashboard.core.travel.safety.MissionCoordinate
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max

/**
 * The one-line summary a collapsed card shows next to its title ("28 ms · 96 ↓ 19 ↑"). It leads with
 * what matters and never repeats the status badge shown next to it.
 *
 * @property help Longer wording for accessibility; the same as [text] unless the summary is shortened.
 */
data class DashboardCardSummary(
    val text: String,
    val help: String = text,
) {
    companion object {
        val OFF = DashboardCardSummary("Off")
    }
}

/** Everything the collapsed summaries are decided from. */
data class DashboardCardSummaryInput(
    val snapshot: DashboardSnapshot,
    val settings: AppSettings,
    val now: Instant,
    /** The device's time zone: it decides today's date. */
    val zone: ZoneId,
    /** The recorded country days, for the Stays summary. */
    val countryDays: List<VisitedCountryDay> = emptyList(),
    /** Tracked time that still needs a project allocation today, for the Time Tracking summary. */
    val pendingTimeTracking: Duration = Duration.ZERO,
)

/**
 * Builds the collapsed one-line summary of each dashboard card, ported from the macOS
 * `DashboardCardSummaryPresentation`. Android has no Data Guard card.
 *
 * Where macOS falls back to a section's status subtitle (an unavailable weather or fuel card), this
 * uses the matching `detail` text the Android snapshot carries.
 */
class DashboardCardSummaryBuilder(
    private val locale: Locale = Locale.getDefault(),
    private val embassyOverviewBuilder: EmbassyOverviewBuilder = EmbassyOverviewBuilder(locale),
) {
    fun summarize(card: DashboardCardId, input: DashboardCardSummaryInput): DashboardCardSummary {
        val snapshot = input.snapshot
        val settings = input.settings
        val today = input.now.atZone(input.zone).toLocalDate()

        return when (card) {
            DashboardCardId.CONNECTIVITY -> DashboardCardSummary(connectivity(snapshot))
            DashboardCardId.POWER -> DashboardCardSummary(power(snapshot))
            DashboardCardId.TIME_TRACKING -> DashboardCardSummary(
                if (settings.projectTimeTrackingEnabled) "Pending ${durationText(input.pendingTimeTracking)}" else "Off",
            )

            DashboardCardId.TRAVEL_CONTEXT -> DashboardCardSummary(travelContext(snapshot))
            DashboardCardId.STAYS -> StaysSummary.of(
                settings = settings,
                countryDays = input.countryDays,
                currentCountryCode = currentCountry(snapshot),
                today = today,
                locale = locale,
            )

            DashboardCardId.LOCAL_INFO -> DashboardCardSummary(localInfo(snapshot, settings, today))
            DashboardCardId.FUEL_PRICES -> DashboardCardSummary(fuel(snapshot, settings))
            DashboardCardId.EMERGENCY_CARE -> emergency(snapshot, settings)
            DashboardCardId.EMBASSY -> embassy(snapshot, settings)
            DashboardCardId.TRAVEL_ALERTS -> DashboardCardSummary(travelAlerts(snapshot))
            DashboardCardId.WEATHER -> DashboardCardSummary(weather(snapshot))
        }
    }

    private fun currentCountry(snapshot: DashboardSnapshot): String? =
        CurrentCountry.code(
            deviceCountryCode = snapshot.travelContext.deviceCountryCode,
            ipCountryCode = snapshot.travelContext.countryCode,
            localInfoCountryCode = snapshot.localInfo.countryCode,
        )

    private fun connectivity(snapshot: DashboardSnapshot): String {
        val connectivity = snapshot.connectivity
        val download = connectivity.downloadMbps
        val upload = connectivity.uploadMbps
        val parts = listOfNotNull(
            connectivity.latencyMs?.let { TravelFormatters.latency(it).text },
            if (download != null && upload != null) "${wholeNumber(download)} ↓ ${wholeNumber(upload)} ↑" else null,
        )
        return if (parts.isEmpty()) "Collecting" else parts.joinToString(" · ")
    }

    private fun power(snapshot: DashboardSnapshot): String {
        val power = snapshot.power
        val percent = power.batteryPercent ?: return PowerMetricsPresentation.ESTIMATING
        val metrics = PowerMetricsPresentation.of(power)

        val parts = mutableListOf(TravelFormatters.percentage(percent.toDouble()))
        if (metrics.timeLeftValue != PowerMetricsPresentation.ESTIMATING) {
            parts += if (power.powerState() == PowerState.CHARGING) "${metrics.timeLeftValue} to full" else metrics.timeLeftValue
        }
        return parts.joinToString(" · ")
    }

    /** The badge already says whether the VPN is on, so the line gives the IP and the Wi-Fi name. */
    private fun travelContext(snapshot: DashboardSnapshot): String =
        listOfNotNull(
            snapshot.travelContext.publicIp.trimmedNonEmpty() ?: "IP unavailable",
            snapshot.connectivity.wifiName.trimmedNonEmpty(),
        ).joinToString(" · ")

    /** The badge carries the level, so the line says where the alerts apply. */
    private fun travelAlerts(snapshot: DashboardSnapshot): String {
        val alerts = snapshot.travelAlerts
        if (alerts.enabledKinds.isEmpty()) return "Off"

        val country = alerts.primaryCountryName.trimmedNonEmpty() ?: return "Checking alerts"
        val neighbours = max(alerts.coverageCountryCodes.size - 1, 0)
        return if (neighbours > 0) "$country + $neighbours nearby" else country
    }

    private fun localInfo(snapshot: DashboardSnapshot, settings: AppSettings, today: LocalDate): String {
        if (!settings.localInfoEnabled) return "Off"

        val localInfo = snapshot.localInfo
        if (localInfo.status == LocalInfoStatus.OFF || localInfo.status == LocalInfoStatus.CHECKING) return "Checking"

        val period = localInfo.publicHoliday?.period
        val startsIn = period?.let { daysUntil(it, today) }
        return when {
            period == null || startsIn == null -> localInfo.detail.trimmedNonEmpty() ?: "Checking"
            startsIn == 0 -> "Holiday today"
            startsIn == 1 -> "Holiday tomorrow"
            else -> "Holiday ${DateTimeFormatter.ofPattern("d MMM", locale).format(period.startDate)}"
        }
    }

    /** Whole days from [today] to the period's start; 0 while it is on, null once it is over. */
    private fun daysUntil(period: HolidayPeriod, today: LocalDate): Int? {
        if (period.startDate <= today && today <= period.endDate) return 0
        return ChronoUnit.DAYS.between(today, period.startDate).toInt().takeIf { it > 0 }
    }

    private fun fuel(snapshot: DashboardSnapshot, settings: AppSettings): String {
        if (!settings.fuelPricesEnabled) return "Off"

        val fuel = snapshot.fuelPrices
        val station = (fuel.diesel ?: fuel.gasoline)?.takeIf { fuel.status == FuelPriceStatus.READY }
            ?: return fuel.detail

        val type = when (station.fuelType) {
            FuelType.DIESEL -> "Diesel"
            FuelType.GASOLINE -> "Gasoline"
        }
        val price = TravelFormatters.fuelPrice(station.pricePerLiter, station.currencyCode, locale)
        return "$type $price · ${TravelFormatters.kilometers(station.distanceKilometers)}"
    }

    /** The number first, then how far the nearest hospital is; the hospital's name goes to the help. */
    private fun emergency(snapshot: DashboardSnapshot, settings: AppSettings): DashboardCardSummary {
        if (!settings.emergencyCareEnabled) return DashboardCardSummary.OFF

        val number = EmergencyNumberDirectory.numbersFor(currentCountry(snapshot))?.general
        val care = snapshot.emergencyCare
        val nearest = care.takeIf { it.status == EmergencyCareStatus.READY }
            ?.facilities?.minByOrNull { it.distanceKilometers }

        if (nearest == null) {
            val state = number ?: care.detail
            return DashboardCardSummary(state)
        }

        val distance = TravelFormatters.kilometers(nearest.distanceKilometers)
        val help = listOfNotNull("Nearest: ${nearest.name}, $distance", number?.let { "Emergency $it" }).joinToString(" · ")
        val text = if (number != null) "$number · nearest $distance" else "Nearest $distance · ${nearest.name}"
        return DashboardCardSummary(text, help)
    }

    private fun embassy(snapshot: DashboardSnapshot, settings: AppSettings): DashboardCardSummary {
        val context = snapshot.travelContext
        val coordinate = context.deviceLatitude?.let { latitude ->
            context.deviceLongitude?.let { longitude -> MissionCoordinate(latitude, longitude) }
        }
        val overview = embassyOverviewBuilder.build(
            citizenships = settings.citizenshipCountryCodes,
            hostCountry = currentCountry(snapshot),
            coordinate = coordinate,
        )
        return DashboardCardSummary(overview.summary, overview.summaryHelp)
    }

    private fun weather(snapshot: DashboardSnapshot): String {
        val weather = snapshot.weather
        val temperature = weather.currentTemperatureCelsius
        if (temperature == null && weather.fetchedAt == null) return weather.summary

        return listOfNotNull(
            temperature?.let(TravelFormatters::temperature),
            weather.conditionDescription,
        ).joinToString(" ")
    }

    companion object {
        /** "42m", "3h", "6h 58m": whole minutes, no zero-padded parts. */
        fun durationText(duration: Duration): String {
            val totalMinutes = Math.round(duration.toMillis() / 60_000.0).toInt()
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return when {
                hours == 0 -> "${minutes}m"
                minutes == 0 -> "${hours}h"
                else -> "${hours}h ${minutes}m"
            }
        }

        private fun wholeNumber(value: Double): String = String.format(Locale.ROOT, "%.0f", value)
    }
}
