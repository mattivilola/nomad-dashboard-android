package com.iloapps.nomaddashboard.core.data.localprice

import com.iloapps.nomaddashboard.core.database.dao.LocalPriceCacheDao
import com.iloapps.nomaddashboard.core.database.entity.LocalPriceCacheEntity
import com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorKind
import com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorRow
import com.iloapps.nomaddashboard.core.model.LocalPriceLevelSnapshot
import com.iloapps.nomaddashboard.core.model.LocalPriceLevelStatus
import com.iloapps.nomaddashboard.core.model.LocalPricePrecision
import com.iloapps.nomaddashboard.core.model.LocalPriceSummaryBand
import com.iloapps.nomaddashboard.core.network.api.CensusGeocoderService
import com.iloapps.nomaddashboard.core.network.api.EurostatService
import com.iloapps.nomaddashboard.core.network.api.HudUserFmrService
import com.iloapps.nomaddashboard.core.network.model.CensusCounty
import com.iloapps.nomaddashboard.core.network.model.EurostatDatasetResponse
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody
import retrofit2.HttpException

@Singleton
class DefaultLocalPriceLevelProvider @Inject constructor(
    private val json: Json,
    private val cacheDao: LocalPriceCacheDao,
    private val eurostatService: EurostatService,
    private val censusGeocoderService: CensusGeocoderService,
    private val hudUserFmrService: HudUserFmrService,
) : LocalPriceLevelProvider {
    override suspend fun prices(
        request: LocalPriceLevelRequest,
        hudUserApiToken: String?,
    ): LocalPriceLevelSnapshot {
        val normalizedCountryCode = normalizedCountryCode(request.countryCode)
        if (normalizedCountryCode == null) {
            return LocalPriceLevelSnapshot(
                status = LocalPriceLevelStatus.LOCATION_REQUIRED,
                sources = CapabilitySources,
                detail = "Allow current location or external IP location to estimate the local price level.",
            )
        }

        return when {
            normalizedCountryCode == UnitedStatesCountryCode -> usSnapshot(
                request = request.copy(countryCode = normalizedCountryCode),
                hudUserApiToken = hudUserApiToken,
            )

            EurostatCountryCodes.contains(normalizedCountryCode) -> eurostatSnapshot(
                request = request.copy(countryCode = normalizedCountryCode),
                countryCode = normalizedCountryCode,
            )

            else -> LocalPriceLevelSnapshot(
                status = LocalPriceLevelStatus.UNSUPPORTED,
                countryCode = normalizedCountryCode,
                countryName = request.countryName,
                sources = CapabilitySources,
                fetchedAt = Instant.now(),
                detail = "Local price level is only supported in Europe and the United States right now.",
            )
        }
    }

    override suspend fun clearUsCache() {
        cacheDao.clearUsEntries()
    }

    private suspend fun eurostatSnapshot(
        request: LocalPriceLevelRequest,
        countryCode: String,
    ): LocalPriceLevelSnapshot {
        val cacheKey = countryCode
        cachedSnapshot(cacheKey)?.let { return it }

        val fetchedAt = Instant.now()
        val meal: EurostatObservation?
        val groceries: EurostatObservation?
        val overall: EurostatObservation?
        try {
            meal = eurostatObservation(countryCode = countryCode, category = MealOutCategory)
            groceries = eurostatObservation(countryCode = countryCode, category = GroceriesCategory)
            overall = eurostatObservation(countryCode = countryCode, category = OverallCategory)
        } catch (_: Throwable) {
            val snapshot = LocalPriceLevelSnapshot(
                status = LocalPriceLevelStatus.UNAVAILABLE,
                countryCode = countryCode,
                countryName = request.countryName,
                rows = emptyList(),
                sources = EurostatSources,
                fetchedAt = fetchedAt,
                detail = "Local price level is unavailable right now.",
            )
            cacheSnapshot(cacheKey, snapshot)
            return snapshot
        }

        val rows = buildList {
            meal?.let { add(makeEurostatRow(kind = LocalPriceIndicatorKind.MEAL_OUT, observation = it)) }
            groceries?.let { add(makeEurostatRow(kind = LocalPriceIndicatorKind.GROCERIES, observation = it)) }
            overall?.let { add(makeEurostatRow(kind = LocalPriceIndicatorKind.OVERALL, observation = it)) }
        }.take(MaxDashboardRows)

        val snapshot = if (rows.isEmpty()) {
            LocalPriceLevelSnapshot(
                status = LocalPriceLevelStatus.UNSUPPORTED,
                countryCode = countryCode,
                countryName = request.countryName,
                rows = emptyList(),
                sources = EurostatSources,
                fetchedAt = fetchedAt,
                detail = "Eurostat does not currently publish traveller price levels for this country in the v1 dataset.",
            )
        } else {
            val summaryReference = overall?.value ?: mean(listOf(meal?.value, groceries?.value))
            LocalPriceLevelSnapshot(
                status = if (rows.size == MaxDashboardRows) {
                    LocalPriceLevelStatus.READY
                } else {
                    LocalPriceLevelStatus.PARTIAL
                },
                summaryBand = summaryReference?.let(::summaryBandFor) ?: LocalPriceSummaryBand.LIMITED,
                countryCode = countryCode,
                countryName = request.countryName,
                rows = rows,
                sources = EurostatSources,
                fetchedAt = fetchedAt,
                detail = "Meal out and groceries use country-level Eurostat price indices. 1BR rent is replaced with an overall local cost signal when no official free rent dataset is available.",
            )
        }

        cacheSnapshot(cacheKey, snapshot)
        return snapshot
    }

    private suspend fun usSnapshot(
        request: LocalPriceLevelRequest,
        hudUserApiToken: String?,
    ): LocalPriceLevelSnapshot {
        val normalizedToken = hudUserApiToken?.trim()?.takeIf(String::isNotBlank)
        if (normalizedToken == null) {
            return LocalPriceLevelSnapshot(
                status = LocalPriceLevelStatus.CONFIGURATION_REQUIRED,
                countryCode = UnitedStatesCountryCode,
                countryName = request.countryName ?: "United States",
                sources = HudSources,
                detail = "Add a HUD USER API token in Settings to show the US 1-bedroom rent benchmark.",
            )
        }

        val latitude = request.latitude
        val longitude = request.longitude
        if (latitude == null || longitude == null) {
            return LocalPriceLevelSnapshot(
                status = LocalPriceLevelStatus.LOCATION_REQUIRED,
                countryCode = UnitedStatesCountryCode,
                countryName = request.countryName ?: "United States",
                sources = HudSources,
                detail = "Allow current location to resolve the US county for the HUD 1-bedroom rent benchmark.",
            )
        }

        val cacheKey = buildUsCacheKey(latitude = latitude, longitude = longitude, token = normalizedToken)
        cachedSnapshot(cacheKey)?.let { return it }

        return try {
            val county = censusGeocoderService.countyForCoordinate(
                latitude = latitude,
                longitude = longitude,
            ).result.geographies.counties.firstOrNull()
                ?: error("County GEOID unavailable")
            val rent = hudOneBedroomRent(county = county, token = normalizedToken)
            val value = CurrencyFormatter.format(rent.oneBedroomRent)?.plus("/mo") ?: "n/a"
            val row = LocalPriceIndicatorRow(
                kind = LocalPriceIndicatorKind.RENT_ONE_BEDROOM,
                value = value,
                detail = "${rent.precision.displayName()} · ${rent.areaName} · ${rent.year}",
                precision = rent.precision,
                source = "HUD USER",
            )
            LocalPriceLevelSnapshot(
                status = LocalPriceLevelStatus.PARTIAL,
                summaryBand = LocalPriceSummaryBand.LIMITED,
                countryCode = UnitedStatesCountryCode,
                countryName = request.countryName ?: "United States",
                rows = listOf(row),
                sources = HudSources,
                fetchedAt = Instant.now(),
                detail = "US v1 currently shows the HUD 1-bedroom rent benchmark only.",
                note = county.name,
            ).also { cacheSnapshot(cacheKey, it) }
        } catch (error: HttpException) {
            if (error.code() == 401 || error.code() == 403) {
                LocalPriceLevelSnapshot(
                    status = LocalPriceLevelStatus.CONFIGURATION_REQUIRED,
                    countryCode = UnitedStatesCountryCode,
                    countryName = request.countryName ?: "United States",
                    sources = HudSources,
                    detail = "The HUD USER API token was rejected. Update the token in Settings.",
                )
            } else {
                LocalPriceLevelSnapshot(
                    status = LocalPriceLevelStatus.UNAVAILABLE,
                    countryCode = UnitedStatesCountryCode,
                    countryName = request.countryName ?: "United States",
                    sources = HudSources,
                    fetchedAt = Instant.now(),
                    detail = "Local price level is unavailable right now.",
                ).also { cacheSnapshot(cacheKey, it) }
            }
        } catch (_: Throwable) {
            LocalPriceLevelSnapshot(
                status = LocalPriceLevelStatus.UNAVAILABLE,
                countryCode = UnitedStatesCountryCode,
                countryName = request.countryName ?: "United States",
                sources = HudSources,
                fetchedAt = Instant.now(),
                detail = "Local price level is unavailable right now.",
            ).also { cacheSnapshot(cacheKey, it) }
        }
    }

    private suspend fun eurostatObservation(
        countryCode: String,
        category: String,
    ): EurostatObservation? {
        val dataset = eurostatService.purchasingPowerParity(
            countryCode = countryCode,
            category = category,
        )
        return dataset.latestObservation(category)
    }

    private suspend fun hudOneBedroomRent(
        county: CensusCounty,
        token: String,
    ): HudOneBedroomRent {
        val payload = hudUserFmrService.fairMarketRent(
            countyGEOID = county.geoid,
            authorization = "Bearer $token",
        ).use { body -> parseJsonObject(body) }

        val dataObject = payload["data"]?.jsonObject ?: error("Missing HUD data object")
        val basicData = dataObject["basicdata"] ?: error("Missing HUD basicdata")

        if (basicData is JsonObject) {
            val rent = oneBedroomRentValue(basicData)
            val metroName = stringValue(dataObject["metro_name"])
            val countyName = stringValue(dataObject["county_name"])
            val areaName = stringValue(dataObject["area_name"]) ?: metroName ?: countyName ?: "HUD area"
            val precision = if (metroName == null) {
                LocalPricePrecision.COUNTY_BENCHMARK
            } else {
                LocalPricePrecision.METRO_BENCHMARK
            }
            val year = intValue(basicData["year"])
                ?: intValue(dataObject["year"])
                ?: Instant.now().atZone(java.time.ZoneId.systemDefault()).year
            return HudOneBedroomRent(
                areaName = areaName,
                year = year,
                oneBedroomRent = rent,
                precision = precision,
            )
        }

        if (basicData is JsonArray) {
            val row = basicData.firstOrNull { stringValue(it.jsonObject["zip_code"]) == "MSA level" }
                ?: basicData.firstOrNull()
                ?: error("Missing HUD array row")
            val rent = oneBedroomRentValue(row.jsonObject)
            val areaName = stringValue(dataObject["area_name"])
                ?: stringValue(dataObject["metro_name"])
                ?: "HUD metro area"
            val year = intValue(dataObject["year"])
                ?: Instant.now().atZone(java.time.ZoneId.systemDefault()).year
            return HudOneBedroomRent(
                areaName = areaName,
                year = year,
                oneBedroomRent = rent,
                precision = LocalPricePrecision.METRO_BENCHMARK,
            )
        }

        error("Unsupported HUD payload")
    }

    private suspend fun cachedSnapshot(cacheKey: String): LocalPriceLevelSnapshot? {
        val cached = cacheDao.getByCacheKey(cacheKey) ?: return null
        val fetchedAt = cached.fetchedAtEpochMillis?.let(Instant::ofEpochMilli)
        if (fetchedAt == null || Duration.between(fetchedAt, Instant.now()) > CacheTtl) {
            return null
        }
        return cached.toSnapshot(json)
    }

    private suspend fun cacheSnapshot(
        cacheKey: String,
        snapshot: LocalPriceLevelSnapshot,
    ) {
        cacheDao.upsert(snapshot.toEntity(cacheKey, json))
    }

    private fun EurostatDatasetResponse.latestObservation(category: String): EurostatObservation? {
        if (value.isEmpty()) {
            return null
        }

        val reverseTimeIndex = dimension.time.category.index.entries.associate { (year, index) -> index to year }
        val latestKey = value.keys.mapNotNull(String::toIntOrNull).maxOrNull() ?: return null
        val latestValue = value[latestKey.toString()] ?: return null
        val year = reverseTimeIndex[latestKey]?.toIntOrNull() ?: return null

        return EurostatObservation(category = category, year = year, value = latestValue)
    }

    private fun makeEurostatRow(
        kind: LocalPriceIndicatorKind,
        observation: EurostatObservation,
    ): LocalPriceIndicatorRow =
        LocalPriceIndicatorRow(
            kind = kind,
            value = rowValueDescription(observation.value),
            detail = relativeDifferenceText(observation.value) +
                " · ${LocalPricePrecision.COUNTRY_FALLBACK.displayName()} · ${observation.year}",
            precision = LocalPricePrecision.COUNTRY_FALLBACK,
            source = "Eurostat",
        )

    private fun buildUsCacheKey(
        latitude: Double,
        longitude: Double,
        token: String,
    ): String =
        buildString {
            append(UnitedStatesCountryCode)
            append("|")
            append(String.format(Locale.US, "%.3f", latitude))
            append(",")
            append(String.format(Locale.US, "%.3f", longitude))
            append("|")
            append(token.sha256())
        }

    private fun parseJsonObject(body: ResponseBody): JsonObject =
        json.parseToJsonElement(body.string()).jsonObject

    private fun oneBedroomRentValue(objectValue: JsonObject): Double =
        doubleValue(objectValue["One-Bedroom"]) ?: error("Missing one-bedroom rent")

    private fun summaryBandFor(value: Double): LocalPriceSummaryBand = when {
        value < 95.0 -> LocalPriceSummaryBand.LOW
        value >= 105.0 -> LocalPriceSummaryBand.HIGH
        else -> LocalPriceSummaryBand.MEDIUM
    }

    private fun rowValueDescription(value: Double): String = when {
        value < 95.0 -> "Below Avg"
        value >= 105.0 -> "Above Avg"
        else -> "Moderate"
    }

    private fun relativeDifferenceText(value: Double): String {
        val delta = (value - 100.0).roundToInt()
        return if (delta < 0) {
            "${abs(delta)}% below EU average"
        } else {
            "$delta% above EU average"
        }
    }

    private fun normalizedCountryCode(countryCode: String?): String? =
        countryCode?.trim()?.takeIf(String::isNotBlank)?.uppercase(Locale.US)?.let { normalized ->
            if (normalized == "GR") {
                "EL"
            } else {
                normalized
            }
        }

    private fun stringValue(element: JsonElement?): String? =
        element?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotBlank)

    private fun intValue(element: JsonElement?): Int? =
        when (val primitive = element as? JsonPrimitive) {
            null -> null
            else -> primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull()
        }

    private fun doubleValue(element: JsonElement?): Double? =
        when (val primitive = element as? JsonPrimitive) {
            null -> null
            else -> primitive.doubleOrNull ?: primitive.contentOrNull?.toDoubleOrNull()
        }

    private fun mean(values: List<Double?>): Double? {
        val resolved = values.filterNotNull()
        return if (resolved.isEmpty()) {
            null
        } else {
            resolved.sum() / resolved.size
        }
    }

    private fun LocalPriceLevelSnapshot.toEntity(
        cacheKey: String,
        json: Json,
    ): LocalPriceCacheEntity =
        LocalPriceCacheEntity(
            cacheKey = cacheKey,
            status = status.name,
            summaryBand = summaryBand?.name,
            countryCode = countryCode,
            countryName = countryName,
            rowsJson = json.encodeToString(ListSerializer(LocalPriceIndicatorRow.serializer()), rows),
            sourcesJson = json.encodeToString(ListSerializer(String.serializer()), sources),
            fetchedAtEpochMillis = fetchedAt?.toEpochMilli(),
            detail = detail,
            note = note,
        )

    private fun LocalPriceCacheEntity.toSnapshot(json: Json): LocalPriceLevelSnapshot =
        LocalPriceLevelSnapshot(
            status = enumValueOf<LocalPriceLevelStatus>(status),
            summaryBand = summaryBand?.let { enumValueOf<LocalPriceSummaryBand>(it) },
            countryCode = countryCode,
            countryName = countryName,
            rows = json.decodeFromString(ListSerializer(LocalPriceIndicatorRow.serializer()), rowsJson),
            sources = json.decodeFromString(ListSerializer(String.serializer()), sourcesJson),
            fetchedAt = fetchedAtEpochMillis?.let(Instant::ofEpochMilli),
            detail = detail,
            note = note,
        )

    private fun LocalPricePrecision.displayName(): String = when (this) {
        LocalPricePrecision.COUNTRY_FALLBACK -> "Country fallback"
        LocalPricePrecision.COUNTY_BENCHMARK -> "County benchmark"
        LocalPricePrecision.METRO_BENCHMARK -> "Metro benchmark"
    }

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

    private data class EurostatObservation(
        val category: String,
        val year: Int,
        val value: Double,
    )

    private data class HudOneBedroomRent(
        val areaName: String,
        val year: Int,
        val oneBedroomRent: Double,
        val precision: LocalPricePrecision,
    )

    private companion object {
        val CacheTtl: Duration = Duration.ofHours(6)
        val EurostatCountryCodes: Set<String> = setOf(
            "AT", "BE", "BG", "CY", "CZ", "DE", "DK", "EE", "EL", "ES", "FI", "FR", "HR", "HU",
            "IE", "IT", "LT", "LU", "LV", "MT", "NL", "PL", "PT", "RO", "SE", "SI", "SK",
        )
        val EurostatSources = listOf("Eurostat")
        val HudSources = listOf("HUD USER", "US Census Geocoder")
        val CapabilitySources = listOf("Eurostat", "HUD USER", "US Census Geocoder")
        val CurrencyFormatter = java.text.NumberFormat.getCurrencyInstance(Locale.US).apply {
            maximumFractionDigits = 0
        }
        const val GroceriesCategory = "A0101"
        const val MaxDashboardRows = 3
        const val MealOutCategory = "A0111"
        const val OverallCategory = "A01"
        const val UnitedStatesCountryCode = "US"
    }
}
