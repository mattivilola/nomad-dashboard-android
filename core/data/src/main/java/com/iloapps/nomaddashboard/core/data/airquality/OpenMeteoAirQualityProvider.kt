package com.iloapps.nomaddashboard.core.data.airquality

import com.iloapps.nomaddashboard.core.data.fuel.ExpiringRequestCache
import com.iloapps.nomaddashboard.core.data.openmeteo.OpenMeteoTimestamps
import com.iloapps.nomaddashboard.core.model.AirQualityForecastSlot
import com.iloapps.nomaddashboard.core.model.AirQualityPollutant
import com.iloapps.nomaddashboard.core.model.AirQualitySnapshot
import com.iloapps.nomaddashboard.core.network.api.OpenMeteoAirQualityService
import com.iloapps.nomaddashboard.core.network.model.OpenMeteoAirQualityResponse
import com.iloapps.nomaddashboard.core.travel.environment.AirQualityGridPoint
import com.iloapps.nomaddashboard.core.travel.environment.dominantAirQualityPollutant
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Keyless Open-Meteo Air Quality API (CAMS Europe 0.1° ≈ 11 km, CAMS global 0.4° elsewhere).
 *
 * Privacy: the coordinate is rounded to one decimal (~11 km) before the request is made. The rounded
 * grid point is also the cache key and is the only location stored in the snapshot.
 */
@Singleton
class OpenMeteoAirQualityProvider @Inject constructor(
    private val service: OpenMeteoAirQualityService,
    private val clock: Clock,
) : AirQualityProvider {
    private val cache = ExpiringRequestCache<String, AirQualitySnapshot>(maxEntries = MaxCacheEntries)

    override suspend fun airQuality(latitude: Double, longitude: Double): AirQualitySnapshot {
        val grid = AirQualityGridPoint.privacyRounded(latitude, longitude)
            ?: throw AirQualityProviderException("Invalid coordinate for air quality")
        return cache.getOrLoad(
            key = "${grid.latitudeText},${grid.longitudeText}",
            ttlMillis = CacheTtl.toMillis(),
            nowMillis = clock.millis(),
        ) {
            val response = service.airQuality(
                latitude = grid.latitudeText,
                longitude = grid.longitudeText,
            )
            buildSnapshot(response, grid, clock.instant())
        }
    }

    internal companion object {
        const val SourceName = "Open-Meteo"
        const val ForecastHours = OpenMeteoAirQualityService.FORECAST_HOURS
        private const val MaxCacheEntries = 4
        private val CacheTtl: Duration = Duration.ofMinutes(30)

        fun buildSnapshot(
            response: OpenMeteoAirQualityResponse,
            grid: AirQualityGridPoint,
            now: Instant,
        ): AirQualitySnapshot {
            // Outside the model's coverage Open-Meteo answers with nulls; there is nothing useful to show then.
            val current = response.current
            val currentAqi = current?.europeanAqi?.let(::wholeIndex)
                ?: throw AirQualityProviderException("Air quality response has no current index")

            val hourly = response.hourly
            val times = hourly?.let {
                OpenMeteoTimestamps.parseAll(it.times, response.timezone, response.utcOffsetSeconds)
            } ?: emptyList()
            if (hourly != null && times.size != hourly.europeanAqi.size) {
                throw AirQualityProviderException("Air quality hourly series is misaligned")
            }

            val slots = hourly?.let { series ->
                times.zip(series.europeanAqi)
                    .filter { (time, _) -> time.isAfter(now) }
                    .sortedBy { (time, _) -> time }
                    .take(ForecastHours)
                    .map { (time, aqi) -> AirQualityForecastSlot(time, aqi?.let(::wholeIndex)) }
            }.orEmpty()

            val subIndices = buildMap<AirQualityPollutant, Double> {
                current.europeanAqiPm2_5?.let { put(AirQualityPollutant.PM2_5, it) }
                current.europeanAqiPm10?.let { put(AirQualityPollutant.PM10, it) }
                current.europeanAqiOzone?.let { put(AirQualityPollutant.OZONE, it) }
                current.europeanAqiNitrogenDioxide?.let { put(AirQualityPollutant.NITROGEN_DIOXIDE, it) }
                current.europeanAqiSulphurDioxide?.let { put(AirQualityPollutant.SULPHUR_DIOXIDE, it) }
            }

            return AirQualitySnapshot(
                latitude = grid.latitude,
                longitude = grid.longitude,
                sourceName = SourceName,
                europeanAqi = currentAqi,
                mainPollutant = dominantAirQualityPollutant(subIndices),
                pm2_5 = current.pm2_5,
                pm10 = current.pm10,
                ozone = current.ozone,
                nitrogenDioxide = current.nitrogenDioxide,
                forecastSlots = slots,
                fetchedAt = now,
            )
        }

        private fun wholeIndex(value: Double): Int? =
            value.takeIf { it.isFinite() && it >= 0 }?.roundToInt()
    }
}
