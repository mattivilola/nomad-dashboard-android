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
                override suspend fun destinations(): Response<ResponseBody> =
                    Response.success(
                        """
                        <html>
                          <body>
                            <table>
                              <tr>
                                <th>Destination</th>
                                <th>Region</th>
                                <th>Overall Advice Level</th>
                                <th>Updated</th>
                              </tr>
                              <tr>
                                <td><a href="/destinations/spain">Spain</a></td>
                                <td>Europe</td>
                                <td>Exercise normal safety precautions</td>
                                <td>07 Apr 2026</td>
                              </tr>
                              <tr>
                                <td><a href="/destinations/france">France</a></td>
                                <td>Europe</td>
                                <td>Reconsider your need to travel</td>
                                <td>07 Apr 2026</td>
                              </tr>
                            </table>
                          </body>
                        </html>
                        """.trimIndent().toResponseBody(HtmlMediaType),
                    )

                override suspend fun destinationsExport(): Response<ResponseBody> =
                    Response.error(404, "{}".toResponseBody(JsonMediaType))

                override suspend fun destinationPage(url: String): Response<ResponseBody> =
                    Response.success(
                        """
                        <html>
                          <body>
                            <p>Reconsider your need to travel to France due to the threat of terrorism.</p>
                          </body>
                        </html>
                        """.trimIndent().toResponseBody(HtmlMediaType),
                    )
            },
            countryNameResolver = CountryNameResolver(),
            json = json,
            browserFetcher = object : SmartravellerBrowserFetcher {
                override suspend fun destinationsHtml(): String = error("browser fallback should not be used")
            },
        )

        val signal = provider.advisory(
            countryCodes = listOf("ES", "FR"),
            primaryCountryCode = "ES",
        )

        assertThat(signal.severity).isEqualTo(TravelAlertSeverity.WARNING)
        assertThat(signal.summary).isEqualTo("France nearby: reconsider your need to travel.")
        assertThat(signal.detailSummary).isEqualTo("Reconsider your need to travel to France due to the threat of terrorism.")
        assertThat(signal.sourceUrl).isEqualTo("https://www.smartraveller.gov.au/destinations/france")
        assertThat(signal.affectedCountryCodes).containsExactly("FR")
    }

    @Test
    fun `smartraveller provider resolves alias country names`() = runTest {
        val provider = SmartravellerAdvisoryProvider(
            service = object : SmartravellerService {
                override suspend fun destinations(): Response<ResponseBody> =
                    Response.success(
                        """
                        [
                          {
                            "country": "Turkiye",
                            "level": 2,
                            "url": "https://example.com/turkiye",
                            "last_updated": "2026-04-07T09:00:00Z"
                          }
                        ]
                        """.trimIndent().toResponseBody(JsonMediaType),
                    )

                override suspend fun destinationsExport(): Response<ResponseBody> =
                    Response.error(404, "{}".toResponseBody(JsonMediaType))

                override suspend fun destinationPage(url: String): Response<ResponseBody> =
                    Response.success(
                        """
                        <html>
                          <body>
                            <p>Exercise a high degree of caution in Turkiye due to the risk of civil unrest.</p>
                          </body>
                        </html>
                        """.trimIndent().toResponseBody(HtmlMediaType),
                    )
            },
            countryNameResolver = CountryNameResolver(),
            json = json,
            browserFetcher = object : SmartravellerBrowserFetcher {
                override suspend fun destinationsHtml(): String = error("browser fallback should not be used")
            },
        )

        val signal = provider.advisory(
            countryCodes = listOf("TR"),
            primaryCountryCode = "TR",
        )

        assertThat(signal.severity).isEqualTo(TravelAlertSeverity.CAUTION)
        assertThat(signal.summary).isEqualTo("Türkiye: exercise a high degree of caution.")
        assertThat(signal.detailSummary).isEqualTo("Exercise a high degree of caution in Turkiye due to the risk of civil unrest.")
    }

    @Test
    fun `smartraveller provider falls back to browser fetch when both direct endpoints fail`() = runTest {
        val provider = SmartravellerAdvisoryProvider(
            service = object : SmartravellerService {
                override suspend fun destinations(): Response<ResponseBody> = Response.error(
                    504,
                    "{}".toResponseBody(JsonMediaType),
                )

                override suspend fun destinationsExport(): Response<ResponseBody> = Response.error(
                    504,
                    "{}".toResponseBody(JsonMediaType),
                )

                override suspend fun destinationPage(url: String): Response<ResponseBody> =
                    Response.error(504, "{}".toResponseBody(JsonMediaType))
            },
            countryNameResolver = CountryNameResolver(),
            json = json,
            browserFetcher = object : SmartravellerBrowserFetcher {
                override suspend fun destinationsHtml(): String =
                    """
                    <html>
                      <body>
                        <table>
                          <tr>
                            <th>Destination</th>
                            <th>Region</th>
                            <th>Overall Advice Level</th>
                            <th>Updated</th>
                          </tr>
                          <tr>
                            <td><a href="/destinations/france">France</a></td>
                            <td>Europe</td>
                            <td>Exercise a high degree of caution</td>
                            <td>07 Apr 2026</td>
                          </tr>
                        </table>
                      </body>
                    </html>
                    """.trimIndent()
            },
        )

        val signal = provider.advisory(
            countryCodes = listOf("FR"),
            primaryCountryCode = "FR",
        )

        assertThat(signal.severity).isEqualTo(TravelAlertSeverity.CAUTION)
        assertThat(signal.summary).contains("exercise a high degree of caution")
    }

    @Test
    fun `smartraveller detail parser extracts concise advice sentence`() {
        val provider = SmartravellerAdvisoryProvider(
            service = object : SmartravellerService {
                override suspend fun destinations(): Response<ResponseBody> = Response.error(
                    404,
                    "{}".toResponseBody(JsonMediaType),
                )

                override suspend fun destinationsExport(): Response<ResponseBody> = Response.error(
                    404,
                    "{}".toResponseBody(JsonMediaType),
                )

                override suspend fun destinationPage(url: String): Response<ResponseBody> = Response.error(
                    404,
                    "{}".toResponseBody(JsonMediaType),
                )
            },
            countryNameResolver = CountryNameResolver(),
            json = json,
            browserFetcher = object : SmartravellerBrowserFetcher {
                override suspend fun destinationsHtml(): String = error("browser fallback should not be used")
            },
        )

        val detail = provider.parseDestinationDetailSummary(
            """
            <html>
              <body>
                <h3>Advice Level summary</h3>
                <p>We advise:</p>
                <p>Exercise a high degree of caution in France due to the threat of terrorism.</p>
              </body>
            </html>
            """.trimIndent(),
        )

        assertThat(detail).isEqualTo("Exercise a high degree of caution in France due to the threat of terrorism.")
    }

    @Test
    fun `smartraveller detail parser decodes numeric apostrophe entities`() {
        val provider = SmartravellerAdvisoryProvider(
            service = object : SmartravellerService {
                override suspend fun destinations(): Response<ResponseBody> = Response.error(
                    404,
                    "{}".toResponseBody(JsonMediaType),
                )

                override suspend fun destinationsExport(): Response<ResponseBody> = Response.error(
                    404,
                    "{}".toResponseBody(JsonMediaType),
                )

                override suspend fun destinationPage(url: String): Response<ResponseBody> = Response.error(
                    404,
                    "{}".toResponseBody(JsonMediaType),
                )
            },
            countryNameResolver = CountryNameResolver(),
            json = json,
            browserFetcher = object : SmartravellerBrowserFetcher {
                override suspend fun destinationsHtml(): String = error("browser fallback should not be used")
            },
        )

        val detail = provider.parseDestinationDetailSummary(
            """
            <html>
              <body>
                <p>Exercise a high degree of caution because you&#039;re travelling during a disruption.</p>
              </body>
            </html>
            """.trimIndent(),
        )

        assertThat(detail).isEqualTo("Exercise a high degree of caution because you're travelling during a disruption.")
    }

    @Test
    fun `bundled neighbor country resolver includes France plus 8 bordering countries`() {
        val resolver = BundledNeighborCountryResolver.fromRecords(
            mapOf("FR" to listOf("AD", "BE", "CH", "DE", "ES", "IT", "LU", "MC")),
        )

        assertThat(resolver.neighboringCountryCodes("fr"))
            .containsExactly("AD", "BE", "CH", "DE", "ES", "IT", "LU", "MC")
            .inOrder()
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
            countryNameResolver = CountryNameResolver(),
            json = json,
        )

        val error = runCatching {
            provider.security(
                appName = "NomadDashboardTests",
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
        val HtmlMediaType = "text/html".toMediaType()
    }
}
