package com.iloapps.nomaddashboard.core.travel.cards

import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.travel.glance.trimmedNonEmpty
import com.iloapps.nomaddashboard.core.travel.summary.TravelContextPresentation
import java.util.Locale

/** How the device's own location compares with where the public IP says the connection is. */
enum class LocationAlignment(val label: String, val tone: CardTone) {
    ALIGNED("Aligned", CardTone.GOOD),
    NEARBY("Nearby", CardTone.NEUTRAL),
    MISMATCH("Mismatch", CardTone.CAUTION),
}

/** One line of the "Device vs network" comparison the Travel Context card keeps behind its details row. */
data class TravelContextComparisonRow(
    val label: String,
    val value: String,
    val supporting: String? = null,
    /** The status shown next to the value ("Ready", "Live", "Permission"), or null. */
    val status: String? = null,
    val statusTone: CardTone = CardTone.NEUTRAL,
)

/**
 * The Travel Context card: the four rows (Public IP, Wi-Fi, VPN, Location) plus the comparison of the
 * device location with the network's, which stays reachable in the card's details.
 *
 * The Location row names the device's own place when Android resolved one, and the public IP's place
 * otherwise, so it matches the map action; the comparison rows show both.
 */
data class TravelContextCardPresentation(
    val publicIp: String,
    val hasPublicIp: Boolean,
    /** "Nomad Hub · −56 dBm · 720 Mbps". */
    val wifi: String,
    val wifiHelp: String?,
    /** Android reports only whether a VPN is up, never its name. */
    val vpn: String,
    /** "Valencia, Spain · Europe/Madrid". */
    val location: String,
    val alignment: LocationAlignment?,
    val comparison: List<TravelContextComparisonRow>,
) {
    companion object {
        const val PUBLIC_IP_OFF = "Off in Settings"
        const val NO_LOCATION = "Location unavailable"

        fun of(
            connectivity: ConnectivitySnapshot,
            travelContext: TravelContextSnapshot,
            isPublicIpLocationEnabled: Boolean,
            hasLocationPermission: Boolean,
            isCheckingDeviceLocation: Boolean,
        ): TravelContextCardPresentation {
            val rows = TravelContextPresentation.of(connectivity, travelContext, isPublicIpLocationEnabled)
            val hasDevice = TravelContextLabels.hasDeviceLocation(travelContext)

            val location = if (hasDevice) {
                listOfNotNull(
                    TravelContextLabels.deviceLocationLabel(travelContext),
                    connectivity.timeZoneId.trimmedNonEmpty(),
                ).joinToString(" · ")
            } else {
                rows.location
            }

            return TravelContextCardPresentation(
                publicIp = if (!isPublicIpLocationEnabled && !rows.hasPublicIp) PUBLIC_IP_OFF else rows.publicIp,
                hasPublicIp = rows.hasPublicIp,
                wifi = rows.wifi,
                wifiHelp = rows.wifiHelp,
                vpn = rows.vpn,
                location = location,
                alignment = alignment(travelContext),
                comparison = comparison(
                    connectivity = connectivity,
                    travelContext = travelContext,
                    isPublicIpLocationEnabled = isPublicIpLocationEnabled,
                    hasLocationPermission = hasLocationPermission,
                    isCheckingDeviceLocation = isCheckingDeviceLocation,
                ),
            )
        }

        fun alignment(travelContext: TravelContextSnapshot): LocationAlignment? {
            if (!TravelContextLabels.hasDeviceLocation(travelContext) || !TravelContextLabels.hasIpLocation(travelContext)) {
                return null
            }
            val sameCountry = travelContext.deviceCountryCode.equals(travelContext.countryCode, ignoreCase = true) &&
                travelContext.deviceCountryCode != null
            val sameCity = travelContext.deviceCity.equals(travelContext.city, ignoreCase = true) && travelContext.deviceCity != null
            return when {
                sameCountry && (sameCity || travelContext.deviceCity == null || travelContext.city == null) -> LocationAlignment.ALIGNED
                sameCountry -> LocationAlignment.NEARBY
                else -> LocationAlignment.MISMATCH
            }
        }

        private fun comparison(
            connectivity: ConnectivitySnapshot,
            travelContext: TravelContextSnapshot,
            isPublicIpLocationEnabled: Boolean,
            hasLocationPermission: Boolean,
            isCheckingDeviceLocation: Boolean,
        ): List<TravelContextComparisonRow> {
            val hasDevice = TravelContextLabels.hasDeviceLocation(travelContext)
            val hasIp = TravelContextLabels.hasIpLocation(travelContext)

            val device = TravelContextComparisonRow(
                label = "Device",
                value = TravelContextLabels.deviceLocationLabel(travelContext),
                supporting = when {
                    isCheckingDeviceLocation -> "Android is resolving current coordinates and place details."
                    hasDevice -> listOfNotNull(travelContext.deviceRegion, travelContext.deviceCountry).joinToString(" · ")
                        .ifBlank { "Resolved from Android location services." }
                    !hasLocationPermission -> "Allow location to compare the device's physical position."
                    else -> "Location permission is granted, but Android has not resolved a place yet."
                },
                status = when {
                    isCheckingDeviceLocation -> "Checking"
                    hasDevice -> "Ready"
                    !hasLocationPermission -> "Permission"
                    else -> "Waiting"
                },
                statusTone = when {
                    isCheckingDeviceLocation -> CardTone.NEUTRAL
                    hasDevice -> CardTone.GOOD
                    !hasLocationPermission -> CardTone.CAUTION
                    else -> CardTone.NEUTRAL
                },
            )
            val network = TravelContextComparisonRow(
                label = "Public IP",
                value = when {
                    !isPublicIpLocationEnabled -> PUBLIC_IP_OFF
                    hasIp -> TravelContextLabels.ipLocationLabel(travelContext)
                    else -> "Unavailable"
                },
                supporting = when {
                    !isPublicIpLocationEnabled -> "Turn on external IP location to compare network identity."
                    hasIp -> listOfNotNull(travelContext.city, travelContext.region, travelContext.country).joinToString(" · ")
                        .ifBlank { "Network-derived location" }
                    else -> "No IP-based location was resolved on the last refresh."
                },
                status = when {
                    !isPublicIpLocationEnabled -> "Off"
                    hasIp -> "Live"
                    else -> "Unavailable"
                },
                statusTone = when {
                    !isPublicIpLocationEnabled -> CardTone.NEUTRAL
                    hasIp -> CardTone.GOOD
                    else -> CardTone.CAUTION
                },
            )
            val timeZone = TravelContextComparisonRow(
                label = "Time zone",
                value = timeZoneSummary(connectivity, travelContext),
                supporting = "Device time zone first; the IP's appears when it differs.",
            )
            val signal = signalSummary(connectivity)?.let { summary ->
                TravelContextComparisonRow(
                    label = "Signal",
                    value = summary,
                    supporting = "Android exposes RSSI, link speed and band when available.",
                )
            }
            val vpn = TravelContextComparisonRow(
                label = "VPN",
                value = if (connectivity.vpnActive) "Active" else "Inactive",
                supporting = if (connectivity.vpnActive) {
                    "Public IP location may differ from your physical device location."
                } else {
                    "Network identity matches the device's direct connection."
                },
            )
            return listOfNotNull(device, network, timeZone, signal, vpn)
        }

        private fun timeZoneSummary(connectivity: ConnectivitySnapshot, travelContext: TravelContextSnapshot): String {
            val deviceZone = connectivity.timeZoneId
            val ipZone = travelContext.timeZoneId
            return if (ipZone.isNullOrBlank() || ipZone == deviceZone) deviceZone else "Device $deviceZone · IP $ipZone"
        }

        private fun signalSummary(connectivity: ConnectivitySnapshot): String? =
            listOfNotNull(
                connectivity.wifiSignalDbm?.let { "RSSI $it" },
                connectivity.wifiLinkSpeedMbps?.let { "$it Mbps" },
                connectivity.wifiFrequencyMhz?.let(::wifiBand),
            ).takeIf { it.isNotEmpty() }?.joinToString(" · ")

        /** "5 GHz": the Wi-Fi band of a channel frequency. */
        fun wifiBand(frequencyMhz: Int): String =
            when (frequencyMhz) {
                in 2400..2500 -> "2.4 GHz"
                in 4900..5900 -> "5 GHz"
                in 5925..7125 -> "6 GHz"
                else -> "$frequencyMhz MHz"
            }
    }
}

/** The place names of the Travel Context snapshot, shared by the card, the map action and the header. */
object TravelContextLabels {
    fun hasDeviceLocation(context: TravelContextSnapshot): Boolean =
        context.deviceCountry != null || context.deviceLatitude != null || context.deviceLongitude != null

    fun hasIpLocation(context: TravelContextSnapshot): Boolean =
        context.country != null || context.latitude != null || context.longitude != null

    /** "Valencia, Spain"; the country alone, then the coordinates, then "Location unavailable". */
    fun deviceLocationLabel(context: TravelContextSnapshot): String =
        listOfNotNull(context.deviceCity, context.deviceCountry).joinToString(", ").ifBlank {
            context.deviceCountry ?: coordinates(context.deviceLatitude, context.deviceLongitude)
                ?: TravelContextCardPresentation.NO_LOCATION
        }

    fun ipLocationLabel(context: TravelContextSnapshot): String =
        listOfNotNull(context.city, context.country).joinToString(", ").ifBlank {
            context.country ?: TravelContextCardPresentation.NO_LOCATION
        }

    private fun coordinates(latitude: Double?, longitude: Double?): String? =
        if (latitude != null && longitude != null) String.format(Locale.US, "%.3f, %.3f", latitude, longitude) else null
}
