package com.iloapps.nomaddashboard.core.travel.daylight

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunrise, sunset and civil twilight for one calendar day at one place.
 *
 * Computed entirely on the device with the NOAA solar calculator equations (Jean Meeus,
 * "Astronomical Algorithms"): no network. Accurate to about a minute outside the polar circles.
 *
 * [civilDawn] is the morning civil twilight start and [civilDusk] is the evening civil twilight end that
 * follows the day's sunset, so near the summer solstice at high latitudes it can fall after midnight on
 * the next calendar day.
 */
data class SolarDay(
    val sunrise: Instant?,
    val sunset: Instant?,
    val civilDawn: Instant?,
    val civilDusk: Instant?,
    /** The sun stays above the horizon all day (midnight sun). */
    val isPolarDay: Boolean,
    /** The sun stays below the horizon all day. */
    val isPolarNight: Boolean,
) {
    companion object {
        private val NoTimes = SolarDay(
            sunrise = null,
            sunset = null,
            civilDawn = null,
            civilDusk = null,
            isPolarDay = false,
            isPolarNight = false,
        )

        /**
         * The solar day for the calendar day that contains [instant] in [zone].
         *
         * @param latitude Degrees north, -90..90.
         * @param longitude Degrees east, -180..180.
         */
        fun compute(latitude: Double, longitude: Double, instant: Instant, zone: ZoneId): SolarDay =
            compute(latitude, longitude, instant.atZone(zone).toLocalDate(), zone)

        /** The solar day for the calendar [date] in [zone], the place's own time zone. */
        fun compute(latitude: Double, longitude: Double, date: LocalDate, zone: ZoneId): SolarDay {
            if (!latitude.isFinite() || !longitude.isFinite() || abs(latitude) > 90 || abs(longitude) > 180) {
                return NoTimes
            }

            val localNoon = date.atTime(12, 0).atZone(zone).toInstant().epochSecondsExact()
            val observer = Observer(latitude = latitude, longitude = longitude)
            // Solar noon nearest to local noon: the UTC day it falls in is the base for every event of this day.
            val noon = observer.solarNoon(near = localNoon)

            val horizon = observer.hourAngle(Observer.HorizonZenith, noon.parameters)
            val isPolarDay = horizon == HourAngle.AlwaysUp
            val isPolarNight = horizon == HourAngle.AlwaysDown
            val hasHorizonEvents = !isPolarDay && !isPolarNight

            val rise = observer.event(Observer.HorizonZenith, rising = true, base = noon)
            val set = observer.event(Observer.HorizonZenith, rising = false, base = noon)
            val dawn = observer.event(Observer.CivilZenith, rising = true, base = noon)
            val dusk = observer.event(Observer.CivilZenith, rising = false, base = noon)

            return SolarDay(
                sunrise = rise.takeIf { hasHorizonEvents }?.toInstant(),
                sunset = set.takeIf { hasHorizonEvents }?.toInstant(),
                civilDawn = dawn?.toInstant(),
                civilDusk = dusk?.toInstant(),
                isPolarDay = isPolarDay,
                isPolarNight = isPolarNight,
            )
        }
    }
}

// region NOAA solar position

private sealed interface HourAngle {
    data class Degrees(val value: Double) : HourAngle
    data object AlwaysUp : HourAngle
    data object AlwaysDown : HourAngle
}

private data class SolarParameters(
    /** Equation of time in minutes. */
    val equationOfTime: Double,
    /** Solar declination in degrees. */
    val declination: Double,
)

private class NoonBase(
    /** Unix seconds of the UTC midnight that starts the UTC day containing the solar noon. */
    val utcMidnight: Double,
    val parameters: SolarParameters,
)

private const val SecondsPerDay = 86_400.0

private class Observer(
    private val latitude: Double,
    private val longitude: Double,
) {
    /** The solar noon closest to the local noon [near] (Unix seconds). */
    fun solarNoon(near: Double): NoonBase {
        var instant = near
        var utcMidnight = floor(near / SecondsPerDay) * SecondsPerDay

        repeat(3) {
            val parameters = parametersAt(instant)
            var candidate = utcMidnight + (720 - 4 * longitude - parameters.equationOfTime) * 60
            // Keep the solar noon that is closest to the local noon anchor.
            while (candidate - near > 43_200) {
                utcMidnight -= SecondsPerDay
                candidate -= SecondsPerDay
            }
            while (near - candidate > 43_200) {
                utcMidnight += SecondsPerDay
                candidate += SecondsPerDay
            }
            instant = candidate
        }

        return NoonBase(utcMidnight = utcMidnight, parameters = parametersAt(instant))
    }

    /**
     * The rising or setting instant (Unix seconds) at [zenith], refined at the event time as the NOAA
     * calculator does. Null when the sun never crosses that zenith on this day.
     */
    fun event(zenith: Double, rising: Boolean, base: NoonBase): Double? {
        var parameters = base.parameters
        var instant: Double? = null

        repeat(3) {
            val angle = (hourAngle(zenith, parameters) as? HourAngle.Degrees)?.value ?: return instant
            val minutes = 720 - 4 * (longitude + if (rising) angle else -angle) - parameters.equationOfTime
            val refined = base.utcMidnight + minutes * 60
            instant = refined
            parameters = parametersAt(refined)
        }
        return instant
    }

    fun hourAngle(zenith: Double, parameters: SolarParameters): HourAngle {
        val latitudeRadians = radians(latitude)
        val declinationRadians = radians(parameters.declination)
        val cosine = cos(radians(zenith)) / (cos(latitudeRadians) * cos(declinationRadians)) -
            tan(latitudeRadians) * tan(declinationRadians)

        return when {
            cosine > 1 -> HourAngle.AlwaysDown
            cosine < -1 -> HourAngle.AlwaysUp
            else -> HourAngle.Degrees(degrees(acos(cosine)))
        }
    }

    private fun parametersAt(epochSeconds: Double): SolarParameters {
        val julianDay = epochSeconds / SecondsPerDay + 2_440_587.5
        val t = (julianDay - 2_451_545) / 36_525

        val meanLongitude = (280.46646 + t * (36_000.76983 + t * 0.0003032)) % 360
        val meanAnomaly = 357.52911 + t * (35_999.05029 - 0.0001537 * t)
        val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)

        val anomalyRadians = radians(meanAnomaly)
        val centre = sin(anomalyRadians) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * anomalyRadians) * (0.019993 - 0.000101 * t) +
            sin(3 * anomalyRadians) * 0.000289

        val trueLongitude = meanLongitude + centre
        val omega = 125.04 - 1_934.136 * t
        val apparentLongitude = trueLongitude - 0.00569 - 0.00478 * sin(radians(omega))

        val meanObliquity = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60
        val obliquity = meanObliquity + 0.00256 * cos(radians(omega))

        val declination = degrees(asin(sin(radians(obliquity)) * sin(radians(apparentLongitude))))

        val y = tan(radians(obliquity) / 2).pow(2)
        val longitudeRadians = radians(meanLongitude)
        val equationOfTime = 4 * degrees(
            y * sin(2 * longitudeRadians) -
                2 * eccentricity * sin(anomalyRadians) +
                4 * eccentricity * y * sin(anomalyRadians) * cos(2 * longitudeRadians) -
                0.5 * y * y * sin(4 * longitudeRadians) -
                1.25 * eccentricity * eccentricity * sin(2 * anomalyRadians),
        )

        return SolarParameters(equationOfTime = equationOfTime, declination = declination)
    }

    companion object {
        /** 90.833 degrees: the sun's centre at the horizon including refraction and the solar radius. */
        const val HorizonZenith = 90.833

        /** 96 degrees: the sun's centre 6 degrees below the horizon. */
        const val CivilZenith = 96.0
    }
}

private fun radians(degrees: Double): Double = degrees * Math.PI / 180

private fun degrees(radians: Double): Double = radians * 180 / Math.PI

private fun Instant.epochSecondsExact(): Double = epochSecond + nano / 1_000_000_000.0

private fun Double.toInstant(): Instant {
    val seconds = floor(this)
    return Instant.ofEpochSecond(seconds.toLong(), ((this - seconds) * 1_000_000_000).toLong())
}

// endregion
