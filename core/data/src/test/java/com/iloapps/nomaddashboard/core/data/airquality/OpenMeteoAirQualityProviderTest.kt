package com.iloapps.nomaddashboard.core.data.airquality

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.AirQualityPollutant
import com.iloapps.nomaddashboard.core.network.api.OpenMeteoAirQualityService
import com.iloapps.nomaddashboard.core.network.model.OpenMeteoAirQualityCurrent
import com.iloapps.nomaddashboard.core.network.model.OpenMeteoAirQualityHourly
import com.iloapps.nomaddashboard.core.network.model.OpenMeteoAirQualityResponse
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class OpenMeteoAirQualityProviderTest {
    // 2026-10-12T10:20:00Z: mid-hour, so the current hour's slot is already in the past.
    private val now = Instant.parse("2026-10-12T10:20:00Z")
    private val currentHour = Instant.parse("2026-10-12T10:00:00Z")

    @Test
    fun `decodes current and hourly air quality into a snapshot`() = runTest {
        // The hourly series starts at the current hour, which is not part of the 12 hour outlook.
        val times = (0..12).map { currentHour.plus(Duration.ofHours(it.toLong())) }
        val aqi = listOf(41.2, 44.0, 48.0, 52.0, 58.0, 61.0, 66.0, 70.0, 72.0, null, 64.0, 59.0, 55.0)
        val service = FakeAirQualityService(
            response(
                times = times,
                hourlyAqi = aqi,
                current = OpenMeteoAirQualityCurrent(
                    europeanAqi = 43.6, pm2_5 = 14.2, pm10 = 31.5, ozone = 88.0, nitrogenDioxide = 12.4,
                    europeanAqiPm2_5 = 43.6, europeanAqiPm10 = 33.0, europeanAqiOzone = 20.0,
                    europeanAqiNitrogenDioxide = 5.0, europeanAqiSulphurDioxide = 1.0,
                ),
            ),
        )

        val snapshot = provider(service).airQuality(60.1699, 24.9384)

        assertThat(snapshot.sourceName).isEqualTo("Open-Meteo")
        assertThat(snapshot.latitude).isEqualTo(60.2)
        assertThat(snapshot.longitude).isEqualTo(24.9)
        assertThat(snapshot.europeanAqi).isEqualTo(44)
        assertThat(snapshot.mainPollutant).isEqualTo(AirQualityPollutant.PM2_5)
        assertThat(snapshot.pm2_5).isEqualTo(14.2)
        assertThat(snapshot.pm10).isEqualTo(31.5)
        assertThat(snapshot.ozone).isEqualTo(88.0)
        assertThat(snapshot.nitrogenDioxide).isEqualTo(12.4)
        assertThat(snapshot.fetchedAt).isEqualTo(now)
        assertThat(snapshot.forecastSlots).hasSize(12)
        assertThat(snapshot.forecastSlots.all { it.time.isAfter(now) }).isTrue()
        assertThat(snapshot.forecastSlots.map { it.time }).isInOrder()
        assertThat(snapshot.forecastSlots.first().europeanAqi).isEqualTo(44)
        assertThat(snapshot.forecastSlots.any { it.europeanAqi == null }).isTrue()
    }

    @Test
    fun `dominant pollutant follows the highest sub index`() = runTest {
        val service = FakeAirQualityService(
            response(
                times = listOf(currentHour),
                hourlyAqi = listOf(30.0),
                current = OpenMeteoAirQualityCurrent(
                    europeanAqi = 65.0, europeanAqiPm2_5 = 40.0, europeanAqiPm10 = 30.0,
                    europeanAqiOzone = 65.0, europeanAqiNitrogenDioxide = 10.0,
                ),
            ),
        )

        assertThat(provider(service).airQuality(41.0, 2.0).mainPollutant).isEqualTo(AirQualityPollutant.OZONE)
    }

    @Test
    fun `request rounds the coordinate and asks for the documented variables`() = runTest {
        val service = FakeAirQualityService(simpleResponse())

        provider(service).airQuality(60.169857, 24.938379)

        val request = service.requests.single()
        assertThat(request.latitude).isEqualTo("60.2")
        assertThat(request.longitude).isEqualTo("24.9")
        assertThat(request.hourly).isEqualTo("european_aqi")
        assertThat(request.forecastHours).isEqualTo(13)
        assertThat(request.timezone).isEqualTo("auto")
        assertThat(request.current.split(",")).containsAtLeast(
            "european_aqi", "pm2_5", "pm10", "ozone", "nitrogen_dioxide",
            "european_aqi_pm2_5", "european_aqi_pm10", "european_aqi_ozone",
            "european_aqi_nitrogen_dioxide", "european_aqi_sulphur_dioxide",
        )
        // The exact coordinate must not appear anywhere in the request.
        assertThat(request.toString()).doesNotContain("60.169")
        assertThat(request.toString()).doesNotContain("24.938")
    }

    @Test
    fun `negative zero and southern western coordinates round cleanly`() = runTest {
        val service = FakeAirQualityService(simpleResponse())

        provider(service).airQuality(-0.04, -33.86)

        assertThat(service.requests.single().latitude).isEqualTo("0.0")
        assertThat(service.requests.single().longitude).isEqualTo("-33.9")
    }

    @Test
    fun `nearby coordinates in the same grid cell share the cached snapshot`() = runTest {
        val service = FakeAirQualityService(simpleResponse())
        val provider = provider(service)

        val first = provider.airQuality(60.161, 24.941)
        val second = provider.airQuality(60.174, 24.935)
        provider.airQuality(60.4, 24.9)

        assertThat(first).isEqualTo(second)
        assertThat(service.requests).hasSize(2)
    }

    @Test
    fun `expired cache refetches`() = runTest {
        val service = FakeAirQualityService(simpleResponse())
        val clock = MutableClock(now)
        val provider = provider(service, clock)

        provider.airQuality(60.2, 24.9)
        clock.advance(Duration.ofMinutes(29))
        provider.airQuality(60.2, 24.9)
        assertThat(service.requests).hasSize(1)

        clock.advance(Duration.ofMinutes(2))
        val refreshed = provider.airQuality(60.2, 24.9)

        assertThat(service.requests).hasSize(2)
        assertThat(refreshed.fetchedAt).isEqualTo(now.plus(Duration.ofMinutes(31)))
    }

    @Test
    fun `missing current index is an invalid response`() = runTest {
        val service = FakeAirQualityService(
            response(
                times = listOf(currentHour),
                hourlyAqi = listOf(null),
                current = OpenMeteoAirQualityCurrent(europeanAqi = null, pm2_5 = null),
            ),
        )

        val error = runCatching { provider(service).airQuality(0.1, 0.1) }.exceptionOrNull()

        assertThat(error).isInstanceOf(AirQualityProviderException::class.java)
    }

    @Test
    fun `misaligned hourly series is an invalid response`() = runTest {
        val service = FakeAirQualityService(
            response(times = listOf(currentHour, currentHour.plusSeconds(3_600)), hourlyAqi = listOf(30.0)),
        )

        val error = runCatching { provider(service).airQuality(60.2, 24.9) }.exceptionOrNull()

        assertThat(error).isInstanceOf(AirQualityProviderException::class.java)
    }

    @Test
    fun `transport failures propagate and are not cached`() = runTest {
        val service = FakeAirQualityService(simpleResponse(), failures = 1)
        val provider = provider(service)

        val error = runCatching { provider.airQuality(60.2, 24.9) }.exceptionOrNull()
        assertThat(error).isInstanceOf(IOException::class.java)
        val recovered = provider.airQuality(60.2, 24.9)

        assertThat(recovered.europeanAqi).isEqualTo(30)
        assertThat(service.requests).hasSize(2)
    }

    @Test
    fun `invalid coordinates never reach the network`() = runTest {
        val service = FakeAirQualityService(simpleResponse())
        val provider = provider(service)

        listOf(91.0 to 0.0, 0.0 to 181.0, Double.NaN to 0.0, 0.0 to Double.POSITIVE_INFINITY).forEach { (lat, lon) ->
            val error = runCatching { provider.airQuality(lat, lon) }.exceptionOrNull()
            assertThat(error).isInstanceOf(AirQualityProviderException::class.java)
        }
        assertThat(service.requests).isEmpty()
    }

    @Test
    fun `timestamps are interpreted in the locations time zone`() = runTest {
        val zone = ZoneId.of("Asia/Kolkata")
        val localHour = now.atZone(zone).withMinute(0).withSecond(0).withNano(0)
        val service = FakeAirQualityService(
            response(
                times = listOf(localHour.toInstant(), localHour.toInstant().plusSeconds(3_600), localHour.toInstant().plusSeconds(7_200)),
                hourlyAqi = listOf(90.0, 95.0, 99.0),
                current = OpenMeteoAirQualityCurrent(europeanAqi = 90.0),
                zone = zone,
            ),
        )

        val snapshot = provider(service).airQuality(28.6, 77.2)

        assertThat(snapshot.forecastSlots).hasSize(2)
        assertThat(snapshot.forecastSlots.first().time).isEqualTo(localHour.toInstant().plusSeconds(3_600))
        assertThat(snapshot.forecastSlots.first().europeanAqi).isEqualTo(95)
    }

    @Test
    fun `utc offset is used when the time zone name is unknown`() = runTest {
        val service = FakeAirQualityService(
            OpenMeteoAirQualityResponse(
                timezone = "Not/AZone",
                utcOffsetSeconds = 3 * 3_600,
                current = OpenMeteoAirQualityCurrent(europeanAqi = 20.0),
                hourly = OpenMeteoAirQualityHourly(times = listOf("2026-10-12T14:00"), europeanAqi = listOf(77.0)),
            ),
        )

        val snapshot = provider(service).airQuality(60.2, 24.9)

        // 14:00 at UTC+3 is 11:00Z, which is after the 10:20Z clock.
        assertThat(snapshot.forecastSlots.single().time).isEqualTo(Instant.parse("2026-10-12T11:00:00Z"))
    }

    @Test
    fun `response model decodes a real payload with nulls and unknown keys`() {
        val payload = """
            {"latitude":60.2,"longitude":24.9,"generationtime_ms":0.2,"utc_offset_seconds":10800,
             "timezone":"Europe/Helsinki","timezone_abbreviation":"EEST","elevation":15.0,
             "current_units":{"time":"iso8601","european_aqi":"european_aqi"},
             "current":{"time":"2026-10-12T13:00","interval":3600,"european_aqi":31,"pm2_5":6.1,"pm10":9.4,
               "ozone":58.0,"nitrogen_dioxide":null,"european_aqi_pm2_5":31,"european_aqi_pm10":17,
               "european_aqi_ozone":22,"european_aqi_nitrogen_dioxide":null,"european_aqi_sulphur_dioxide":1},
             "hourly_units":{"time":"iso8601","european_aqi":"european_aqi"},
             "hourly":{"time":["2026-10-12T13:00","2026-10-12T14:00"],"european_aqi":[31,null]}}
        """.trimIndent()

        val response = Json { ignoreUnknownKeys = true; explicitNulls = false }
            .decodeFromString<OpenMeteoAirQualityResponse>(payload)

        assertThat(response.current?.europeanAqi).isEqualTo(31.0)
        assertThat(response.current?.nitrogenDioxide).isNull()
        assertThat(response.current?.europeanAqiSulphurDioxide).isEqualTo(1.0)
        assertThat(response.hourly?.europeanAqi).containsExactly(31.0, null).inOrder()
        assertThat(response.utcOffsetSeconds).isEqualTo(10_800)
    }

    private fun provider(service: OpenMeteoAirQualityService, clock: Clock = Clock.fixed(now, ZoneOffset.UTC)) =
        OpenMeteoAirQualityProvider(service = service, clock = clock)

    private fun simpleResponse() =
        response(times = listOf(currentHour), hourlyAqi = listOf(30.0), current = OpenMeteoAirQualityCurrent(europeanAqi = 30.0))

    private fun response(
        times: List<Instant>,
        hourlyAqi: List<Double?>,
        current: OpenMeteoAirQualityCurrent = OpenMeteoAirQualityCurrent(europeanAqi = 30.0),
        zone: ZoneId = ZoneOffset.UTC,
    ) = OpenMeteoAirQualityResponse(
        timezone = zone.id,
        utcOffsetSeconds = zone.rules.getOffset(now).totalSeconds,
        current = current,
        hourly = OpenMeteoAirQualityHourly(
            times = times.map { LocalTimeFormat.format(it.atZone(zone)) },
            europeanAqi = hourlyAqi,
        ),
    )
}

private val LocalTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

private data class AirQualityRequest(
    val latitude: String,
    val longitude: String,
    val current: String,
    val hourly: String,
    val forecastHours: Int,
    val timezone: String,
)

private class FakeAirQualityService(
    private val response: OpenMeteoAirQualityResponse,
    private var failures: Int = 0,
) : OpenMeteoAirQualityService {
    val requests = mutableListOf<AirQualityRequest>()

    override suspend fun airQuality(
        latitude: String,
        longitude: String,
        current: String,
        hourly: String,
        forecastHours: Int,
        timezone: String,
    ): OpenMeteoAirQualityResponse {
        requests += AirQualityRequest(latitude, longitude, current, hourly, forecastHours, timezone)
        if (failures > 0) {
            failures -= 1
            throw IOException("offline")
        }
        return response
    }
}

private class MutableClock(private var instant: Instant) : Clock() {
    fun advance(duration: Duration) {
        instant = instant.plus(duration)
    }

    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = instant
}
