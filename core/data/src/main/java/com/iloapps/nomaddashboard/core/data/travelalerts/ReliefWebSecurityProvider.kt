package com.iloapps.nomaddashboard.core.data.travelalerts

import com.iloapps.nomaddashboard.core.model.TravelAlertKind
import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalSnapshot
import com.iloapps.nomaddashboard.core.network.api.ReliefWebReportsService
import com.iloapps.nomaddashboard.core.network.model.ReliefWebCondition
import com.iloapps.nomaddashboard.core.network.model.ReliefWebFilter
import com.iloapps.nomaddashboard.core.network.model.ReliefWebReportsRequest
import java.io.IOException
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Singleton
class ReliefWebSecurityProvider @Inject constructor(
    private val service: ReliefWebReportsService,
    private val appConfig: TravelAlertProviderAppConfig,
    private val countryNameResolver: CountryNameResolver,
    private val json: Json,
) {
    suspend fun security(
        countryCodes: List<String>,
        primaryCountryCode: String,
    ): TravelAlertSignalSnapshot {
        val appName = appConfig.reliefWebAppName.trim()
        if (appName.isBlank()) {
            throw ReliefWebProviderError.AppNameMissing(
                "Set NOMAD_RELIEFWEB_APP_NAME in local AppConfig.env.",
            )
        }

        val normalizedCountryCodes = countryCodes.map { it.uppercase() }.uniqued()
        val countryNames = normalizedCountryCodes.mapNotNull(countryNameResolver::primaryName)
        val primaryCountryName = countryNameResolver.primaryName(primaryCountryCode) ?: primaryCountryCode
        val request = ReliefWebReportsRequest(
            filter = ReliefWebFilter(
                conditions = listOf(
                    ReliefWebCondition(
                        field = "country.name",
                        value = countryNames,
                    ),
                ),
            ),
        )

        val response = try {
            service.reports(appName = appName, request = request)
        } catch (error: IOException) {
            throw ReliefWebProviderError.RequestFailed(error)
        }

        val bodyText = response.errorBody()?.string() ?: response.body()?.string().orEmpty()
        if (response.isSuccessful.not()) {
            val errorMessage = parseErrorMessage(bodyText)
            val normalized = errorMessage?.lowercase().orEmpty()
            when {
                response.code() == 403 && normalized.contains("approved appname") -> {
                    throw ReliefWebProviderError.AppNameApprovalRequired(errorMessage)
                }
                normalized.contains("missing appname parameter") -> {
                    throw ReliefWebProviderError.AppNameMissing(errorMessage)
                }
                else -> throw ReliefWebProviderError.UnexpectedStatus(
                    statusCode = response.code(),
                    bodySnippet = bodyText.trim().take(160).ifBlank { null },
                )
            }
        }

        val reports = parseReports(bodyText)
        val recentReports = reports.filter { Duration.between(it.date, Instant.now()) <= RECENCY_WINDOW }
        return signal(
            reports = recentReports,
            primaryCountryName = primaryCountryName,
            matchedCountryNames = countryNames,
            now = Instant.now(),
        )
    }

    internal fun signal(
        reports: List<SecurityReportPayload>,
        primaryCountryName: String,
        matchedCountryNames: List<String>,
        now: Instant,
    ): TravelAlertSignalSnapshot {
        val primaryNormalizer = countryNameResolver.normalized(primaryCountryName)
        val currentCountryReports = reports.filter {
            countryNameResolver.normalized(it.primaryCountryName) == primaryNormalizer
        }
        val nearbyReports = reports.filter {
            countryNameResolver.normalized(it.primaryCountryName) != primaryNormalizer
        }
        val currentCountryRecentReports = currentCountryReports.filter {
            Duration.between(it.date, now) <= PRIMARY_COUNTRY_WARNING_WINDOW
        }

        val severity = when {
            currentCountryRecentReports.isNotEmpty() -> TravelAlertSeverity.WARNING
            currentCountryReports.isNotEmpty() || nearbyReports.size >= 2 -> TravelAlertSeverity.CAUTION
            nearbyReports.isNotEmpty() -> TravelAlertSeverity.INFO
            else -> TravelAlertSeverity.CLEAR
        }

        val latestReport = reports.maxByOrNull { it.date }
        val summary = when (severity) {
            TravelAlertSeverity.WARNING ->
                "${currentCountryRecentReports.size} recent security bulletin(s) mention $primaryCountryName."
            TravelAlertSeverity.CAUTION ->
                if (currentCountryReports.isNotEmpty()) {
                    "Security reporting mentions $primaryCountryName within the last 72 hours."
                } else {
                    "${nearbyReports.size} nearby security bulletins were published recently."
                }
            TravelAlertSeverity.INFO -> "A nearby security bulletin was published within the last 72 hours."
            TravelAlertSeverity.CLEAR -> "No recent security bulletins across ${matchedCountryNames.size} monitored countries."
            TravelAlertSeverity.CRITICAL -> "Regional security conditions require immediate review."
        }

        return TravelAlertSignalSnapshot(
            kind = TravelAlertKind.SECURITY,
            severity = severity,
            title = "Regional security",
            summary = summary,
            sourceName = latestReport?.sourceName ?: SOURCE_NAME,
            sourceUrl = latestReport?.resolvedUrl ?: SOURCE_URL,
            updatedAt = latestReport?.date ?: now,
            itemCount = reports.size,
        )
    }

    internal fun parseReports(rawBody: String): List<SecurityReportPayload> {
        val rootObject = try {
            json.parseToJsonElement(rawBody) as? JsonObject
        } catch (error: Exception) {
            throw ReliefWebProviderError.InvalidPayload("Response body was not valid JSON.")
        } ?: throw ReliefWebProviderError.InvalidPayload("Top-level response was not a JSON object.")

        val items = rootObject["data"] as? JsonArray
            ?: throw ReliefWebProviderError.InvalidPayload("Missing top-level data array.")

        return items.mapNotNull { item ->
            val fields = (item as? JsonObject)?.get("fields") as? JsonObject ?: return@mapNotNull null
            val title = fields.primitive("title") ?: "Security update"
            val dateValue = ((fields["date"] as? JsonObject)?.primitive("created")) ?: return@mapNotNull null
            val date = parseTravelAlertInstant(dateValue) ?: return@mapNotNull null
            val primaryCountry = (fields["primary_country"] as? JsonObject)?.primitive("shortname")
                ?: (fields["primary_country"] as? JsonObject)?.primitive("name")
                ?: "Unknown"
            val sourceName = (fields["source"] as? JsonArray)
                ?.firstNotNullOfOrNull { source ->
                    (source as? JsonObject)?.primitive("shortname")
                        ?: (source as? JsonObject)?.primitive("name")
                } ?: SOURCE_NAME
            SecurityReportPayload(
                title = title,
                date = date,
                primaryCountryName = primaryCountry,
                sourceName = sourceName,
                urlAlias = fields.primitive("url_alias"),
            )
        }
    }

    internal fun parseErrorMessage(rawBody: String): String? {
        val rootObject = runCatching { json.parseToJsonElement(rawBody) as? JsonObject }.getOrNull() ?: return null
        val message = ((rootObject["error"] as? JsonObject)?.primitive("message")).orEmpty().trim()
        return message.ifBlank { null }
    }

    private companion object {
        val PRIMARY_COUNTRY_WARNING_WINDOW: Duration = Duration.ofHours(24)
        val RECENCY_WINDOW: Duration = Duration.ofHours(72)
        const val SOURCE_NAME = "ReliefWeb"
        const val SOURCE_URL = "https://reliefweb.int"
    }
}

internal data class SecurityReportPayload(
    val title: String,
    val date: Instant,
    val primaryCountryName: String,
    val sourceName: String,
    val urlAlias: String?,
) {
    val resolvedUrl: String?
        get() = when {
            urlAlias.isNullOrBlank() -> null
            urlAlias.startsWith("http") -> urlAlias
            else -> "https://reliefweb.int$urlAlias"
        }
}

private fun JsonObject.primitive(key: String): String? =
    (this[key] as? JsonPrimitive)?.content?.takeIf { value -> value.isNotBlank() }

internal fun parseTravelAlertInstant(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull()
