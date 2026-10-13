package com.iloapps.nomaddashboard.core.data.emergency

import com.iloapps.nomaddashboard.core.model.EmergencyCareFacility
import com.iloapps.nomaddashboard.core.model.HospitalOwnership
import java.text.Normalizer
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Which kind of search produced a candidate; a nearby (point of interest) match beats a text match of equal detail. */
internal enum class HospitalSearchSource { TEXT, NEARBY }

/** A raw search hit before duplicates are merged and distances are measured. */
internal data class HospitalCandidate(
    val placeId: String?,
    val name: String,
    val address: String?,
    val locality: String?,
    val latitude: Double,
    val longitude: Double,
    /** The Places primary type, which doubles as the ownership/relevance hint. */
    val hint: String?,
    val source: HospitalSearchSource,
)

/**
 * Derives public/private from the place name and type hint, as macOS does. Places has no ownership
 * field, so a hospital whose name carries no hint stays [HospitalOwnership.UNKNOWN].
 */
internal fun classifyHospitalOwnership(name: String, ownershipHint: String?): HospitalOwnership {
    val haystack = foldForMatching(listOfNotNull(name, ownershipHint).joinToString(" "))
    return when {
        PublicOwnershipKeywords.any(haystack::contains) -> HospitalOwnership.PUBLIC
        PrivateOwnershipKeywords.any(haystack::contains) -> HospitalOwnership.PRIVATE
        else -> HospitalOwnership.UNKNOWN
    }
}

/**
 * Whether a text-search hit looks like a human emergency facility: it must mention a hospital or
 * emergency keyword and must not be a veterinary or pet facility.
 */
internal fun isRelevantBroaderSearchResult(candidate: HospitalCandidate): Boolean {
    val haystack = foldForMatching(listOfNotNull(candidate.name, candidate.hint).joinToString(" "))
    val tokens = hospitalTokens(haystack)
    if (tokens.any { token -> ExcludedTokenPrefixes.any(token::startsWith) || token in ExcludedTokens }) return false
    return BroaderSearchKeywords.any(haystack::contains)
}

/**
 * Merges duplicates: an exact name+coordinate match, then the same hospital under a shorter alias within
 * 500 m. The more specific entry (longer informative name, address, locality) wins.
 */
internal fun deduplicateHospitalCandidates(candidates: List<HospitalCandidate>): List<HospitalCandidate> {
    val bestByExactKey = LinkedHashMap<String, HospitalCandidate>()
    for (candidate in candidates) {
        val key = exactHospitalKey(candidate.name, candidate.latitude, candidate.longitude)
        val existing = bestByExactKey[key]
        bestByExactKey[key] = if (existing == null) candidate else preferred(existing, candidate)
    }

    val deduplicated = mutableListOf<HospitalCandidate>()
    for (candidate in bestByExactKey.values.sortedWith(PreferenceComparator)) {
        val duplicateIndex = deduplicated.indexOfFirst { areNearbyAliasDuplicates(it, candidate) }
        if (duplicateIndex < 0) {
            deduplicated += candidate
        } else {
            deduplicated[duplicateIndex] = preferred(deduplicated[duplicateIndex], candidate)
        }
    }
    return deduplicated
}

/** Dedupes the candidates, measures each from the origin and applies [selectEmergencyHospitals]. */
internal fun pickEmergencyHospitals(
    candidates: List<HospitalCandidate>,
    originLatitude: Double,
    originLongitude: Double,
    maximumResults: Int,
): List<EmergencyCareFacility> =
    selectEmergencyHospitals(
        facilities = deduplicateHospitalCandidates(candidates).map { candidate ->
            EmergencyCareFacility(
                placeId = candidate.placeId,
                name = candidate.name,
                address = candidate.address,
                distanceKilometers = distanceKilometers(
                    originLatitude, originLongitude, candidate.latitude, candidate.longitude,
                ),
                latitude = candidate.latitude,
                longitude = candidate.longitude,
                primaryType = candidate.hint,
                ownership = classifyHospitalOwnership(name = candidate.name, ownershipHint = candidate.hint),
            )
        },
        maximumResults = maximumResults,
    )

/**
 * Picks the hospitals to show: always the nearest public and the nearest private one (when both exist),
 * filled up with the next nearest, capped at [maximumResults]. The result is always ordered nearest first,
 * so a guaranteed far-away private hospital never appears before a closer one.
 */
internal fun selectEmergencyHospitals(
    facilities: List<EmergencyCareFacility>,
    maximumResults: Int,
    radiusKilometers: Double = Double.POSITIVE_INFINITY,
): List<EmergencyCareFacility> {
    if (maximumResults <= 0) return emptyList()
    val sorted = facilities
        .filter { it.distanceKilometers <= radiusKilometers }
        .distinctBy { exactHospitalKey(it.name, it.latitude, it.longitude) }
        .sortedBy(EmergencyCareFacility::distanceKilometers)

    val nearestPublic = sorted.firstOrNull { it.ownership == HospitalOwnership.PUBLIC }
    val nearestPrivate = sorted.firstOrNull { it.ownership == HospitalOwnership.PRIVATE }
    if (nearestPublic == null || nearestPrivate == null) {
        return sorted.take(maximumResults)
    }

    val selected = LinkedHashSet<EmergencyCareFacility>()
    selected += nearestPublic
    selected += nearestPrivate
    for (facility in sorted) {
        if (selected.size >= maximumResults) break
        selected += facility
    }
    return selected.sortedBy(EmergencyCareFacility::distanceKilometers)
}

internal fun distanceKilometers(
    startLatitude: Double,
    startLongitude: Double,
    endLatitude: Double,
    endLongitude: Double,
): Double {
    val earthRadiusKilometers = 6_371.0
    val deltaLatitude = Math.toRadians(endLatitude - startLatitude)
    val deltaLongitude = Math.toRadians(endLongitude - startLongitude)
    val originLatitudeRadians = Math.toRadians(startLatitude)
    val destinationLatitudeRadians = Math.toRadians(endLatitude)
    val haversine = sin(deltaLatitude / 2).pow(2) +
        cos(originLatitudeRadians) * cos(destinationLatitudeRadians) *
        sin(deltaLongitude / 2).pow(2)
    val arc = 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
    return earthRadiusKilometers * arc
}

private fun exactHospitalKey(name: String, latitude: Double, longitude: Double): String =
    listOf(
        hospitalTokens(name).joinToString(" "),
        String.format(Locale.ROOT, "%.4f", latitude),
        String.format(Locale.ROOT, "%.4f", longitude),
    ).joinToString("|")

private fun informativeTokens(candidate: HospitalCandidate): List<String> =
    hospitalTokens(candidate.name).filter { it !in NoiseTokens }

private fun specificityScore(candidate: HospitalCandidate): Int {
    var score = informativeTokens(candidate).size * 4
    score += hospitalTokens(candidate.name).size
    if (!candidate.address.isNullOrEmpty()) score += 6
    if (!candidate.locality.isNullOrEmpty()) score += 2
    if (!candidate.hint.isNullOrEmpty()) score += 1
    return score
}

/** Most specific first, then nearby over text, then the longer name; ties keep their input order. */
private val PreferenceComparator: Comparator<HospitalCandidate> =
    compareByDescending<HospitalCandidate> { specificityScore(it) }
        .thenByDescending { it.source.ordinal }
        .thenByDescending { it.name.length }

private fun preferred(first: HospitalCandidate, second: HospitalCandidate): HospitalCandidate =
    if (PreferenceComparator.compare(first, second) <= 0) first else second

private fun areNearbyAliasDuplicates(first: HospitalCandidate, second: HospitalCandidate): Boolean {
    val firstFingerprint = aliasFingerprint(first)
    if (firstFingerprint.isEmpty() || firstFingerprint != aliasFingerprint(second)) return false
    return distanceKilometers(first.latitude, first.longitude, second.latitude, second.longitude) * 1_000 <= AliasDuplicateMeters
}

private fun aliasFingerprint(candidate: HospitalCandidate): String {
    val localityTokens = hospitalTokens(candidate.locality.orEmpty()).toSet()
    val informative = informativeTokens(candidate).filter { it !in localityTokens }
    if (informative.isNotEmpty()) return informative.joinToString(" ")
    return hospitalTokens(candidate.name).filter { it !in NoiseTokens }.joinToString(" ")
}

private fun hospitalTokens(value: String): List<String> =
    foldForMatching(value)
        .replace("quironsalud", "quiron")
        .split(NonAlphanumeric)
        .filter(String::isNotEmpty)

private fun foldForMatching(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(CombiningMarks, "")
        .lowercase(Locale.ROOT)

private const val AliasDuplicateMeters = 500.0
private val CombiningMarks = Regex("\\p{M}+")
private val NonAlphanumeric = Regex("[^\\p{L}\\p{N}]+")
private val PublicOwnershipKeywords =
    listOf("public", "publique", "pubblico", "publico", "publica", "public hospital")
private val PrivateOwnershipKeywords =
    listOf("private", "privat", "privee", "privada", "privado", "privato")
private val BroaderSearchKeywords =
    listOf("hospital", "emergency", "emergency room", "urgent care", "urgencias", "emergencias")
private val ExcludedTokenPrefixes = listOf("veterin", "animal")
private val ExcludedTokens = setOf("pet", "pets", "mascota", "mascotas")
private val NoiseTokens = setOf(
    "hospital", "de", "del", "la", "el", "los", "las", "y", "i",
    "centro", "centre", "medical", "medico", "sanitario", "clinic", "clinica",
)
