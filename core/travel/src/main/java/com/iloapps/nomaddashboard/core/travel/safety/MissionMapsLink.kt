package com.iloapps.nomaddashboard.core.travel.safety

import java.util.Locale

/** `geo:` links for a mission, which Android hands to the user's maps app. */
object MissionMapsLink {
    /**
     * A precise coordinate is pinned (`q=lat,lon(label)`). A city-centre coordinate would drop the
     * pin in the wrong street, so those missions are searched by name and city instead, with the
     * coordinate only steering the search towards the right area. Without a valid coordinate the
     * link is a plain search.
     *
     * The query is percent-encoded by hand because a mission name such as "Embassy of Bosnia &
     * Herzegovina" must not be able to split or end the query.
     */
    fun uri(mission: DiplomaticMission): String {
        val hasValidCoordinate = mission.latitude.isFinite() && mission.longitude.isFinite() &&
            mission.latitude in -90.0..90.0 && mission.longitude in -180.0..180.0
        val coordinate = String.format(Locale.ROOT, "%.6f,%.6f", mission.latitude, mission.longitude)
        val center = if (hasValidCoordinate) coordinate else "0,0"

        if (mission.isApproximateLocation) {
            val place = mission.city?.trim()?.takeIf { it.isNotEmpty() }
            val query = listOfNotNull(mission.name, place).joinToString(", ")
            return "geo:$center?q=${encode(query)}"
        }

        val label = encode(mission.name)
        return if (hasValidCoordinate) "geo:$center?q=$coordinate($label)" else "geo:$center?q=$label"
    }

    /** Unreserved characters stay, everything else is escaped; the comma of a coordinate pair stays literal. */
    private fun encode(value: String): String = buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val char = (byte.toInt() and 0xFF).toChar()
            if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "-._~,") {
                append(char)
            } else {
                append('%').append(String.format(Locale.ROOT, "%02X", byte.toInt() and 0xFF))
            }
        }
    }
}
