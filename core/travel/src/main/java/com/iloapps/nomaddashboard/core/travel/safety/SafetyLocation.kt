package com.iloapps.nomaddashboard.core.travel.safety

import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import kotlin.math.roundToInt

/**
 * Where the traveller is, for the safety surfaces (the Your Embassy card and the saved essentials).
 * Location is sensitive: this only ever feeds the offline dataset and never leaves the device.
 *
 * The country is the device location's, then the IP location's, then the Local Info card's. The
 * coordinate is the device's when there is one, otherwise the IP lookup's (city level, so
 * [isCoordinateApproximate] is set and distances are shown as "~4 km").
 */
data class SafetyLocation(
    val countryCode: String?,
    val coordinate: MissionCoordinate?,
    val isCoordinateApproximate: Boolean,
) {
    companion object {
        fun of(travelContext: TravelContextSnapshot, localInfoCountryCode: String?): SafetyLocation {
            val country = CurrentCountry.code(
                deviceCountryCode = travelContext.deviceCountryCode,
                ipCountryCode = travelContext.countryCode,
                localInfoCountryCode = localInfoCountryCode,
            )
            val device = coordinate(travelContext.deviceLatitude, travelContext.deviceLongitude)
            if (device != null) return SafetyLocation(country, device, isCoordinateApproximate = false)

            val ip = coordinate(travelContext.latitude, travelContext.longitude)
            return SafetyLocation(country, ip, isCoordinateApproximate = ip != null)
        }

        private fun coordinate(latitude: Double?, longitude: Double?): MissionCoordinate? =
            if (latitude != null && longitude != null) MissionCoordinate(latitude, longitude) else null
    }
}

/**
 * The overview for [citizenships] at [location]. When the position is only an IP lookup, every
 * distance reads as approximate ("~4 km") whatever the mission's own precision.
 */
fun EmbassyOverviewBuilder.build(citizenships: List<String>, location: SafetyLocation): EmbassyOverview {
    val overview = build(citizenships, location.countryCode, location.coordinate)
    return if (location.isCoordinateApproximate) overview.withApproximateDistances() else overview
}

private val KilometersText = Regex("""~?(\d+(?:\.\d+)?) km""")

internal fun EmbassyOverview.withApproximateDistances(): EmbassyOverview {
    var summary = summary
    var summaryHelp = summaryHelp

    fun approximate(row: EmbassyOverview.MissionRow): EmbassyOverview.MissionRow {
        val original = row.distanceText ?: return row
        val kilometers = KilometersText.matchEntire(original)?.groupValues?.get(1)?.toDoubleOrNull() ?: return row
        val approximateText = "~${maxOf(1, kilometers.roundToInt())} km"
        summary = summary.replace(original, approximateText)
        summaryHelp = summaryHelp.replace(original, approximateText)
        return row.copy(distanceText = approximateText)
    }

    val missions = missions.map(::approximate)
    val fallbackMissions = fallbackMissions.map(::approximate)
    return copy(missions = missions, fallbackMissions = fallbackMissions, summary = summary, summaryHelp = summaryHelp)
}
