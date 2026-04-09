package com.iloapps.nomaddashboard.core.data.localinfo

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.data.localprice.LocalPriceLevelProvider
import com.iloapps.nomaddashboard.core.data.localprice.LocalPriceLevelRequest
import com.iloapps.nomaddashboard.core.database.dao.LocalInfoCacheDao
import com.iloapps.nomaddashboard.core.database.entity.LocalInfoCacheEntity
import com.iloapps.nomaddashboard.core.model.LocalHolidayPhase
import com.iloapps.nomaddashboard.core.model.LocalInfoStatus
import com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorKind
import com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorRow
import com.iloapps.nomaddashboard.core.model.LocalPriceLevelSnapshot
import com.iloapps.nomaddashboard.core.model.LocalPriceLevelStatus
import com.iloapps.nomaddashboard.core.model.LocalPricePrecision
import com.iloapps.nomaddashboard.core.model.LocalPriceSummaryBand
import com.iloapps.nomaddashboard.core.network.api.NagerDateService
import com.iloapps.nomaddashboard.core.network.api.OpenHolidaysService
import com.iloapps.nomaddashboard.core.network.model.NagerPublicHoliday
import com.iloapps.nomaddashboard.core.network.model.OpenHolidaysHoliday
import com.iloapps.nomaddashboard.core.network.model.OpenHolidaysLocalizedText
import com.iloapps.nomaddashboard.core.network.model.OpenHolidaysSubdivision
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class DefaultLocalInfoProviderTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `device location with reverse-geocoded region matches school holiday subdivision`() = runTest {
        val provider = provider(
            clock = Clock.fixed(Instant.parse("2026-04-03T08:00:00Z"), ZoneOffset.UTC),
            nagerDateService = RecordingNagerDateService(
                responses = mapOf(
                    "DE:2026" to listOf(
                        NagerPublicHoliday(date = "2026-05-01", name = "Labour Day", countryCode = "DE", global = true),
                    ),
                ),
            ),
            openHolidaysService = RecordingOpenHolidaysService(
                subdivisionResponses = mapOf(
                    "DE" to listOf(
                        subdivision(code = "DE-BY", name = "Bavaria"),
                    ),
                ),
                schoolHolidayResponses = mapOf(
                    "DE:DE-BY:2026-01-01:2026-12-31" to listOf(
                        schoolHoliday("Spring Holidays", "2026-04-01", "2026-04-10", "DE-BY"),
                    ),
                ),
            ),
        )

        val snapshot = provider.localInfo(
            request = LocalInfoRequest(
                locality = "Munich",
                region = "Bavaria",
                countryCode = "DE",
                countryName = "Germany",
                timeZoneId = "Europe/Berlin",
                locationSource = LocalInfoLocationSource.DEVICE,
            ),
            hudUserApiToken = null,
        )

        assertThat(snapshot.status).isEqualTo(LocalInfoStatus.READY)
        assertThat(snapshot.matchedSubdivisionCode).isEqualTo("DE-BY")
        assertThat(snapshot.schoolHoliday?.phase).isEqualTo(LocalHolidayPhase.ON_BREAK)
        assertThat(snapshot.publicHoliday?.phase).isEqualTo(LocalHolidayPhase.NEXT)
    }

    @Test
    fun `ip-only fallback still shows location and public holidays`() = runTest {
        val provider = provider(
            clock = Clock.fixed(Instant.parse("2026-04-30T08:00:00Z"), ZoneOffset.UTC),
            nagerDateService = RecordingNagerDateService(
                responses = mapOf(
                    "FI:2026" to listOf(
                        NagerPublicHoliday(date = "2026-05-01", name = "May Day", countryCode = "FI", global = true),
                    ),
                ),
            ),
        )

        val snapshot = provider.localInfo(
            request = LocalInfoRequest(
                locality = "Helsinki",
                region = "Uusimaa",
                countryCode = "FI",
                countryName = "Finland",
                timeZoneId = "Europe/Helsinki",
                locationSource = LocalInfoLocationSource.IP_GEOLOCATION,
            ),
            hudUserApiToken = null,
        )

        assertThat(snapshot.status).isEqualTo(LocalInfoStatus.READY)
        assertThat(snapshot.locality).isEqualTo("Helsinki")
        assertThat(snapshot.publicHoliday?.phase).isEqualTo(LocalHolidayPhase.TOMORROW)
        assertThat(snapshot.note).contains("IP-based location fallback")
    }

    @Test
    fun `public holidays still show when school holidays cannot be matched`() = runTest {
        val provider = provider(
            clock = Clock.fixed(Instant.parse("2026-04-30T08:00:00Z"), ZoneOffset.UTC),
            nagerDateService = RecordingNagerDateService(
                responses = mapOf(
                    "FR:2026" to listOf(
                        NagerPublicHoliday(date = "2026-05-01", name = "Labour Day", countryCode = "FR", global = true),
                    ),
                ),
            ),
            openHolidaysService = RecordingOpenHolidaysService(
                subdivisionResponses = mapOf(
                    "FR" to listOf(
                        subdivision(code = "FR-IDF", name = "Ile-de-France"),
                    ),
                ),
            ),
        )

        val snapshot = provider.localInfo(
            request = LocalInfoRequest(
                locality = "Paris",
                region = "Unknown Region",
                countryCode = "FR",
                countryName = "France",
                timeZoneId = "Europe/Paris",
                locationSource = LocalInfoLocationSource.DEVICE,
            ),
            hudUserApiToken = null,
        )

        assertThat(snapshot.status).isEqualTo(LocalInfoStatus.READY)
        assertThat(snapshot.publicHoliday?.phase).isEqualTo(LocalHolidayPhase.TOMORROW)
        assertThat(snapshot.schoolHoliday).isNull()
        assertThat(snapshot.note).contains("match your region confidently")
    }

    @Test
    fun `year boundary finds next january public holiday`() = runTest {
        val nagerDateService = RecordingNagerDateService(
            responses = mapOf(
                "DE:2026" to emptyList(),
                "DE:2027" to listOf(
                    NagerPublicHoliday(date = "2027-01-01", name = "New Year's Day", countryCode = "DE", global = true),
                ),
            ),
        )
        val provider = provider(
            clock = Clock.fixed(Instant.parse("2026-12-30T08:00:00Z"), ZoneOffset.UTC),
            nagerDateService = nagerDateService,
        )

        val snapshot = provider.localInfo(
            request = LocalInfoRequest(
                locality = "Berlin",
                region = "Berlin",
                countryCode = "DE",
                countryName = "Germany",
                timeZoneId = "Europe/Berlin",
                locationSource = LocalInfoLocationSource.DEVICE,
            ),
            hudUserApiToken = null,
        )

        assertThat(snapshot.publicHoliday?.phase).isEqualTo(LocalHolidayPhase.NEXT)
        assertThat(snapshot.publicHoliday?.period?.startDate.toString()).isEqualTo("2027-01-01")
        assertThat(nagerDateService.requests).containsExactly("DE:2026", "DE:2027").inOrder()
    }

    @Test
    fun `resolved local timezone drives today logic`() = runTest {
        val provider = provider(
            clock = Clock.fixed(Instant.parse("2026-05-01T15:30:00Z"), ZoneOffset.UTC),
            nagerDateService = RecordingNagerDateService(
                responses = mapOf(
                    "JP:2026" to listOf(
                        NagerPublicHoliday(date = "2026-05-02", name = "Constitution Memorial Day", countryCode = "JP", global = true),
                    ),
                ),
            ),
        )

        val snapshot = provider.localInfo(
            request = LocalInfoRequest(
                locality = "Tokyo",
                region = "Tokyo",
                countryCode = "JP",
                countryName = "Japan",
                timeZoneId = "Asia/Tokyo",
                locationSource = LocalInfoLocationSource.DEVICE,
            ),
            hudUserApiToken = null,
        )

        assertThat(snapshot.publicHoliday?.phase).isEqualTo(LocalHolidayPhase.TODAY)
    }

    @Test
    fun `cache avoids duplicate holiday requests`() = runTest {
        val nagerDateService = RecordingNagerDateService(
            responses = mapOf(
                "DE:2026" to listOf(
                    NagerPublicHoliday(date = "2026-05-01", name = "Labour Day", countryCode = "DE", global = true),
                ),
            ),
        )
        val openHolidaysService = RecordingOpenHolidaysService(
            subdivisionResponses = mapOf(
                "DE" to listOf(subdivision(code = "DE-BY", name = "Bavaria")),
            ),
            schoolHolidayResponses = mapOf(
                "DE:DE-BY:2026-01-01:2026-12-31" to listOf(
                    schoolHoliday("Spring Holidays", "2026-04-01", "2026-04-10", "DE-BY"),
                ),
            ),
        )
        val provider = provider(
            clock = Clock.fixed(Instant.parse("2026-04-03T08:00:00Z"), ZoneOffset.UTC),
            nagerDateService = nagerDateService,
            openHolidaysService = openHolidaysService,
        )
        val request = LocalInfoRequest(
            locality = "Munich",
            region = "Bavaria",
            countryCode = "DE",
            countryName = "Germany",
            timeZoneId = "Europe/Berlin",
            locationSource = LocalInfoLocationSource.DEVICE,
        )

        provider.localInfo(request, hudUserApiToken = null)
        provider.localInfo(request, hudUserApiToken = null)

        assertThat(nagerDateService.requests).containsExactly("DE:2026")
        assertThat(openHolidaysService.subdivisionRequests).containsExactly("DE")
        assertThat(openHolidaysService.schoolHolidayRequests)
            .containsExactly("DE:DE-BY:2026-01-01:2026-12-31")
    }

    private fun provider(
        clock: Clock = Clock.fixed(Instant.parse("2026-04-03T08:00:00Z"), ZoneOffset.UTC),
        cacheDao: LocalInfoCacheDao = FakeLocalInfoCacheDao(),
        nagerDateService: RecordingNagerDateService = RecordingNagerDateService(),
        openHolidaysService: RecordingOpenHolidaysService = RecordingOpenHolidaysService(),
        localPriceLevelProvider: LocalPriceLevelProvider = FixedLocalPriceLevelProvider(),
    ): DefaultLocalInfoProvider =
        DefaultLocalInfoProvider(
            json = json,
            cacheDao = cacheDao,
            nagerDateService = nagerDateService,
            openHolidaysService = openHolidaysService,
            localPriceLevelProvider = localPriceLevelProvider,
            clock = clock,
        )

    private fun subdivision(
        code: String,
        name: String,
    ): OpenHolidaysSubdivision =
        OpenHolidaysSubdivision(
            code = code,
            shortName = code.substringAfterLast('-'),
            name = listOf(OpenHolidaysLocalizedText(language = "EN", text = name)),
        )

    private fun schoolHoliday(
        name: String,
        startDate: String,
        endDate: String,
        subdivisionCode: String,
    ): OpenHolidaysHoliday =
        OpenHolidaysHoliday(
            startDate = startDate,
            endDate = endDate,
            type = "School",
            name = listOf(OpenHolidaysLocalizedText(language = "EN", text = name)),
            subdivisions = emptyList(),
        )

    private class FakeLocalInfoCacheDao : LocalInfoCacheDao {
        private val cache = mutableMapOf<String, LocalInfoCacheEntity>()

        override suspend fun getByCacheKey(cacheKey: String): LocalInfoCacheEntity? = cache[cacheKey]

        override suspend fun upsert(entity: LocalInfoCacheEntity) {
            cache[entity.cacheKey] = entity
        }
    }

    private class RecordingNagerDateService(
        val responses: Map<String, List<NagerPublicHoliday>> = emptyMap(),
    ) : NagerDateService {
        val requests = mutableListOf<String>()

        override suspend fun publicHolidays(year: Int, countryCode: String): List<NagerPublicHoliday> {
            val key = "$countryCode:$year"
            requests += key
            return responses[key].orEmpty()
        }
    }

    private class RecordingOpenHolidaysService(
        val subdivisionResponses: Map<String, List<OpenHolidaysSubdivision>> = emptyMap(),
        val schoolHolidayResponses: Map<String, List<OpenHolidaysHoliday>> = emptyMap(),
    ) : OpenHolidaysService {
        val subdivisionRequests = mutableListOf<String>()
        val schoolHolidayRequests = mutableListOf<String>()

        override suspend fun subdivisions(countryIsoCode: String): List<OpenHolidaysSubdivision> {
            subdivisionRequests += countryIsoCode
            return subdivisionResponses[countryIsoCode].orEmpty()
        }

        override suspend fun schoolHolidays(
            countryIsoCode: String,
            subdivisionCode: String,
            languageIsoCode: String,
            validFrom: String,
            validTo: String,
        ): List<OpenHolidaysHoliday> {
            val key = "$countryIsoCode:$subdivisionCode:$validFrom:$validTo"
            schoolHolidayRequests += key
            return schoolHolidayResponses[key].orEmpty()
        }
    }

    private class FixedLocalPriceLevelProvider : LocalPriceLevelProvider {
        override suspend fun prices(
            request: LocalPriceLevelRequest,
            hudUserApiToken: String?,
        ): LocalPriceLevelSnapshot =
            LocalPriceLevelSnapshot(
                status = LocalPriceLevelStatus.READY,
                summaryBand = LocalPriceSummaryBand.MEDIUM,
                countryCode = request.countryCode,
                countryName = request.countryName,
                rows = listOf(
                    LocalPriceIndicatorRow(
                        kind = LocalPriceIndicatorKind.MEAL_OUT,
                        value = "Moderate",
                        detail = "Country fallback · 2024",
                        precision = LocalPricePrecision.COUNTRY_FALLBACK,
                        source = "Eurostat",
                    ),
                ),
                sources = listOf("Eurostat"),
            )

        override suspend fun clearUsCache() = Unit
    }
}
