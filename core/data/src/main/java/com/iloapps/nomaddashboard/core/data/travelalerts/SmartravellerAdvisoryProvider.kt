package com.iloapps.nomaddashboard.core.data.travelalerts

import android.util.Log
import com.iloapps.nomaddashboard.core.model.TravelAlertKind
import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalSnapshot
import com.iloapps.nomaddashboard.core.network.api.SmartravellerService
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

@Singleton
class SmartravellerAdvisoryProvider @Inject constructor(
    private val service: SmartravellerService,
    private val countryNameResolver: CountryNameResolver,
    private val json: Json,
    private val browserFetcher: SmartravellerBrowserFetcher,
) {
    suspend fun advisory(
        countryCodes: List<String>,
        primaryCountryCode: String,
    ): TravelAlertSignalSnapshot {
        val normalizedCountryCodes = countryCodes.map { it.uppercase() }.uniqued()
        logDebug(
            LOG_TAG,
            "Smartraveller advisory request primary=$primaryCountryCode coverage=${normalizedCountryCodes.joinToString(",")}",
        )
        val destinations = loadDestinations()
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
            worst.severity == TravelAlertSeverity.CLEAR ->
                "No elevated travel advisories across ${matches.size} monitored countries."
            worst.countryCode.equals(primaryCountryCode, ignoreCase = true) ->
                "${worst.countryName}: ${worst.destination.adviceLabel}."
            else ->
                "${worst.countryName} nearby: ${worst.destination.adviceLabel}."
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
            itemCount = matches.size,
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

    internal fun parseDestinations(rawBody: String): List<SmartravellerDestination> {
        val trimmedBody = rawBody.trim()
        if (trimmedBody.isBlank()) {
            throw TravelAlertSourceException(
                diagnosticSummary = "Smartraveller returned an empty destination list.",
                message = "Smartraveller returned an empty response body.",
            )
        }

        return if (trimmedBody.startsWith("{") || trimmedBody.startsWith("[")) {
            parseDestinationsJson(json.parseToJsonElement(trimmedBody))
        } else {
            parseDestinationsHtml(trimmedBody)
        }
    }

    private fun parseDestinationsJson(root: JsonElement): List<SmartravellerDestination> {
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

    private fun parseDestinationsHtml(rawBody: String): List<SmartravellerDestination> {
        val document = Jsoup.parse(rawBody, SOURCE_URL)
        val destinations = document.select("tr").mapNotNull(::parseDestinationRow)
        if (destinations.isEmpty()) {
            throw TravelAlertSourceException(
                diagnosticSummary = "Smartraveller response format changed.",
                message = "Smartraveller destinations page contained no parseable advisory rows.",
            )
        }
        return destinations
    }

    private fun parseDestinationRow(row: Element): SmartravellerDestination? {
        val cells = row.children()
            .filter { child -> child.tagName() == "th" || child.tagName() == "td" }
        if (cells.size < 4) {
            return null
        }

        val name = cells[0].text().trim()
        if (name.isBlank() || name.equals("Destination", ignoreCase = true)) {
            return null
        }

        val level = levelFromAdviceText(cells[2].text()) ?: return null
        return SmartravellerDestination(
            name = name,
            level = level,
            url = cells[0].selectFirst("a[href]")?.absUrl("href")?.takeIf(String::isNotBlank),
            updatedAt = parseDestinationDate(cells[3].text()),
        )
    }

    private fun levelFromAdviceText(value: String): Int? {
        val normalized = value.trim().lowercase(Locale.US)
        return when {
            normalized.contains("do not travel") -> 4
            normalized.contains("reconsider your need to travel") -> 3
            normalized.contains("exercise a high degree of caution") -> 2
            normalized.contains("exercise normal safety precautions") -> 1
            else -> null
        }
    }

    private fun parseDestinationDate(value: String): Instant? =
        runCatching {
            LocalDate.parse(
                value.trim(),
                DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH),
            ).atStartOfDay().toInstant(ZoneOffset.UTC)
        }.getOrNull()

    private suspend fun loadDestinations(): List<SmartravellerDestination> {
        val directResult = runCatching {
            fetchDirect(
                label = "destinations",
                request = { service.destinations() },
            )
        }
        directResult.getOrNull()?.let { return it }
        val directError = directResult.exceptionOrNull()

        logWarn(LOG_TAG, "Smartraveller direct destinations request failed, trying legacy export", directError)

        val exportResult = runCatching {
            fetchDirect(
                label = "destinations-export",
                request = { service.destinationsExport() },
            )
        }
        exportResult.getOrNull()?.let { return it }

        val exportError = exportResult.exceptionOrNull()
        logWarn(LOG_TAG, "Smartraveller legacy export failed, trying WebView fallback", exportError)

        return runCatching {
            val browserHtml = browserFetcher.destinationsHtml()
            logDebug(
                LOG_TAG,
                "Smartraveller WebView fallback returned bytes=${browserHtml.length} snippet=${browserHtml.trim().take(120).replace('\n', ' ')}",
            )
            parseDestinations(browserHtml)
        }.getOrElse { browserError ->
            logWarn(LOG_TAG, "Smartraveller WebView fallback failed", browserError)
            throw TravelAlertSourceException(
                diagnosticSummary = "Smartraveller could not be reached.",
                message = buildString {
                    append("Smartraveller direct fetch failed")
                    directError?.message?.let { append(": $it") }
                    exportError?.message?.let { append(" | legacy export failed: $it") }
                    browserError.message?.let { append(" | WebView fallback failed: $it") }
                },
                cause = browserError,
            )
        }
    }

    private suspend fun fetchDirect(
        label: String,
        request: suspend () -> retrofit2.Response<okhttp3.ResponseBody>,
    ): List<SmartravellerDestination> {
        val response = try {
            request()
        } catch (error: IOException) {
            logWarn(LOG_TAG, "Smartraveller $label request failed before response", error)
            throw TravelAlertSourceException(
                diagnosticSummary = "Smartraveller could not be reached.",
                message = "Smartraveller $label request failed before a response was received.",
                cause = error,
            )
        }
        val contentType = response.headers()["Content-Type"]
        val bodyText = response.body()?.string().orEmpty()
        if (response.isSuccessful.not()) {
            val snippet = response.errorBody()?.string().orEmpty().ifBlank { bodyText }.trim().take(240)
            logWarn(
                LOG_TAG,
                "Smartraveller $label returned HTTP ${response.code()} contentType=$contentType snippet=$snippet",
            )
            throw TravelAlertSourceException(
                diagnosticSummary = "Smartraveller returned HTTP ${response.code()}.",
                message = buildString {
                    append("Smartraveller $label returned HTTP ${response.code()}.")
                    if (contentType.isNullOrBlank().not()) {
                        append(" Content-Type: $contentType.")
                    }
                    if (snippet.isNotBlank()) {
                        append(" Body snippet: $snippet")
                    }
                },
            )
        }

        val trimmedSnippet = bodyText.trim().take(120).replace('\n', ' ')
        logDebug(
            LOG_TAG,
            "Smartraveller $label response HTTP ${response.code()} contentType=$contentType bytes=${bodyText.length} snippet=$trimmedSnippet",
        )
        return parseDestinations(bodyText)
    }

    private companion object {
        const val SOURCE_NAME = "Smartraveller"
        const val SOURCE_URL = "https://www.smartraveller.gov.au"
        const val LOG_TAG = "NomadTravelAlerts"
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

    val adviceLabel: String
        get() = when (level) {
            1 -> "exercise normal safety precautions"
            2 -> "exercise a high degree of caution"
            3 -> "reconsider your need to travel"
            4 -> "do not travel"
            else -> "review local travel advice"
        }
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

private fun logDebug(tag: String, message: String) {
    runCatching { Log.d(tag, message) }
}

private fun logWarn(tag: String, message: String, error: Throwable? = null) {
    runCatching {
        if (error != null) {
            Log.w(tag, message, error)
        } else {
            Log.w(tag, message)
        }
    }
}
