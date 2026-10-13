package com.iloapps.nomaddashboard.core.travel.safety

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Bundled, offline directory of embassies and consulates.
//
// Source: Wikidata (CC0), extracted at build time by the macOS repository's
// `scripts/update-diplomatic-missions.sh` into `diplomatic-missions.json`, which
// `scripts/sync-macos-datasets.sh` copies here. Nothing is fetched at runtime and the user's
// citizenship and location are only ever compared against this local dataset.
//
// The dataset is community maintained: it can be incomplete or stale, and most coordinates are the
// centre of the mission's city rather than the building (see `isApproximateLocation`). Always send
// users to the mission's own website or their government's travel pages for opening hours,
// telephone numbers and emergency contacts.

/** What kind of diplomatic post a mission is. Serial names are the on-disk strings. */
@Serializable
enum class DiplomaticMissionKind(val title: String) {
    @SerialName("embassy")
    Embassy("Embassy"),

    @SerialName("consulateGeneral")
    ConsulateGeneral("Consulate-general"),

    @SerialName("consulate")
    Consulate("Consulate"),

    @SerialName("honoraryConsulate")
    HonoraryConsulate("Honorary consulate"),

    @SerialName("other")
    Other("Mission"),
    ;

    /**
     * Lower is more capable for a citizen in trouble: embassies and consulates-general issue
     * emergency travel documents and run consular crisis response, plain consulates handle most
     * consular services, honorary consulates are staffed by a local volunteer with limited powers.
     */
    internal val rank: Int
        get() = when (this) {
            Embassy -> 0
            ConsulateGeneral -> 1
            Consulate -> 2
            Other -> 3
            HonoraryConsulate -> 4
        }

    /**
     * Honorary consulates are run by a local volunteer and usually cannot issue passports or
     * emergency travel documents, so they are never ranked ahead of a career mission.
     */
    val isHonorary: Boolean
        get() = this == HonoraryConsulate
}

/**
 * One embassy, consulate or similar post of a sending state in a host country.
 *
 * Short on-disk keys keep the bundled file around 2 MB (about 10,000 missions).
 */
@Serializable
data class DiplomaticMission(
    /** Wikidata item id, for example `Q106303613`. */
    @SerialName("i") val id: String,
    /** Upper-case ISO 3166-1 alpha-2 code of the state that operates the mission. */
    @SerialName("s") val sendingCountry: String,
    /** Upper-case ISO 3166-1 alpha-2 code of the country the mission is in. */
    @SerialName("h") val hostCountry: String,
    @SerialName("k") val kind: DiplomaticMissionKind,
    @SerialName("n") val name: String,
    @SerialName("c") val city: String? = null,
    @SerialName("la") val latitude: Double,
    @SerialName("lo") val longitude: Double,
    /** Street address as recorded on Wikidata, in the language it was entered in. */
    @SerialName("a") val address: String? = null,
    /** Official website, always an http(s) URL string. */
    @SerialName("w") val website: String? = null,
    /**
     * True when the coordinate is the centre of the city or district rather than the building.
     * Good for "nearest mission" ranking, wrong for dropping a map pin on a street.
     */
    @SerialName("x") val isApproximateLocation: Boolean = false,
)

/** The on-disk file: a provenance header plus the missions. */
@Serializable
data class DiplomaticMissionDataset(
    val source: String = WikidataSource,
    /** ISO 8601 calendar date (`yyyy-MM-dd`) the dataset was generated. */
    val generatedAt: String,
    val missions: List<DiplomaticMission>,
) {
    companion object {
        const val WikidataSource = "Wikidata (CC0)"
    }
}

/** A WGS 84 position in decimal degrees. */
data class MissionCoordinate(
    val latitude: Double,
    val longitude: Double,
)

/** A mission together with how far it is from the traveller, when the position is known. */
data class DiplomaticMissionMatch(
    val mission: DiplomaticMission,
    /** Great-circle distance in kilometres from the supplied coordinate, null without a coordinate. */
    val distanceKilometers: Double?,
) {
    val id: String
        get() = mission.id
}
