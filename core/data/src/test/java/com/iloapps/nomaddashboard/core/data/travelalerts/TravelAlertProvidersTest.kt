package com.iloapps.nomaddashboard.core.data.travelalerts

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.network.api.ReliefWebReportsService
import com.iloapps.nomaddashboard.core.network.api.SmartravellerService
import com.iloapps.nomaddashboard.core.network.model.ReliefWebReportsRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response

class TravelAlertProvidersTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `bundled neighbor country resolver returns configured borders`() {
        val resolver = BundledNeighborCountryResolver.fromRecords(
            mapOf("ES" to listOf("FR", "PT", "MA")),
        )

        assertThat(resolver.neighboringCountryCodes("es")).containsExactly("FR", "PT", "MA").inOrder()
    }

    @Test
    fun `smartraveller provider parses tolerant payload shapes and picks highest nearby severity`() = runTest {
        val provider = SmartravellerAdvisoryProvider(
            service = object : SmartravellerService {
                override suspend fun destinations() = json.parseToJsonElement(
                    """
                    {
                      "data": [
                        {
                          "title": "Spain",
                          "advice_level": 1,
                          "canonical_url": "https://example.com/spain",
                          "updated_at": "2026-04-07T10:00:00Z"
                        },
                        {
                          "name": "France",
                          "adviceLevel": "3",
                          "link": "https://example.com/france",
                          "updatedAt": "2026-04-07T11:00:00Z"
                        }
                      ]
                    }
                    """.trimIndent(),
                )
            },
            countryNameResolver = CountryNameResolver(),
        )

        val signal = provider.advisory(
            countryCodes = listOf("ES", "FR"),
            primaryCountryCode = "ES",
        )

        assertThat(signal.severity).isEqualTo(TravelAlertSeverity.WARNING)
        assertThat(signal.summary).isEqualTo("France is at Level 3 nearby.")
        assertThat(signal.sourceUrl).isEqualTo("https://example.com/france")
        assertThat(signal.affectedCountryCodes).containsExactly("FR")
    }

    @Test
    fun `smartraveller provider resolves alias country names`() = runTest {
        val provider = SmartravellerAdvisoryProvider(
            service = object : SmartravellerService {
                override suspend fun destinations() = json.parseToJsonElement(
                    """
                    [
                      {
                        "country": "Turkiye",
                        "level": 2,
                        "url": "https://example.com/turkiye",
                        "last_updated": "2026-04-07T09:00:00Z"
                      }
                    ]
                    """.trimIndent(),
                )
            },
            countryNameResolver = CountryNameResolver(),
        )

        val signal = provider.advisory(
            countryCodes = listOf("TR"),
            primaryCountryCode = "TR",
        )

        assertThat(signal.severity).isEqualTo(TravelAlertSeverity.CAUTION)
        assertThat(signal.summary).contains("Level 2.")
    }

    @Test
    fun `reliefweb provider builds documented request and surfaces appname approval failures`() = runTest {
        var capturedAppName: String? = null
        var capturedRequest: ReliefWebReportsRequest? = null
        val provider = ReliefWebSecurityProvider(
            service = object : ReliefWebReportsService {
                override suspend fun reports(
                    appName: String,
                    request: ReliefWebReportsRequest,
                ): Response<ResponseBody> {
                    capturedAppName = appName
                    capturedRequest = request
                    return Response.error(
                        403,
                        """
                        {"status":403,"error":{"message":"You are not using an approved appname. Kindly request an appname from ReliefWeb here: https://apidoc.reliefweb.int/parameters#appname"}}
                        """.trimIndent().toResponseBody(JsonMediaType),
                    )
                }
            },
            appConfig = TravelAlertProviderAppConfig(reliefWebAppName = "NomadDashboardTests"),
            countryNameResolver = CountryNameResolver(),
            json = json,
        )

        val error = runCatching {
            provider.security(
                countryCodes = listOf("ES", "FR"),
                primaryCountryCode = "ES",
            )
        }.exceptionOrNull()

        assertThat(capturedAppName).isEqualTo("NomadDashboardTests")
        assertThat(capturedRequest?.filter?.conditions?.single()?.field).isEqualTo("country.name")
        assertThat(capturedRequest?.filter?.conditions?.single()?.value).containsExactly("Spain", "France")
        assertThat(error).isInstanceOf(ReliefWebProviderError.AppNameApprovalRequired::class.java)
    }

    @Test
    fun `reliefweb signal maps recency and counts to caution levels`() {
        val now = java.time.Instant.parse("2026-04-07T12:00:00Z")
        val provider = ReliefWebSecurityProvider(
            service = object : ReliefWebReportsService {
                override suspend fun reports(
                    appName: String,
                    request: ReliefWebReportsRequest,
                ): Response<ResponseBody> = Response.success("{}".toResponseBody(JsonMediaType))
            },
            appConfig = TravelAlertProviderAppConfig(reliefWebAppName = "NomadDashboardTests"),
            countryNameResolver = CountryNameResolver(),
            json = json,
        )

        val signal = provider.signal(
            reports = listOf(
                SecurityReportPayload(
                    title = "Border protest update",
                    date = now.minusSeconds(6 * 3_600),
                    primaryCountryName = "France",
                    sourceName = "ReliefWeb",
                    urlAlias = "/report/france/border-protest",
                ),
                SecurityReportPayload(
                    title = "Transit disruption advisory",
                    date = now.minusSeconds(18 * 3_600),
                    primaryCountryName = "Portugal",
                    sourceName = "ReliefWeb",
                    urlAlias = "/report/portugal/transit",
                ),
            ),
            primaryCountryName = "Spain",
            matchedCountryNames = listOf("Spain", "France", "Portugal"),
            now = now,
        )

        assertThat(signal.severity).isEqualTo(TravelAlertSeverity.CAUTION)
        assertThat(signal.itemCount).isEqualTo(2)
        assertThat(signal.summary).isEqualTo("2 nearby security bulletins were published recently.")
        assertThat(signal.sourceUrl).isEqualTo("https://reliefweb.int/report/france/border-protest")
    }

    @Test
    fun `reliefweb parser surfaces invalid payloads`() {
        val provider = ReliefWebSecurityProvider(
            service = object : ReliefWebReportsService {
                override suspend fun reports(
                    appName: String,
                    request: ReliefWebReportsRequest,
                ): Response<ResponseBody> = Response.success("{}".toResponseBody(JsonMediaType))
            },
            appConfig = TravelAlertProviderAppConfig(reliefWebAppName = "NomadDashboardTests"),
            countryNameResolver = CountryNameResolver(),
            json = json,
        )

        val error = runCatching {
            provider.parseReports("""{"unexpected":[]}""")
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(ReliefWebProviderError.InvalidPayload::class.java)
        assertThat(error?.message).contains("Missing top-level data array")
    }

    private companion object {
        val JsonMediaType = "application/json".toMediaType()
    }
}
