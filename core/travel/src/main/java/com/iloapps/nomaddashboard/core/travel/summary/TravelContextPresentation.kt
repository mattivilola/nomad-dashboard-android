package com.iloapps.nomaddashboard.core.travel.summary

import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.travel.glance.GlancePlace
import com.iloapps.nomaddashboard.core.travel.glance.trimmedNonEmpty

/**
 * The four rows of the Travel Context card, already condensed: the Wi-Fi name and signal share one
 * row, and the time zone rides along with the location.
 *
 * Android does not report why a lookup failed, so a missing public IP or IP location always reads
 * "Refreshing…" (macOS says "Lookup unavailable" once the lookup failed).
 */
data class TravelContextPresentation(
    val publicIp: String,
    val hasPublicIp: Boolean,
    /** "Nomad Hub · −56 dBm · 720 Mbps". */
    val wifi: String,
    /** Signal and link speed for the long-press hint; null when there is nothing to add. */
    val wifiHelp: String?,
    val vpn: String,
    /** "Valencia, Spain · Europe/Madrid". */
    val location: String,
) {
    companion object {
        /** "−56 dBm": a typographic minus keeps negative signal levels readable at caption size. */
        fun decibelText(value: Int): String = if (value < 0) "−${-value} dBm" else "$value dBm"

        fun of(
            connectivity: ConnectivitySnapshot,
            travelContext: TravelContextSnapshot,
            isPublicIpLocationEnabled: Boolean,
        ): TravelContextPresentation {
            val address = travelContext.publicIp.trimmedNonEmpty()
            val place = GlancePlace.publicIp(travelContext).cityCountryDescription
                ?: if (isPublicIpLocationEnabled) "Refreshing…" else "Location off"
            val timeZone = travelContext.timeZoneId.trimmedNonEmpty() ?: connectivity.timeZoneId.trimmedNonEmpty()

            return TravelContextPresentation(
                publicIp = address ?: "Refreshing…",
                hasPublicIp = address != null,
                wifi = wifiValue(connectivity),
                wifiHelp = wifiHelp(connectivity),
                vpn = if (connectivity.vpnActive) "Active" else "Inactive",
                location = listOfNotNull(place, timeZone).joinToString(" · "),
            )
        }

        private fun hasWifi(connectivity: ConnectivitySnapshot): Boolean =
            connectivity.wifiName.trimmedNonEmpty() != null ||
                connectivity.wifiSignalDbm != null ||
                connectivity.wifiLinkSpeedMbps != null

        private fun wifiValue(connectivity: ConnectivitySnapshot): String {
            if (!hasWifi(connectivity)) return "Not connected"
            return listOfNotNull(
                connectivity.wifiName.trimmedNonEmpty() ?: "Connected",
                connectivity.wifiSignalDbm?.let(::decibelText),
                connectivity.wifiLinkSpeedMbps?.let { "$it Mbps" },
            ).joinToString(" · ")
        }

        private fun wifiHelp(connectivity: ConnectivitySnapshot): String? =
            listOfNotNull(
                connectivity.wifiSignalDbm?.let { "Signal ${decibelText(it)}" },
                connectivity.wifiLinkSpeedMbps?.let { "Transmit rate $it Mbps" },
            ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
