package com.iloapps.nomaddashboard.core.data.fuel

import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.model.FuelStationPrice
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.network.api.FranceFuelPriceService
import com.iloapps.nomaddashboard.core.network.api.ItalyFuelPriceService
import com.iloapps.nomaddashboard.core.network.api.SpainFuelPriceService
import com.iloapps.nomaddashboard.core.network.api.TankerkoenigService
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Singleton
class DefaultFuelPriceProvider @Inject constructor(
    private val json: Json,
    private val spainFuelPriceService: SpainFuelPriceService,
    private val franceFuelPriceService: FranceFuelPriceService,
    private val italyFuelPriceService: ItalyFuelPriceService,
    private val tankerkoenigService: TankerkoenigService,
    private val localConfig: FuelProviderLocalConfig,
) : FuelPriceProvider {
    override suspend fun prices(request: FuelSearchRequest): FuelPriceSnapshot {
        val countryCode = request.countryCode.uppercase(Locale.US)
        return when (countryCode) {
            "ES" -> runSource(request, sourceName = "Spanish Ministry Fuel Prices") {
                spainSnapshot(request)
            }
            "FR" -> runSource(request, sourceName = "French Government Fuel Prices") {
                franceSnapshot(request)
            }
            "IT" -> runSource(
                request,
                sourceName = "MIMIT Fuel Prices",
                note = "Italian prices come from the daily 8:00 update.",
            ) {
                italySnapshot(request)
            }
            "DE" -> germanySnapshot(request)
            else -> unsupportedSnapshot(request)
        }
    }

    private suspend fun spainSnapshot(request: FuelSearchRequest): FuelPriceSnapshot {
        val response = spainFuelPriceService.stations()
        val fetchedAt = parseDateTime(response.fetchedAt)
        val candidates = response.stations.mapNotNull { station ->
            val latitude = station.latitude.toFlexibleCoordinate() ?: return@mapNotNull null
            val longitude = station.longitude.toFlexibleCoordinate() ?: return@mapNotNull null

            val prices = buildMap {
                firstAvailablePrice(
                    station.dieselPrice.toFlexibleDouble(),
                    station.premiumDieselPrice.toFlexibleDouble(),
                )?.let { put(FuelType.DIESEL, it) }
                firstAvailablePrice(
                    station.gasoline95E5Price.toFlexibleDouble(),
                    station.gasoline95E10Price.toFlexibleDouble(),
                    station.gasoline98E5Price.toFlexibleDouble(),
                    station.gasoline98E10Price.toFlexibleDouble(),
                )?.let { put(FuelType.GASOLINE, it) }
            }

            if (prices.isEmpty()) {
                return@mapNotNull null
            }

            FuelStationCandidate(
                identifier = station.identifier.normalizedText() ?: "${latitude},${longitude}",
                stationName = station.stationName.normalizedText()
                    ?: station.address.normalizedText()
                    ?: "Station",
                address = station.address.normalizedText(),
                locality = station.municipality.normalizedText() ?: station.locality.normalizedText(),
                latitude = latitude,
                longitude = longitude,
                updatedAt = fetchedAt,
                isSelfService = null,
                prices = prices,
            )
        }

        return bestSnapshot(
            request = request,
            sourceName = "Spanish Ministry Fuel Prices",
            candidates = candidates,
            fetchedAt = fetchedAt ?: Instant.now(),
        )
    }

    private suspend fun franceSnapshot(request: FuelSearchRequest): FuelPriceSnapshot {
        val candidates = mutableListOf<FuelStationCandidate>()
        var offset = 0
        val limit = 100
        val where = "within_distance(geom, geom'POINT(${request.longitude} ${request.latitude})', ${request.searchRadiusKilometers.toInt()}km)"

        while (true) {
            val response = franceFuelPriceService.records(
                where = where,
                limit = limit,
                offset = offset,
            )

            response.results.forEach { record ->
                val geometry = record.geometry ?: return@forEach
                val latitude = geometry.latitude ?: return@forEach
                val longitude = geometry.longitude ?: return@forEach
                val fuelEntries = parseFrancePrices(record.pricesJson)
                val prices = buildMap {
                    fuelEntries.firstOrNull { it.name.equals("Gazole", ignoreCase = true) }?.pricePerLiter
                        ?.let { put(FuelType.DIESEL, it) }
                    firstAvailablePrice(
                        fuelEntries.firstOrNull { it.name.equals("E10", ignoreCase = true) }?.pricePerLiter,
                        fuelEntries.firstOrNull { it.name.equals("SP95", ignoreCase = true) }?.pricePerLiter,
                        fuelEntries.firstOrNull { it.name.equals("SP98", ignoreCase = true) }?.pricePerLiter,
                    )?.let { put(FuelType.GASOLINE, it) }
                }

                if (prices.isEmpty()) {
                    return@forEach
                }

                candidates += FuelStationCandidate(
                    identifier = record.id?.toString() ?: "${latitude},${longitude}",
                    stationName = record.address.normalizedText() ?: "Station",
                    address = record.address.normalizedText(),
                    locality = record.locality.normalizedText(),
                    latitude = latitude,
                    longitude = longitude,
                    updatedAt = fuelEntries.mapNotNull(FranceFuelEntry::updatedAt).maxOrNull(),
                    isSelfService = null,
                    prices = prices,
                )
            }

            if (response.results.size < limit || offset >= 900) {
                break
            }
            offset += limit
        }

        return bestSnapshot(
            request = request,
            sourceName = "French Government Fuel Prices",
            candidates = candidates,
            fetchedAt = Instant.now(),
        )
    }

    private suspend fun italySnapshot(request: FuelSearchRequest): FuelPriceSnapshot {
        val stationsText = italyFuelPriceService.stationCatalog().use { it.string() }
        val pricesText = italyFuelPriceService.dailyPrices().use { it.string() }

        val stationRows = CsvTable.parse(stationsText)
        val priceRows = CsvTable.parse(pricesText)

        val stations = stationRows.mapNotNull { row ->
            val identifier = row.string("idimpianto") ?: return@mapNotNull null
            val latitude = row.coordinate("latitudine") ?: return@mapNotNull null
            val longitude = row.coordinate("longitudine") ?: return@mapNotNull null

            identifier to FuelStationCandidate(
                identifier = identifier,
                stationName = row.string("bandiera", "nomeimpianto") ?: "Station",
                address = row.string("indirizzo"),
                locality = row.string("comune"),
                latitude = latitude,
                longitude = longitude,
                updatedAt = null,
                isSelfService = null,
                prices = emptyMap(),
            )
        }.toMap().toMutableMap()

        priceRows.forEach { row ->
            val identifier = row.string("idimpianto") ?: return@forEach
            val existing = stations[identifier] ?: return@forEach
            val fuelType = fuelTypeForItaly(row.string("desccarburante") ?: return@forEach) ?: return@forEach
            val price = row.double("prezzo") ?: return@forEach
            val updatedAt = parseDateTime(row.string("dtcomu"))
            val isSelfService = row.bool("isself")

            val mergedPrices = existing.prices.toMutableMap()
            mergedPrices[fuelType] = min(mergedPrices[fuelType] ?: price, price)

            stations[identifier] = existing.copy(
                updatedAt = latestInstant(existing.updatedAt, updatedAt),
                isSelfService = isSelfService ?: existing.isSelfService,
                prices = mergedPrices,
            )
        }

        return bestSnapshot(
            request = request,
            sourceName = "MIMIT Fuel Prices",
            candidates = stations.values.filter { it.prices.isNotEmpty() },
            fetchedAt = Instant.now(),
            note = "Italian prices come from the daily 8:00 update.",
        )
    }

    private suspend fun germanySnapshot(request: FuelSearchRequest): FuelPriceSnapshot {
        val apiKey = localConfig.tankerkoenigApiKey.normalizedText()
        if (apiKey.isNullOrEmpty()) {
            return FuelPriceSnapshot(
                status = FuelPriceStatus.CONFIGURATION_REQUIRED,
                sourceName = "Tankerkönig",
                countryCode = request.countryCode,
                countryName = request.countryName,
                searchRadiusKilometers = request.searchRadiusKilometers,
                fetchedAt = Instant.now(),
                detail = "Germany needs NOMAD_TANKERKOENIG_API_KEY in local AppConfig.env.",
                note = "Germany uses the free Tankerkönig API.",
            )
        }

        return runSource(
            request = request,
            sourceName = "Tankerkönig",
            note = "Germany uses the free Tankerkönig API.",
        ) {
            val stations = mutableMapOf<String, FuelStationCandidate>()

            tileCenters(request.latitude, request.longitude).forEach { (latitude, longitude) ->
                val response = tankerkoenigService.stations(
                    latitude = latitude,
                    longitude = longitude,
                    apiKey = apiKey,
                )
                if (response.ok.not()) {
                    error(response.message ?: "Tankerkonig request failed")
                }

                response.stations.forEach { station ->
                    val identifier = station.identifier.normalizedText() ?: return@forEach
                    val stationLatitude = station.latitude ?: return@forEach
                    val stationLongitude = station.longitude ?: return@forEach
                    val prices = buildMap {
                        station.dieselPrice?.takeIf { it > 0 }?.let { put(FuelType.DIESEL, it) }
                        firstAvailablePrice(
                            station.e10Price?.takeIf { it > 0 },
                            station.e5Price?.takeIf { it > 0 },
                        )?.let { put(FuelType.GASOLINE, it) }
                    }
                    if (prices.isEmpty()) {
                        return@forEach
                    }

                    val mergedPrices = stations[identifier]?.prices.orEmpty().toMutableMap().apply {
                        prices.forEach { (fuelType, price) ->
                            this[fuelType] = min(this[fuelType] ?: price, price)
                        }
                    }

                    stations[identifier] = FuelStationCandidate(
                        identifier = identifier,
                        stationName = station.brand.normalizedText()
                            ?: station.name.normalizedText()
                            ?: "Station",
                        address = listOf(
                            station.street.normalizedText(),
                            station.houseNumber.normalizedText(),
                        ).filterNotNull().joinToString(" ").normalizedText(),
                        locality = station.locality.normalizedText(),
                        latitude = stationLatitude,
                        longitude = stationLongitude,
                        updatedAt = null,
                        isSelfService = null,
                        prices = mergedPrices,
                    )
                }
            }

            bestSnapshot(
                request = request,
                sourceName = "Tankerkönig",
                candidates = stations.values,
                fetchedAt = Instant.now(),
                note = "Germany uses the free Tankerkönig API.",
            )
        }
    }

    private suspend fun runSource(
        request: FuelSearchRequest,
        sourceName: String,
        note: String? = null,
        block: suspend () -> FuelPriceSnapshot,
    ): FuelPriceSnapshot =
        runCatching { block() }.getOrElse {
            FuelPriceSnapshot(
                status = FuelPriceStatus.UNAVAILABLE,
                sourceName = sourceName,
                countryCode = request.countryCode,
                countryName = request.countryName,
                searchRadiusKilometers = request.searchRadiusKilometers,
                fetchedAt = Instant.now(),
                detail = "Nearby fuel prices are unavailable right now.",
                note = note,
            )
        }

    private fun unsupportedSnapshot(request: FuelSearchRequest): FuelPriceSnapshot =
        FuelPriceSnapshot(
            status = FuelPriceStatus.UNSUPPORTED,
            sourceName = "Nomad Fuel Prices",
            countryCode = request.countryCode,
            countryName = request.countryName,
            searchRadiusKilometers = request.searchRadiusKilometers,
            fetchedAt = Instant.now(),
            detail = "Fuel prices are not supported in ${request.countryName ?: request.countryCode} yet.",
        )

    private fun parseFrancePrices(pricesJson: String?): List<FranceFuelEntry> {
        val payload = pricesJson.normalizedText() ?: return emptyList()
        return runCatching {
            json.parseToJsonElement(payload).jsonArray
        }.getOrDefault(emptyList()).mapNotNull { entry ->
            val objectValue = runCatching { entry.jsonObject }.getOrNull() ?: return@mapNotNull null
            val name = objectValue["@nom"]?.jsonPrimitive?.contentOrNull.normalizedText() ?: return@mapNotNull null
            val price = objectValue["@valeur"]?.jsonPrimitive?.contentOrNull.toFlexibleDouble() ?: return@mapNotNull null
            FranceFuelEntry(
                name = name,
                pricePerLiter = price,
                updatedAt = parseDateTime(objectValue["@maj"]?.jsonPrimitive?.contentOrNull),
            )
        }
    }
}

private data class FuelStationCandidate(
    val identifier: String,
    val stationName: String,
    val address: String?,
    val locality: String?,
    val latitude: Double,
    val longitude: Double,
    val updatedAt: Instant?,
    val isSelfService: Boolean?,
    val prices: Map<FuelType, Double>,
)

private data class ScoredFuelStationCandidate(
    val candidate: FuelStationCandidate,
    val distanceKilometers: Double,
)

private data class FranceFuelEntry(
    val name: String,
    val pricePerLiter: Double,
    val updatedAt: Instant?,
)

private fun bestSnapshot(
    request: FuelSearchRequest,
    sourceName: String,
    candidates: Collection<FuelStationCandidate>,
    fetchedAt: Instant,
    note: String? = null,
): FuelPriceSnapshot {
    val inRange = candidates.mapNotNull { candidate ->
        val distanceKilometers = haversineKilometers(
            latitude1 = request.latitude,
            longitude1 = request.longitude,
            latitude2 = candidate.latitude,
            longitude2 = candidate.longitude,
        )
        if (distanceKilometers > request.searchRadiusKilometers) {
            null
        } else {
            ScoredFuelStationCandidate(candidate = candidate, distanceKilometers = distanceKilometers)
        }
    }

    val diesel = bestStation(FuelType.DIESEL, inRange)
    val gasoline = bestStation(FuelType.GASOLINE, inRange)
    val status = if (diesel != null || gasoline != null) {
        FuelPriceStatus.READY
    } else {
        FuelPriceStatus.NO_STATIONS_FOUND
    }

    return FuelPriceSnapshot(
        status = status,
        sourceName = sourceName,
        countryCode = request.countryCode,
        countryName = request.countryName,
        searchRadiusKilometers = request.searchRadiusKilometers,
        diesel = diesel,
        gasoline = gasoline,
        fetchedAt = fetchedAt,
        detail = if (status == FuelPriceStatus.READY) {
            "Cheapest prices within ${request.searchRadiusKilometers.toInt()} km."
        } else {
            "No priced stations found within ${request.searchRadiusKilometers.toInt()} km."
        },
        note = note,
    )
}

private fun bestStation(
    fuelType: FuelType,
    candidates: List<ScoredFuelStationCandidate>,
): FuelStationPrice? {
    val best = candidates
        .mapNotNull { candidate ->
            val price = candidate.candidate.prices[fuelType] ?: return@mapNotNull null
            candidate to price
        }
        .minWithOrNull(
            compareBy<Pair<ScoredFuelStationCandidate, Double>> { it.second }
                .thenBy { it.first.distanceKilometers }
                .thenBy { it.first.candidate.stationName.lowercase(Locale.US) },
        )
        ?: return null

    return FuelStationPrice(
        fuelType = fuelType,
        stationName = best.first.candidate.stationName,
        address = best.first.candidate.address,
        locality = best.first.candidate.locality,
        pricePerLiter = best.second,
        distanceKilometers = best.first.distanceKilometers,
        latitude = best.first.candidate.latitude,
        longitude = best.first.candidate.longitude,
        updatedAt = best.first.candidate.updatedAt,
        isSelfService = best.first.candidate.isSelfService,
    )
}

private fun tileCenters(latitude: Double, longitude: Double): List<Pair<Double, Double>> {
    val latitudeOffsets = listOf(-0.225, 0.0, 0.225)
    val longitudeScale = max(cos(latitude * PI / 180.0), 0.35)
    val longitudeOffset = 0.225 / longitudeScale
    val longitudeOffsets = listOf(-longitudeOffset, 0.0, longitudeOffset)

    return latitudeOffsets.flatMap { latitudeDelta ->
        longitudeOffsets.map { longitudeDelta ->
            latitude + latitudeDelta to longitude + longitudeDelta
        }
    }
}

private fun haversineKilometers(
    latitude1: Double,
    longitude1: Double,
    latitude2: Double,
    longitude2: Double,
): Double {
    val latitudeDelta = Math.toRadians(latitude2 - latitude1)
    val longitudeDelta = Math.toRadians(longitude2 - longitude1)
    val a = sin(latitudeDelta / 2).pow(2) +
        cos(Math.toRadians(latitude1)) *
        cos(Math.toRadians(latitude2)) *
        sin(longitudeDelta / 2).pow(2)
    return 6_371.0 * 2 * kotlin.math.asin(sqrt(a))
}

private fun latestInstant(first: Instant?, second: Instant?): Instant? = when {
    first == null -> second
    second == null -> first
    first >= second -> first
    else -> second
}

private fun firstAvailablePrice(vararg values: Double?): Double? = values.firstOrNull { it != null }

private fun parseDateTime(value: String?): Instant? {
    val text = value.normalizedText() ?: return null
    return listOf(
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    ).firstNotNullOfOrNull { formatter ->
        runCatching { LocalDateTime.parse(text, formatter).toInstant(ZoneOffset.UTC) }.getOrNull()
    }
}

private fun fuelTypeForItaly(description: String): FuelType? {
    val normalized = description.normalizeKey()
    return when {
        normalized.contains("gasolio") || normalized.contains("diesel") -> FuelType.DIESEL
        normalized.contains("benzina") || normalized.contains("super senza piombo") || normalized.contains("super") -> FuelType.GASOLINE
        else -> null
    }
}

private object CsvTable {
    fun parse(text: String): List<CsvRow> {
        val lines = text
            .replace("\uFEFF", "")
            .lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()

        val headerIndex = lines.indexOfFirst { it.contains('|') || it.contains(';') }
        if (headerIndex == -1) {
            return emptyList()
        }

        val headerLine = lines[headerIndex]
        val separator = if (headerLine.count { it == '|' } >= headerLine.count { it == ';' }) '|' else ';'
        val headers = splitCsv(headerLine, separator).map(String::normalizeKey)

        return lines.drop(headerIndex + 1)
            .filter { it.contains(separator) }
            .map { line ->
                val values = splitCsv(line, separator)
                CsvRow(
                    headers.mapIndexed { index, header ->
                        header to values.getOrElse(index) { "" }
                    }.toMap(),
                )
            }
    }

    private fun splitCsv(line: String, separator: Char): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 2
                    continue
                }
                char == '"' -> inQuotes = inQuotes.not()
                char == separator && inQuotes.not() -> {
                    cells += current.toString()
                    current.setLength(0)
                }
                else -> current.append(char)
            }
            index += 1
        }

        cells += current.toString()
        return cells
    }
}

private data class CsvRow(
    private val values: Map<String, String>,
) {
    fun string(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            values[key.normalizeKey()]?.normalizedText()
        }

    fun double(vararg keys: String): Double? = string(*keys)?.toFlexibleDouble()

    fun coordinate(vararg keys: String): Double? = string(*keys)?.toFlexibleCoordinate()

    fun bool(vararg keys: String): Boolean? = when (string(*keys)?.lowercase(Locale.US)) {
        "1", "true", "yes" -> true
        "0", "false", "no" -> false
        else -> null
    }
}

private fun String?.normalizedText(): String? = this?.trim()?.takeIf(String::isNotBlank)

private fun String.normalizeKey(): String =
    Normalizer.normalize(trim(), Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.US)

private fun String?.toFlexibleDouble(): Double? =
    normalizedText()
        ?.replace(",", ".")
        ?.toDoubleOrNull()
        ?.takeIf { it > 0 }

private fun String?.toFlexibleCoordinate(): Double? =
    normalizedText()
        ?.replace(",", ".")
        ?.toDoubleOrNull()
