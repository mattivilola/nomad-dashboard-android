package com.iloapps.nomaddashboard.core.data.localinfo

import com.iloapps.nomaddashboard.core.data.localprice.LocalPriceLevelProvider
import com.iloapps.nomaddashboard.core.data.localprice.LocalPriceLevelRequest
import com.iloapps.nomaddashboard.core.database.dao.LocalInfoCacheDao
import com.iloapps.nomaddashboard.core.database.entity.LocalInfoCacheEntity
import com.iloapps.nomaddashboard.core.model.HolidayPeriod
import com.iloapps.nomaddashboard.core.model.HolidaySourceAttribution
import com.iloapps.nomaddashboard.core.model.LocalHolidayPhase
import com.iloapps.nomaddashboard.core.model.LocalHolidayStatus
import com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot
import com.iloapps.nomaddashboard.core.model.LocalInfoStatus
import com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorKind
import com.iloapps.nomaddashboard.core.model.LocalPriceLevelSnapshot
import com.iloapps.nomaddashboard.core.model.LocalPriceLevelStatus
import com.iloapps.nomaddashboard.core.network.api.NagerDateService
import com.iloapps.nomaddashboard.core.network.api.OpenHolidaysService
import com.iloapps.nomaddashboard.core.network.model.NagerPublicHoliday
import com.iloapps.nomaddashboard.core.network.model.OpenHolidaysHoliday
import com.iloapps.nomaddashboard.core.network.model.OpenHolidaysLocalizedText
import com.iloapps.nomaddashboard.core.network.model.OpenHolidaysSubdivision
import java.text.Normalizer
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException

@Singleton
class DefaultLocalInfoProvider @Inject constructor(
    private val json: Json,
    private val cacheDao: LocalInfoCacheDao,
    private val nagerDateService: NagerDateService,
    private val openHolidaysService: OpenHolidaysService,
    private val localPriceLevelProvider: LocalPriceLevelProvider,
    private val clock: Clock,
) : LocalInfoProvider {
    override suspend fun localInfo(
        request: LocalInfoRequest,
        hudUserApiToken: String?,
    ): LocalInfoSnapshot {
        val countryCode = request.countryCode.normalizedCountryCode()
        val countryName = request.countryName?.trim()?.takeIf(String::isNotBlank)
        if (countryCode == null) {
            return LocalInfoSnapshot(
                status = LocalInfoStatus.LOCATION_REQUIRED,
                locality = request.locality?.trim()?.takeIf(String::isNotBlank),
                region = request.region?.trim()?.takeIf(String::isNotBlank),
                countryName = countryName,
                timezone = request.timeZoneId,
                sources = capabilitySources(),
                detail = "Allow location or enable IP-based location to show Local Info.",
            )
        }

        val timezoneId = request.timeZoneId?.trim()?.takeIf(String::isNotBlank)
        val zoneId = timezoneId?.let(::safeZoneId) ?: ZoneId.systemDefault()
        val today = LocalDate.now(clock.withZone(zoneId))
        val locationNote = when (request.locationSource) {
            LocalInfoLocationSource.IP_GEOLOCATION -> "Using IP-based location fallback."
            LocalInfoLocationSource.DEVICE -> null
        }
        val fetchedAt = Instant.now(clock)

        val publicHolidayResult = resolvePublicHoliday(
            countryCode = countryCode,
            countryName = countryName,
            subdivisionCode = null,
            today = today,
        )
        val schoolHolidayResult = resolveSchoolHoliday(
            request = request,
            countryCode = countryCode,
            today = today,
        )
        val scopedPublicHolidayResult = if (schoolHolidayResult.matchedSubdivision != null) {
            resolvePublicHoliday(
                countryCode = countryCode,
                countryName = countryName,
                subdivisionCode = schoolHolidayResult.matchedSubdivision.code,
                today = today,
            )
        } else {
            publicHolidayResult
        }
        val localPriceLevel = resolveLocalPriceLevel(
            request = request,
            countryCode = countryCode,
            countryName = countryName,
            hudUserApiToken = hudUserApiToken,
        )

        val noteParts = buildList {
            addIfNotBlank(locationNote)
            addIfNotBlank(schoolHolidayResult.note)
            addIfNotBlank(scopedPublicHolidayResult.note)
            when (localPriceLevel.status) {
                LocalPriceLevelStatus.CONFIGURATION_REQUIRED,
                LocalPriceLevelStatus.LOCATION_REQUIRED,
                LocalPriceLevelStatus.UNSUPPORTED,
                LocalPriceLevelStatus.UNAVAILABLE,
                -> addIfNotBlank(localPriceLevel.detail)
                LocalPriceLevelStatus.READY,
                LocalPriceLevelStatus.PARTIAL,
                -> addIfNotBlank(localPriceLevel.note)
            }
        }

        val sources = linkedMapOf<String, HolidaySourceAttribution>().apply {
            listOfNotNull(scopedPublicHolidayResult.source, schoolHolidayResult.source).forEach { put(it.name, it) }
            localPriceLevel.sources.forEach { put(it, HolidaySourceAttribution(name = it, url = sourceUrl(it))) }
        }.values.toList()

        val status = aggregateStatus(
            publicHoliday = scopedPublicHolidayResult.holiday,
            publicHolidayUnsupported = scopedPublicHolidayResult.unsupported,
            publicHolidayUnavailable = scopedPublicHolidayResult.unavailable,
            schoolHolidayMatched = schoolHolidayResult.matchedSubdivision != null,
            schoolHoliday = schoolHolidayResult.holiday,
            schoolHolidayUnsupported = schoolHolidayResult.unsupported,
            schoolHolidayUnavailable = schoolHolidayResult.unavailable,
            localPriceLevel = localPriceLevel,
        )
        val detail = detailFor(
            status = status,
            publicHoliday = scopedPublicHolidayResult.holiday,
            schoolHoliday = schoolHolidayResult.holiday,
            localPriceLevel = localPriceLevel,
            request = request,
        )

        return LocalInfoSnapshot(
            status = status,
            locality = request.locality?.trim()?.takeIf(String::isNotBlank),
            region = request.region?.trim()?.takeIf(String::isNotBlank),
            countryCode = countryCode,
            countryName = countryName,
            timezone = zoneId.id,
            matchedSubdivisionCode = schoolHolidayResult.matchedSubdivision?.code,
            matchedSubdivisionName = schoolHolidayResult.matchedSubdivision?.name,
            publicHoliday = scopedPublicHolidayResult.holiday,
            schoolHoliday = schoolHolidayResult.holiday,
            localPriceLevel = localPriceLevel,
            sources = sources,
            fetchedAt = fetchedAt,
            detail = detail,
            note = noteParts.joinToString(" ").takeIf(String::isNotBlank),
        )
    }

    private suspend fun resolvePublicHoliday(
        countryCode: String,
        countryName: String?,
        subdivisionCode: String?,
        today: LocalDate,
    ): HolidayLookupResult {
        val holidays = try {
            publicHolidaysFor(countryCode = countryCode, today = today)
        } catch (error: Throwable) {
            return if (error.isUnsupported()) {
                HolidayLookupResult(
                    note = if (countryName != null) {
                        "Public holiday coverage is unavailable for $countryName."
                    } else {
                        "Public holiday coverage is unavailable for this country."
                    },
                    source = PublicHolidaySource,
                    unsupported = true,
                )
            } else {
                HolidayLookupResult(
                    note = "Public holidays are unavailable right now.",
                    source = PublicHolidaySource,
                    unavailable = true,
                )
            }
        }

        val applicable = holidays.filter { holiday ->
            holiday.global || subdivisionCode != null && holiday.counties.orEmpty().contains(subdivisionCode)
        }
        val holiday = nextPublicHoliday(applicable, today)
        return HolidayLookupResult(
            holiday = holiday,
            source = PublicHolidaySource,
            note = if (holiday == null) "No upcoming public holidays found." else null,
            unsupported = false,
            unavailable = holiday == null,
        )
    }

    private suspend fun resolveSchoolHoliday(
        request: LocalInfoRequest,
        countryCode: String,
        today: LocalDate,
    ): SchoolHolidayLookupResult {
        val subdivisions = try {
            subdivisionsFor(countryCode)
        } catch (error: Throwable) {
            return if (error.isUnsupported()) {
                SchoolHolidayLookupResult(
                    note = "School holiday coverage is unavailable for this country.",
                    source = SchoolHolidaySource,
                    unsupported = true,
                )
            } else {
                SchoolHolidayLookupResult(
                    note = "School holidays are unavailable right now.",
                    source = SchoolHolidaySource,
                    unavailable = true,
                )
            }
        }

        if (subdivisions.isEmpty()) {
            return SchoolHolidayLookupResult(
                note = "School holiday coverage is unavailable for this country.",
                source = SchoolHolidaySource,
                unsupported = true,
            )
        }

        val matchedSubdivision = matchSubdivision(
            subdivisions = subdivisions,
            region = request.region,
            locality = request.locality,
        ) ?: return SchoolHolidayLookupResult(
            note = subdivisionMatchNote(request),
            source = SchoolHolidaySource,
        )

        val holidays = try {
            schoolHolidaysFor(
                countryCode = countryCode,
                subdivisionCode = matchedSubdivision.code,
                today = today,
            )
        } catch (error: Throwable) {
            return if (error.isUnsupported()) {
                SchoolHolidayLookupResult(
                    matchedSubdivision = matchedSubdivision,
                    note = "School holiday coverage is unavailable for ${matchedSubdivision.name}.",
                    source = SchoolHolidaySource,
                    unsupported = true,
                )
            } else {
                SchoolHolidayLookupResult(
                    matchedSubdivision = matchedSubdivision,
                    note = "School holidays are unavailable right now.",
                    source = SchoolHolidaySource,
                    unavailable = true,
                )
            }
        }

        return SchoolHolidayLookupResult(
            matchedSubdivision = matchedSubdivision,
            holiday = nextSchoolHoliday(holidays, today),
            source = SchoolHolidaySource,
        )
    }

    private suspend fun resolveLocalPriceLevel(
        request: LocalInfoRequest,
        countryCode: String,
        countryName: String?,
        hudUserApiToken: String?,
    ): LocalPriceLevelSnapshot =
        runCatching {
            localPriceLevelProvider.prices(
                request = LocalPriceLevelRequest(
                    latitude = request.latitude,
                    longitude = request.longitude,
                    locality = request.locality,
                    countryCode = countryCode,
                    countryName = countryName,
                ),
                hudUserApiToken = hudUserApiToken,
            )
        }.getOrElse {
            LocalPriceLevelSnapshot(
                status = LocalPriceLevelStatus.UNAVAILABLE,
                countryCode = countryCode,
                countryName = countryName,
                sources = listOf("Eurostat", "HUD USER", "US Census Geocoder"),
                fetchedAt = Instant.now(clock),
                detail = "Local price signals are unavailable right now.",
            )
        }

    private fun aggregateStatus(
        publicHoliday: LocalHolidayStatus?,
        publicHolidayUnsupported: Boolean,
        publicHolidayUnavailable: Boolean,
        schoolHolidayMatched: Boolean,
        schoolHoliday: LocalHolidayStatus?,
        schoolHolidayUnsupported: Boolean,
        schoolHolidayUnavailable: Boolean,
        localPriceLevel: LocalPriceLevelSnapshot,
    ): LocalInfoStatus {
        val hasPriceRows = localPriceLevel.rows.isNotEmpty()
        val anySignals = publicHoliday != null || schoolHoliday != null || hasPriceRows
        val priceIssue = localPriceLevel.status in setOf(
            LocalPriceLevelStatus.CONFIGURATION_REQUIRED,
            LocalPriceLevelStatus.LOCATION_REQUIRED,
            LocalPriceLevelStatus.UNSUPPORTED,
            LocalPriceLevelStatus.UNAVAILABLE,
        )

        return when {
            publicHoliday == null && anySignals.not() -> {
                if (publicHolidayUnsupported || schoolHolidayUnsupported || localPriceLevel.status == LocalPriceLevelStatus.UNSUPPORTED) {
                    LocalInfoStatus.UNSUPPORTED
                } else {
                    LocalInfoStatus.UNAVAILABLE
                }
            }

            publicHoliday == null && anySignals -> {
                if (publicHolidayUnsupported) LocalInfoStatus.UNSUPPORTED else LocalInfoStatus.PARTIAL
            }

            publicHoliday != null && (publicHolidayUnavailable || priceIssue || schoolHolidayUnavailable) -> LocalInfoStatus.PARTIAL
            publicHoliday != null && schoolHolidayMatched && schoolHoliday == null -> {
                if (schoolHolidayUnsupported) LocalInfoStatus.UNSUPPORTED else LocalInfoStatus.PARTIAL
            }

            publicHoliday != null && localPriceLevel.status == LocalPriceLevelStatus.UNSUPPORTED && hasPriceRows.not() ->
                LocalInfoStatus.UNSUPPORTED

            anySignals -> LocalInfoStatus.READY
            else -> LocalInfoStatus.UNAVAILABLE
        }
    }

    private fun detailFor(
        status: LocalInfoStatus,
        publicHoliday: LocalHolidayStatus?,
        schoolHoliday: LocalHolidayStatus?,
        localPriceLevel: LocalPriceLevelSnapshot,
        request: LocalInfoRequest,
    ): String =
        when (status) {
            LocalInfoStatus.OFF -> "Local Info is disabled. Enable it in Settings."
            LocalInfoStatus.CHECKING -> "Looking up local context and holiday calendar."
            LocalInfoStatus.LOCATION_REQUIRED ->
                "Allow location or enable IP-based location to show Local Info."
            LocalInfoStatus.UNSUPPORTED -> "Local context is available, but some Local Info sources do not cover this location yet."
            LocalInfoStatus.UNAVAILABLE ->
                "Local Info is unavailable right now."
            LocalInfoStatus.PARTIAL,
            LocalInfoStatus.READY,
            -> listOfNotNull(
                publicHoliday?.let { "Public holiday ready" },
                schoolHoliday?.let { "School holiday ready" },
                localPriceLevel.rows.takeIf { it.isNotEmpty() }?.let { "Local price signals ready" },
            ).joinToString(" · ").ifBlank {
                listOfNotNull(
                    request.countryName,
                    localPriceLevel.detail,
                ).joinToString(" · ").ifBlank { "Local context ready." }
            }
        }

    private suspend fun publicHolidaysFor(
        countryCode: String,
        today: LocalDate,
    ): List<NagerPublicHoliday> {
        val currentYear = cachedOrFresh(
            cacheKey = "public|$countryCode|${today.year}",
            ttl = HolidayCacheTtl,
            serializer = ListSerializer(NagerPublicHoliday.serializer()),
        ) {
            nagerDateService.publicHolidays(year = today.year, countryCode = countryCode)
        }
        if (today.monthValue < 12 && currentYear.any { LocalDate.parse(it.date) >= today }) {
            return currentYear
        }
        val nextYear = cachedOrFresh(
            cacheKey = "public|$countryCode|${today.year + 1}",
            ttl = HolidayCacheTtl,
            serializer = ListSerializer(NagerPublicHoliday.serializer()),
        ) {
            nagerDateService.publicHolidays(year = today.year + 1, countryCode = countryCode)
        }
        return currentYear + nextYear
    }

    private suspend fun subdivisionsFor(countryCode: String): List<OpenHolidaysSubdivision> =
        cachedOrFresh(
            cacheKey = "subdivision|$countryCode",
            ttl = SubdivisionCacheTtl,
            serializer = ListSerializer(OpenHolidaysSubdivision.serializer()),
        ) {
            openHolidaysService.subdivisions(countryIsoCode = countryCode)
        }

    private suspend fun schoolHolidaysFor(
        countryCode: String,
        subdivisionCode: String,
        today: LocalDate,
    ): List<OpenHolidaysHoliday> {
        val currentYear = cachedOrFresh(
            cacheKey = "school|$countryCode|$subdivisionCode|${today.year}",
            ttl = HolidayCacheTtl,
            serializer = ListSerializer(OpenHolidaysHoliday.serializer()),
        ) {
            openHolidaysService.schoolHolidays(
                countryIsoCode = countryCode,
                subdivisionCode = subdivisionCode,
                validFrom = LocalDate.of(today.year, 1, 1).toString(),
                validTo = LocalDate.of(today.year, 12, 31).toString(),
            )
        }
        if (today.monthValue < 12 && currentYear.any { LocalDate.parse(it.endDate) >= today }) {
            return currentYear
        }
        val nextYear = cachedOrFresh(
            cacheKey = "school|$countryCode|$subdivisionCode|${today.year + 1}",
            ttl = HolidayCacheTtl,
            serializer = ListSerializer(OpenHolidaysHoliday.serializer()),
        ) {
            openHolidaysService.schoolHolidays(
                countryIsoCode = countryCode,
                subdivisionCode = subdivisionCode,
                validFrom = LocalDate.of(today.year + 1, 1, 1).toString(),
                validTo = LocalDate.of(today.year + 1, 12, 31).toString(),
            )
        }
        return currentYear + nextYear
    }

    private suspend fun <T> cachedOrFresh(
        cacheKey: String,
        ttl: Duration,
        serializer: KSerializer<T>,
        fetch: suspend () -> T,
    ): T {
        val cached = cacheDao.getByCacheKey(cacheKey)
        val cachedFetchedAt = cached?.fetchedAtEpochMillis?.let(Instant::ofEpochMilli)
        if (cached != null && cachedFetchedAt != null && Duration.between(cachedFetchedAt, Instant.now(clock)) <= ttl) {
            return json.decodeFromString(serializer, cached.payloadJson)
        }

        return try {
            fetch().also { fresh ->
                cacheDao.upsert(
                    LocalInfoCacheEntity(
                        cacheKey = cacheKey,
                        payloadJson = json.encodeToString(serializer, fresh),
                        fetchedAtEpochMillis = Instant.now(clock).toEpochMilli(),
                    ),
                )
            }
        } catch (error: Throwable) {
            if (cached != null) {
                json.decodeFromString(serializer, cached.payloadJson)
            } else {
                throw error
            }
        }
    }

    private fun nextPublicHoliday(
        holidays: List<NagerPublicHoliday>,
        today: LocalDate,
    ): LocalHolidayStatus? {
        val sorted = holidays.mapNotNull { holiday ->
            val date = holiday.date.parseDateOrNull() ?: return@mapNotNull null
            holiday to date
        }.sortedBy { it.second }
        val todayHoliday = sorted.firstOrNull { (_, date) -> date == today } ?: return sorted.firstOrNull { (_, date) ->
            date == today.plusDays(1)
        }?.let { (holiday, date) ->
            LocalHolidayStatus(
                phase = LocalHolidayPhase.TOMORROW,
                period = HolidayPeriod(
                    name = holiday.name?.takeIf(String::isNotBlank) ?: holiday.localName.orEmpty(),
                    startDate = date,
                ),
            )
        } ?: sorted.firstOrNull { (_, date) -> date > today }?.let { (holiday, date) ->
            LocalHolidayStatus(
                phase = LocalHolidayPhase.NEXT,
                period = HolidayPeriod(
                    name = holiday.name?.takeIf(String::isNotBlank) ?: holiday.localName.orEmpty(),
                    startDate = date,
                ),
            )
        }

        val (holiday, date) = todayHoliday
        return LocalHolidayStatus(
            phase = LocalHolidayPhase.TODAY,
            period = HolidayPeriod(
                name = holiday.name?.takeIf(String::isNotBlank) ?: holiday.localName.orEmpty(),
                startDate = date,
            ),
        )
    }

    private fun nextSchoolHoliday(
        holidays: List<OpenHolidaysHoliday>,
        today: LocalDate,
    ): LocalHolidayStatus? {
        val sorted = holidays.mapNotNull { holiday ->
            val startDate = holiday.startDate.parseDateOrNull() ?: return@mapNotNull null
            val endDate = holiday.endDate.parseDateOrNull() ?: return@mapNotNull null
            holiday to (startDate to endDate)
        }.sortedBy { it.second.first }

        sorted.firstOrNull { (_, dates) -> today in dates.first..dates.second }?.let { (holiday, dates) ->
            return LocalHolidayStatus(
                phase = LocalHolidayPhase.ON_BREAK,
                period = HolidayPeriod(
                    name = holiday.displayName(),
                    startDate = dates.first,
                    endDate = dates.second,
                ),
            )
        }
        sorted.firstOrNull { (_, dates) -> dates.first == today.plusDays(1) }?.let { (holiday, dates) ->
            return LocalHolidayStatus(
                phase = LocalHolidayPhase.TOMORROW,
                period = HolidayPeriod(
                    name = holiday.displayName(),
                    startDate = dates.first,
                    endDate = dates.second,
                ),
            )
        }
        sorted.firstOrNull { (_, dates) -> dates.first > today }?.let { (holiday, dates) ->
            return LocalHolidayStatus(
                phase = LocalHolidayPhase.NEXT,
                period = HolidayPeriod(
                    name = holiday.displayName(),
                    startDate = dates.first,
                    endDate = dates.second,
                ),
            )
        }
        return null
    }

    private fun matchSubdivision(
        subdivisions: List<OpenHolidaysSubdivision>,
        region: String?,
        locality: String?,
    ): MatchedSubdivision? {
        val candidates = subdivisions.flatMap { it.flattened() }
        exactSubdivisionMatch(region, candidates)?.let { return it }
        return exactSubdivisionMatch(locality, candidates)
    }

    private fun exactSubdivisionMatch(
        rawValue: String?,
        candidates: List<MatchedSubdivision>,
    ): MatchedSubdivision? {
        val normalizedValue = rawValue.normalizedLookupKey() ?: return null
        val matches = candidates.filter { candidate ->
            candidate.lookupKeys.contains(normalizedValue)
        }
        return matches.singleOrNull()
    }

    private fun OpenHolidaysSubdivision.flattened(): List<MatchedSubdivision> =
        buildList {
            add(toMatchedSubdivision())
            children.forEach { child -> addAll(child.flattened()) }
        }

    private fun OpenHolidaysSubdivision.toMatchedSubdivision(): MatchedSubdivision =
        MatchedSubdivision(
            code = code,
            name = displayName(),
            lookupKeys = buildSet {
                addIfNotBlank(code.normalizedLookupKey())
                addIfNotBlank(shortName.normalizedLookupKey())
                addIfNotBlank(isoCode.normalizedLookupKey())
                name.mapNotNull(OpenHolidaysLocalizedText::text)
                    .mapNotNull { it.normalizedLookupKey() }
                    .forEach(::add)
            },
        )

    private fun OpenHolidaysSubdivision.displayName(): String =
        name.firstNotNullOfOrNull { it.text?.trim()?.takeIf(String::isNotBlank) }
            ?: shortName
            ?: code

    private fun OpenHolidaysHoliday.displayName(): String =
        name.firstNotNullOfOrNull { it.text?.trim()?.takeIf(String::isNotBlank) }
            ?: "School break"

    private fun subdivisionMatchNote(request: LocalInfoRequest): String? =
        when {
            request.region?.isNotBlank() == true || request.locality?.isNotBlank() == true ->
                "School holidays appear only when the app can match your region confidently."
            else -> null
        }

    private fun capabilitySources(): List<HolidaySourceAttribution> =
        listOf(
            PublicHolidaySource,
            SchoolHolidaySource,
            HolidaySourceAttribution("Eurostat", sourceUrl("Eurostat")),
            HolidaySourceAttribution("HUD USER", sourceUrl("HUD USER")),
            HolidaySourceAttribution("US Census Geocoder", sourceUrl("US Census Geocoder")),
        )

    private fun sourceUrl(name: String): String? = when (name) {
        "Eurostat" -> "https://ec.europa.eu/eurostat/"
        "HUD USER" -> "https://www.huduser.gov/"
        "US Census Geocoder" -> "https://geocoding.geo.census.gov/"
        else -> null
    }

    private fun Throwable.isUnsupported(): Boolean =
        this is HttpException && code() == 404

    private fun safeZoneId(raw: String): ZoneId =
        runCatching { ZoneId.of(raw) }
            .getOrElse { ZoneOffset.UTC }

    private fun String?.normalizedCountryCode(): String? =
        this?.trim()?.takeIf(String::isNotBlank)?.uppercase(Locale.US)

    private fun String?.normalizedLookupKey(): String? {
        val source = this?.trim()?.takeIf(String::isNotBlank) ?: return null
        return Normalizer.normalize(source, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase(Locale.US)
            .replace("[^a-z0-9]+".toRegex(), "")
            .takeIf(String::isNotBlank)
    }

    private fun MutableList<String>.addIfNotBlank(value: String?) {
        value?.trim()?.takeIf(String::isNotBlank)?.let(::add)
    }

    private fun MutableSet<String>.addIfNotBlank(value: String?) {
        value?.trim()?.takeIf(String::isNotBlank)?.let(::add)
    }

    private fun String.parseDateOrNull(): LocalDate? =
        runCatching { LocalDate.parse(this) }.getOrNull()

    private data class MatchedSubdivision(
        val code: String,
        val name: String,
        val lookupKeys: Set<String>,
    )

    private data class HolidayLookupResult(
        val holiday: LocalHolidayStatus? = null,
        val note: String? = null,
        val source: HolidaySourceAttribution? = null,
        val unsupported: Boolean = false,
        val unavailable: Boolean = false,
    )

    private data class SchoolHolidayLookupResult(
        val matchedSubdivision: MatchedSubdivision? = null,
        val holiday: LocalHolidayStatus? = null,
        val note: String? = null,
        val source: HolidaySourceAttribution? = null,
        val unsupported: Boolean = false,
        val unavailable: Boolean = false,
    )

    private companion object {
        val HolidayCacheTtl: Duration = Duration.ofHours(24)
        val SubdivisionCacheTtl: Duration = Duration.ofDays(7)
        val PublicHolidaySource = HolidaySourceAttribution(
            name = "Nager.Date",
            url = "https://date.nager.at/",
        )
        val SchoolHolidaySource = HolidaySourceAttribution(
            name = "OpenHolidays",
            url = "https://openholidaysapi.org/",
        )
    }
}
