package com.iloapps.nomaddashboard.core.travel.cardcontent

import com.iloapps.nomaddashboard.core.model.EmergencyCareFacility
import com.iloapps.nomaddashboard.core.model.EmergencyCareSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareStatus
import com.iloapps.nomaddashboard.core.model.HospitalOwnership
import com.iloapps.nomaddashboard.core.travel.format.FormattedMetric
import com.iloapps.nomaddashboard.core.travel.format.TravelFormatters
import com.iloapps.nomaddashboard.core.travel.safety.EmergencyNumbers

/**
 * One hospital of the Emergency Care card.
 *
 * @property distance The distance with its unit apart ("2.4" and "km").
 * @property ownership "Public" or "Private", shown as a neutral chip; null when unknown (nothing is shown).
 */
data class EmergencyHospitalRow(
    val id: String,
    val name: String,
    val detail: String,
    val distance: FormattedMetric,
    val ownership: String?,
    val latitude: Double,
    val longitude: Double,
)

/** What the Emergency Care card says. Ported from the macOS `EmergencyCareSectionPresentation`. */
object EmergencyCarePresentation {
    /**
     * "Within 25 km · Emergency 112": the search radius, then the offline emergency number of the current
     * country, which stays when the hospital search fails and is left out when the country is not in the
     * directory.
     *
     * @param waitingForLocation the startup location check is still running.
     */
    fun subtitle(
        enabled: Boolean,
        waitingForLocation: Boolean,
        snapshot: EmergencyCareSnapshot,
        numbers: EmergencyNumbers?,
    ): String {
        if (!enabled) return "Off"

        val base = when {
            waitingForLocation -> "Checking device location before nearby search"
            else -> when (snapshot.status) {
                EmergencyCareStatus.LOADING -> "Looking for nearby hospitals"
                EmergencyCareStatus.READY -> "Within ${radius(snapshot.searchRadiusKilometers)} km"
                EmergencyCareStatus.CONFIGURATION_REQUIRED -> "Configuration"
                EmergencyCareStatus.PERMISSION_REQUIRED -> "Precise location is required"
                EmergencyCareStatus.UNAVAILABLE,
                EmergencyCareStatus.ERROR,
                -> snapshot.sourceName
            }
        }
        return listOfNotNull(base, numbers?.headline).joinToString(" · ")
    }

    /** The hospitals, nearest first. */
    fun hospitals(snapshot: EmergencyCareSnapshot): List<EmergencyHospitalRow> =
        snapshot.facilities
            .sortedBy { it.distanceKilometers }
            .map { facility ->
                EmergencyHospitalRow(
                    id = facility.placeId ?: "${facility.name}@${facility.latitude},${facility.longitude}",
                    name = facility.name,
                    detail = facility.address.orEmpty(),
                    distance = TravelFormatters.kilometersMetric(facility.distanceKilometers),
                    ownership = facility.ownershipTitle(),
                    latitude = facility.latitude,
                    longitude = facility.longitude,
                )
            }

    /** The search radius is 25, 50 or 100 km; a fractional radius keeps its decimal. */
    private fun radius(kilometers: Double): String =
        if (kilometers % 1.0 == 0.0) kilometers.toInt().toString() else kilometers.toString()

    private fun EmergencyCareFacility.ownershipTitle(): String? =
        ownership.takeIf { it != HospitalOwnership.UNKNOWN }?.displayName
}
