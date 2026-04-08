package com.iloapps.nomaddashboard.core.data.localprice

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.database.dao.LocalPriceCacheDao
import com.iloapps.nomaddashboard.core.database.entity.LocalPriceCacheEntity
import com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorKind
import com.iloapps.nomaddashboard.core.model.LocalPriceLevelStatus
import com.iloapps.nomaddashboard.core.model.LocalPricePrecision
import com.iloapps.nomaddashboard.core.model.LocalPriceSummaryBand
import com.iloapps.nomaddashboard.core.network.api.CensusGeocoderService
import com.iloapps.nomaddashboard.core.network.api.EurostatService
import com.iloapps.nomaddashboard.core.network.api.HudUserFmrService
import com.iloapps.nomaddashboard.core.network.model.CensusCountyLookupResponse
import com.iloapps.nomaddashboard.core.network.model.EurostatDatasetResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

class DefaultLocalPriceLevelProviderTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `eurostat parser reads sparse latest value`() = runTest {
        val eurostatService = RecordingEurostatService(
            responses = mapOf(
                "EL:A0111" to eurostatDataset(
                    """
                    {
                      "value": { "27": 84.4, "29": 84.1 },
                      "dimension": { "time": { "category": { "index": { "2022": 27, "2023": 28, "2024": 29 } } } }
                    }
                    """.trimIndent(),
                ),
                "EL:A0101" to eurostatDataset(
                    """
                    {
                      "value": { "29": 95.6 },
                      "dimension": { "time": { "category": { "index": { "2024": 29 } } } }
                    }
                    """.trimIndent(),
                ),
                "EL:A01" to eurostatDataset(
                    """
                    {
                      "value": { "29": 90.9 },
                      "dimension": { "time": { "category": { "index": { "2024": 29 } } } }
                    }
                    """.trimIndent(),
                ),
            ),
        )
        val provider = provider(
            eurostatService = eurostatService,
        )

        val snapshot = provider.prices(
            request = LocalPriceLevelRequest(countryCode = "GR", countryName = "Greece"),
            hudUserApiToken = null,
        )

        assertThat(snapshot.status).isEqualTo(LocalPriceLevelStatus.READY)
        assertThat(snapshot.countryCode).isEqualTo("EL")
        assertThat(snapshot.summaryBand).isEqualTo(LocalPriceSummaryBand.LOW)
        assertThat(eurostatService.requestedCountryCodes).contains("EL")
        assertThat(snapshot.rows.map { it.kind }).containsExactly(
            LocalPriceIndicatorKind.MEAL_OUT,
            LocalPriceIndicatorKind.GROCERIES,
            LocalPriceIndicatorKind.OVERALL,
        ).inOrder()
        assertThat(snapshot.rows.first().detail).isEqualTo("16% below EU average · Country fallback · 2024")
    }

    @Test
    fun `europe returns partial when one category is missing`() = runTest {
        val provider = provider(
            eurostatService = RecordingEurostatService(
                responses = mapOf(
                    "ES:A0111" to eurostatValue(84.1, 2024),
                    "ES:A0101" to eurostatEmpty(),
                    "ES:A01" to eurostatValue(100.0, 2024),
                ),
            ),
        )

        val snapshot = provider.prices(
            request = LocalPriceLevelRequest(countryCode = "ES", countryName = "Spain"),
            hudUserApiToken = null,
        )

        assertThat(snapshot.status).isEqualTo(LocalPriceLevelStatus.PARTIAL)
        assertThat(snapshot.rows).hasSize(2)
    }

    @Test
    fun `europe returns unsupported when eurostat has no rows`() = runTest {
        val provider = provider(
            eurostatService = RecordingEurostatService(
                responses = mapOf(
                    "ES:A0111" to eurostatEmpty(),
                    "ES:A0101" to eurostatEmpty(),
                    "ES:A01" to eurostatEmpty(),
                ),
            ),
        )

        val snapshot = provider.prices(
            request = LocalPriceLevelRequest(countryCode = "ES", countryName = "Spain"),
            hudUserApiToken = null,
        )

        assertThat(snapshot.status).isEqualTo(LocalPriceLevelStatus.UNSUPPORTED)
    }

    @Test
    fun `europe source failure returns unavailable`() = runTest {
        val snapshot = provider(
            eurostatService = object : EurostatService {
                override suspend fun purchasingPowerParity(
                    countryCode: String,
                    indicator: String,
                    category: String,
                    frequency: String,
                    language: String,
                ): EurostatDatasetResponse = error("upstream failure")
            },
        ).prices(
            request = LocalPriceLevelRequest(countryCode = "ES", countryName = "Spain"),
            hudUserApiToken = null,
        )

        assertThat(snapshot.status).isEqualTo(LocalPriceLevelStatus.UNAVAILABLE)
    }

    @Test
    fun `us without token returns configuration required`() = runTest {
        val snapshot = provider().prices(
            request = LocalPriceLevelRequest(
                latitude = 47.61,
                longitude = -122.33,
                countryCode = "US",
                countryName = "United States",
            ),
            hudUserApiToken = null,
        )

        assertThat(snapshot.status).isEqualTo(LocalPriceLevelStatus.CONFIGURATION_REQUIRED)
    }

    @Test
    fun `us without coordinate returns location required`() = runTest {
        val snapshot = provider().prices(
            request = LocalPriceLevelRequest(countryCode = "US", countryName = "United States"),
            hudUserApiToken = "hud-token-123",
        )

        assertThat(snapshot.status).isEqualTo(LocalPriceLevelStatus.LOCATION_REQUIRED)
    }

    @Test
    fun `hud object payload maps county benchmark`() = runTest {
        val snapshot = provider(
            censusGeocoderService = FixedCensusGeocoderService(),
            hudUserFmrService = FixedHudUserFmrService(
                """
                {
                  "data": {
                    "county_name": "King County",
                    "area_name": "King County",
                    "basicdata": {
                      "One-Bedroom": 1803,
                      "year": 2024
                    }
                  }
                }
                """.trimIndent(),
            ),
        ).prices(
            request = LocalPriceLevelRequest(
                latitude = 47.61,
                longitude = -122.33,
                countryCode = "US",
                countryName = "United States",
            ),
            hudUserApiToken = "hud-token-123",
        )

        assertThat(snapshot.status).isEqualTo(LocalPriceLevelStatus.PARTIAL)
        assertThat(snapshot.summaryBand).isEqualTo(LocalPriceSummaryBand.LIMITED)
        assertThat(snapshot.rows.single().precision).isEqualTo(LocalPricePrecision.COUNTY_BENCHMARK)
        assertThat(snapshot.rows.single().value).isEqualTo("${'$'}1,803/mo")
    }

    @Test
    fun `hud array payload prefers msa level`() = runTest {
        val snapshot = provider(
            censusGeocoderService = FixedCensusGeocoderService(),
            hudUserFmrService = FixedHudUserFmrService(
                """
                {
                  "data": {
                    "area_name": "Seattle-Bellevue",
                    "year": 2024,
                    "basicdata": [
                      { "zip_code": "98101", "One-Bedroom": 1700 },
                      { "zip_code": "MSA level", "One-Bedroom": 1900 }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        ).prices(
            request = LocalPriceLevelRequest(
                latitude = 47.61,
                longitude = -122.33,
                countryCode = "US",
                countryName = "United States",
            ),
            hudUserApiToken = "hud-token-123",
        )

        assertThat(snapshot.rows.single().precision).isEqualTo(LocalPricePrecision.METRO_BENCHMARK)
        assertThat(snapshot.rows.single().value).isEqualTo("${'$'}1,900/mo")
        assertThat(snapshot.rows.single().detail).isEqualTo("Metro benchmark · Seattle-Bellevue · 2024")
    }

    @Test
    fun `county geoid lookup model decodes counties array`() {
        val response = json.decodeFromString<CensusCountyLookupResponse>(
            """
            {
              "result": {
                "geographies": {
                  "Counties": [
                    { "GEOID": "53033", "NAME": "King County" }
                  ]
                }
              }
            }
            """.trimIndent(),
        )

        assertThat(response.result.geographies.counties.single().geoid).isEqualTo("53033")
        assertThat(response.result.geographies.counties.single().name).isEqualTo("King County")
    }

    @Test
    fun `unsupported country returns unsupported`() = runTest {
        val snapshot = provider().prices(
            request = LocalPriceLevelRequest(countryCode = "JP", countryName = "Japan"),
            hudUserApiToken = null,
        )

        assertThat(snapshot.status).isEqualTo(LocalPriceLevelStatus.UNSUPPORTED)
    }

    private fun provider(
        cacheDao: LocalPriceCacheDao = FakeLocalPriceCacheDao(),
        eurostatService: EurostatService = RecordingEurostatService(),
        censusGeocoderService: CensusGeocoderService = FixedCensusGeocoderService(),
        hudUserFmrService: HudUserFmrService = FixedHudUserFmrService(
            """
            {
              "data": {
                "area_name": "Seattle metro",
                "year": 2024,
                "basicdata": [
                  { "zip_code": "MSA level", "One-Bedroom": 1900 }
                ]
              }
            }
            """.trimIndent(),
        ),
    ): DefaultLocalPriceLevelProvider =
        DefaultLocalPriceLevelProvider(
            json = json,
            cacheDao = cacheDao,
            eurostatService = eurostatService,
            censusGeocoderService = censusGeocoderService,
            hudUserFmrService = hudUserFmrService,
        )

    private fun eurostatValue(value: Double, year: Int): EurostatDatasetResponse =
        eurostatDataset(
            """
            {
              "value": { "0": $value },
              "dimension": { "time": { "category": { "index": { "$year": 0 } } } }
            }
            """.trimIndent(),
        )

    private fun eurostatEmpty(): EurostatDatasetResponse =
        eurostatDataset("""{ "value": {}, "dimension": { "time": { "category": { "index": {} } } } }""")

    private fun eurostatDataset(raw: String): EurostatDatasetResponse =
        json.decodeFromString(raw)
}

private class FakeLocalPriceCacheDao : LocalPriceCacheDao {
    private val entities = linkedMapOf<String, LocalPriceCacheEntity>()

    override suspend fun getByCacheKey(cacheKey: String): LocalPriceCacheEntity? = entities[cacheKey]

    override suspend fun upsert(entity: LocalPriceCacheEntity) {
        entities[entity.cacheKey] = entity
    }

    override suspend fun clearUsEntries() {
        entities.keys.filter { it.startsWith("US|") }.toList().forEach(entities::remove)
    }
}

private class RecordingEurostatService(
    val responses: Map<String, EurostatDatasetResponse> = emptyMap(),
) : EurostatService {
    val requestedCountryCodes = mutableListOf<String>()

    override suspend fun purchasingPowerParity(
        countryCode: String,
        indicator: String,
        category: String,
        frequency: String,
        language: String,
    ): EurostatDatasetResponse {
        requestedCountryCodes += countryCode
        return responses["$countryCode:$category"] ?: throw IllegalStateException("Missing Eurostat fixture for $countryCode:$category")
    }
}

private class FixedCensusGeocoderService : CensusGeocoderService {
    override suspend fun countyForCoordinate(
        longitude: Double,
        latitude: Double,
        benchmark: String,
        vintage: String,
        format: String,
    ): CensusCountyLookupResponse =
        Json { ignoreUnknownKeys = true; explicitNulls = false }.decodeFromString(
            """
            {
              "result": {
                "geographies": {
                  "Counties": [
                    { "GEOID": "53033", "NAME": "King County" }
                  ]
                }
              }
            }
            """.trimIndent(),
        )
}

private class FixedHudUserFmrService(
    private val payload: String,
) : HudUserFmrService {
    override suspend fun fairMarketRent(
        countyGEOID: String,
        authorization: String,
        accept: String,
    ): ResponseBody = payload.toResponseBody()
}
