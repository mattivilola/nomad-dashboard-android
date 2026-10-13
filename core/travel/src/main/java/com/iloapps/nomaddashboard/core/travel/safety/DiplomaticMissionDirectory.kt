package com.iloapps.nomaddashboard.core.travel.safety

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object DiplomaticMissionDirectory {
    /**
     * The 27 EU member states (ISO 3166-1 alpha-2), as of 2026. Their citizens are entitled to
     * consular protection from any other member state's mission when their own state has none in
     * the country (TFEU Art. 20(2)(c), Art. 23; Council Directive (EU) 2015/637).
     */
    val euMemberStates: List<String> = listOf(
        "AT", "BE", "BG", "CY", "CZ", "DE", "DK", "EE", "ES", "FI", "FR", "GR", "HR", "HU",
        "IE", "IT", "LT", "LU", "LV", "MT", "NL", "PL", "PT", "RO", "SE", "SI", "SK",
    )

    /**
     * Nordic states (ISO 3166-1 alpha-2). Nordic cooperation (Helsinki Treaty and Nordic consular
     * cooperation): foreign-service officials assist citizens of other Nordic countries that are not
     * represented locally. This is a cooperative fallback, not an individual legal entitlement.
     * Norway and Iceland are not EU members, so this is the only fallback they get.
     */
    val nordicStates: List<String> = listOf("DK", "FI", "IS", "NO", "SE")

    /**
     * Distances closer than this are treated as equally near, because a city-level coordinate is
     * accurate to about this much. Within the same bucket the more capable kind wins.
     */
    internal const val DistanceBucketKilometers = 5.0

    private const val EarthRadiusKilometers = 6_371.0088

    /** Missions the sending state runs in the host country, most capable kind first. */
    fun missions(
        sendingCountry: String,
        hostCountry: String,
        index: DiplomaticMissionIndex = DiplomaticMissionIndex.bundled,
    ): List<DiplomaticMission> =
        index.missions(sendingCountry, hostCountry).sortedWith(::compareWithoutLocation)

    /**
     * The missions of the given sending countries in the host country, best first, capped at [limit].
     *
     * Ordering, chosen for a traveller who needs help soon:
     * - With a coordinate, career missions (embassy, consulate-general, consulate) are sorted by
     *   distance, because the realistic question is "where can I physically get to". Honorary
     *   consulates always follow the career missions regardless of distance: they are run by a
     *   local volunteer, usually cannot replace a lost passport, and a close-by honorary post must
     *   not hide the embassy that can. Distances within [DistanceBucketKilometers] count as a tie
     *   (coordinates are mostly city centres, so an embassy and a consulate-general in the same
     *   city are equally "near") and the more capable kind wins the tie.
     * - Without a coordinate there is no honest way to rank by distance, so kind decides
     *   (embassy, consulate-general, consulate, other, honorary), then name.
     *
     * [arrangementRank] (lower is better) lets a caller prefer one kind of sending state over another
     * among equally near missions: it is applied after the distance bucket and before the kind. Without
     * a coordinate it applies right after the honorary rule. The default ranks everything equally.
     *
     * Missions listed under several requested countries are returned once.
     */
    fun nearest(
        sendingCountries: List<String>,
        hostCountry: String,
        near: MissionCoordinate?,
        limit: Int,
        index: DiplomaticMissionIndex = DiplomaticMissionIndex.bundled,
        arrangementRank: (DiplomaticMission) -> Int = { 0 },
    ): List<DiplomaticMissionMatch> {
        if (limit <= 0) return emptyList()

        val seenIds = mutableSetOf<String>()
        val candidates = sendingCountries
            .flatMap { index.missions(it, hostCountry) }
            .filter { seenIds.add(it.id) }

        val matches = candidates.map { mission ->
            DiplomaticMissionMatch(
                mission = mission,
                distanceKilometers = near?.let {
                    greatCircleDistanceKilometers(it.latitude, it.longitude, mission.latitude, mission.longitude)
                },
            )
        }

        return matches.sortedWith { lhs, rhs -> compareMatches(lhs, rhs, arrangementRank) }.take(limit)
    }

    /**
     * The states whose missions can help a citizen of [citizenship] when their own state has no
     * mission in the country they are in: the other EU member states for EU citizens, plus the
     * other Nordic states for Nordic citizens (which brings in Norway and Iceland). Everyone else
     * gets an empty list. Sorted, never contains the citizenship itself.
     */
    fun fallbackStates(citizenship: String): List<String> {
        val code = citizenship.normalizedCountryCode()
        val states = mutableSetOf<String>()

        if (code in euMemberStates) states += euMemberStates
        if (code in nordicStates) states += nordicStates

        states -= code
        return states.sorted()
    }

    // region Ordering

    private fun compareWithoutLocation(lhs: DiplomaticMission, rhs: DiplomaticMission): Int {
        if (lhs.kind.rank != rhs.kind.rank) return lhs.kind.rank.compareTo(rhs.kind.rank)
        if (lhs.name != rhs.name) return lhs.name.compareTo(rhs.name)
        return lhs.id.compareTo(rhs.id)
    }

    private fun compareMatches(
        lhs: DiplomaticMissionMatch,
        rhs: DiplomaticMissionMatch,
        arrangementRank: (DiplomaticMission) -> Int,
    ): Int {
        // Honorary consulates always follow the career missions.
        if (lhs.mission.kind.isHonorary != rhs.mission.kind.isHonorary) {
            return if (rhs.mission.kind.isHonorary) -1 else 1
        }

        val lhsArrangement = arrangementRank(lhs.mission)
        val rhsArrangement = arrangementRank(rhs.mission)
        val lhsDistance = lhs.distanceKilometers
        val rhsDistance = rhs.distanceKilometers

        if (lhsDistance == null || rhsDistance == null) {
            if (lhsArrangement != rhsArrangement) return lhsArrangement.compareTo(rhsArrangement)
            return compareWithoutLocation(lhs.mission, rhs.mission)
        }

        val lhsBucket = floor(lhsDistance / DistanceBucketKilometers)
        val rhsBucket = floor(rhsDistance / DistanceBucketKilometers)
        if (lhsBucket != rhsBucket) return lhsBucket.compareTo(rhsBucket)
        if (lhsArrangement != rhsArrangement) return lhsArrangement.compareTo(rhsArrangement)
        if (lhs.mission.kind.rank != rhs.mission.kind.rank) {
            return lhs.mission.kind.rank.compareTo(rhs.mission.kind.rank)
        }
        if (lhsDistance != rhsDistance) return lhsDistance.compareTo(rhsDistance)
        return compareWithoutLocation(lhs.mission, rhs.mission)
    }

    // endregion

    /** Haversine distance on a spherical Earth, accurate to well under 1% which is plenty here. */
    internal fun greatCircleDistanceKilometers(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double,
    ): Double {
        val phi1 = Math.toRadians(fromLatitude)
        val phi2 = Math.toRadians(toLatitude)
        val deltaPhi = Math.toRadians(toLatitude - fromLatitude)
        val deltaLambda = Math.toRadians(toLongitude - fromLongitude)

        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
            cos(phi1) * cos(phi2) * sin(deltaLambda / 2) * sin(deltaLambda / 2)
        return 2 * EarthRadiusKilometers * asin(min(1.0, sqrt(a)))
    }
}
