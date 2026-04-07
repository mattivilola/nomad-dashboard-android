package com.iloapps.nomaddashboard.core.data.travelalerts

import com.iloapps.nomaddashboard.core.model.TravelAlertKind
import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalSnapshot
import com.iloapps.nomaddashboard.core.network.api.SmartravellerService
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Singleton
class SmartravellerAdvisoryProvider @Inject constructor(
    private val service: SmartravellerService,
    private val countryNameResolver: CountryNameResolver,
) {
    suspend fun advisory(
        countryCodes: List<String>,
        primaryCountryCode: String,
    ): TravelAlertSignalSnapshot {
        val normalizedCountryCodes = countryCodes.map { it.uppercase() }.uniqued()
        val destinations = parseDestinations(service.destinations())
        val matches = normalizedCountryCodes.mapNotNull { countryCode ->
            val destination = bestDestinationMatch(countryCode, destinations) ?: return@mapNotNull null
            AdvisoryMatch(
                countryCode = countryCode,
                countryName = countryNameResolver.primaryName(countryCode) ?: countryCode,
                destination = destination,
            )
        }

        val worst = matches.maxWithOrNull(
            compareBy<AdvisoryMatch> { it.severity.rank }
                .thenBy { if (it.countryCode == primaryCountryCode.uppercase()) 1 else 0 },
        ) ?: throw TravelAlertSourceException(
            diagnosticSummary = "Smartraveller advisory unavailable.",
            message = "Smartraveller returned no destinations matching the monitored countries.",
        )

        val summary = when {
            worst.severity == TravelAlertSeverity.CLEAR -> "No elevated travel advisories across your nearby countries."
            worst.countryCode.equals(primaryCountryCode, ignoreCase = true) -> "${worst.countryName} is at ${worst.destination.levelLabel}."
            else -> "${worst.countryName} is at ${worst.destination.levelLabel} nearby."
        }

        return TravelAlertSignalSnapshot(
            kind = TravelAlertKind.ADVISORY,
            severity = worst.severity,
            title = "Travel advisory",
            summary = summary,
            sourceName = SOURCE_NAME,
            sourceUrl = worst.destination.url ?: SOURCE_URL,
            updatedAt = worst.destination.updatedAt ?: Instant.now(),
            affectedCountryCodes = matches.filter { it.severity.rank > TravelAlertSeverity.CLEAR.rank }.map { it.countryCode }.uniqued(),
        )
    }

    internal fun bestDestinationMatch(
        countryCode: String,
        destinations: List<SmartravellerDestination>,
    ): SmartravellerDestination? {
        val normalizedCandidates = countryNameResolver.candidateNames(countryCode)
            .map(countryNameResolver::normalized)
            .toSet()

        destinations.firstOrNull { destination ->
            normalizedCandidates.contains(countryNameResolver.normalized(destination.name))
        }?.let { return it }

        return destinations.firstOrNull { destination ->
            val normalizedName = countryNameResolver.normalized(destination.name)
            normalizedCandidates.any { candidate ->
                normalizedName.contains(candidate) || candidate.contains(normalizedName)
            }
        }
    }

    internal fun parseDestinations(root: JsonElement): List<SmartravellerDestination> {
        val items = when (root) {
            is JsonArray -> root
            is JsonObject -> root["data"] as? JsonArray ?: root["destinations"] as? JsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }

        val destinations = items.mapNotNull { item ->
            val objectValue = item as? JsonObject ?: return@mapNotNull null
            val name = objectValue.firstString("title", "name", "country")?.trim().orEmpty()
            if (name.isBlank()) {
                return@mapNotNull null
            }

            SmartravellerDestination(
                name = name,
                level = objectValue.firstInt("advice_level", "adviceLevel", "level") ?: 1,
                url = objectValue.firstString("url", "link", "canonical_url", "destination_url"),
                updatedAt = objectValue.firstInstant("updated_at", "updatedAt", "last_updated", "lastUpdated"),
            )
        }

        if (destinations.isEmpty()) {
            throw TravelAlertSourceException(
                diagnosticSummary = "Smartraveller response format changed.",
                message = "Smartraveller returned no parseable destinations.",
            )
        }

        return destinations
    }

    private companion object {
        const val SOURCE_NAME = "Smartraveller"
        const val SOURCE_URL = "https://www.smartraveller.gov.au"
    }
}

internal data class SmartravellerDestination(
    val name: String,
    val level: Int,
    val url: String?,
    val updatedAt: Instant?,
) {
    val severity: TravelAlertSeverity
        get() = when (level) {
            1 -> TravelAlertSeverity.CLEAR
            2 -> TravelAlertSeverity.CAUTION
            3 -> TravelAlertSeverity.WARNING
            4 -> TravelAlertSeverity.CRITICAL
            else -> TravelAlertSeverity.INFO
        }

    val levelLabel: String
        get() = "Level $level"
}

internal data class AdvisoryMatch(
    val countryCode: String,
    val countryName: String,
    val destination: SmartravellerDestination,
) {
    val severity: TravelAlertSeverity
        get() = destination.severity
}

private fun JsonObject.firstString(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.content?.takeIf { value -> value.isNotBlank() }
    }

private fun JsonObject.firstInt(vararg keys: String): Int? =
    keys.firstNotNullOfOrNull { key ->
        val primitive = this[key] as? JsonPrimitive ?: return@firstNotNullOfOrNull null
        primitive.content.toIntOrNull()
    }

private fun JsonObject.firstInstant(vararg keys: String): Instant? =
    keys.firstNotNullOfOrNull { key ->
        val value = (this[key] as? JsonPrimitive)?.content ?: return@firstNotNullOfOrNull null
        parseTravelAlertInstant(value)
    }
