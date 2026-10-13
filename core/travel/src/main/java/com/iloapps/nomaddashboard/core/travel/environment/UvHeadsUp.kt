package com.iloapps.nomaddashboard.core.travel.environment

import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpDecision
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpSeverity
import java.time.Duration
import java.time.Instant

/**
 * The part of the day in which UV readings matter: [start] inclusive, [endExclusive] exclusive (usually
 * the sunset, or the end of the calendar day when no sunset can be computed).
 */
data class RemainingDaylight(
    val start: Instant,
    val endExclusive: Instant,
) {
    operator fun contains(time: Instant): Boolean = time >= start && time < endExclusive
}

/** One hourly UV forecast reading. */
data class HourlyUvReading(
    val time: Instant,
    val uvIndex: Int?,
)

/**
 * UV index heads-up. WHO categories: 6-7 high, 8-10 very high, 11+ extreme.
 * https://www.who.int/news-room/questions-and-answers/item/radiation-the-ultraviolet-(uv)-index
 */
object UvHeadsUp {
    const val HIGH_THRESHOLD = 6
    const val VERY_HIGH_THRESHOLD = 8
    const val EXTREME_THRESHOLD = 11

    /** A current reading older than this is not shown as "now". */
    val CURRENT_READING_MAX_AGE: Duration = Duration.ofHours(2)

    private data class Reading(val time: Instant?, val uv: Int)

    /**
     * Warns about the strongest UV left in today's daylight. The hourly forecast only has a few slots, so
     * the time range is the run of consecutive readings (the current one first) that reach the same WHO
     * category.
     *
     * @param currentUvIndex the weather provider's current UV, counted as "now" when [currentFetchedAt] is fresh.
     * @param daylight the remaining daylight; readings outside it are ignored.
     * @param clock formats an instant as a clock time in the place and locale shown to the traveller.
     */
    fun evaluate(
        currentUvIndex: Int?,
        currentFetchedAt: Instant,
        hourly: List<HourlyUvReading>,
        now: Instant,
        daylight: RemainingDaylight,
        clock: (Instant) -> String,
    ): HeadsUpDecision? {
        val readings = buildList {
            val isFresh = Duration.between(currentFetchedAt, now) <= CURRENT_READING_MAX_AGE
            if (isFresh && now in daylight && currentUvIndex != null) {
                add(Reading(null, currentUvIndex))
            }
            for (slot in hourly.sortedBy { it.time }) {
                if (slot.time >= now && slot.time in daylight && slot.uvIndex != null) {
                    add(Reading(slot.time, slot.uvIndex))
                }
            }
        }

        val peak = readings.maxOfOrNull { it.uv }?.takeIf { it >= HIGH_THRESHOLD } ?: return null
        val peakIndex = readings.indexOfFirst { it.uv == peak }

        val severity: HeadsUpSeverity
        val floor: Int
        val label: String
        val detail: String
        when {
            peak >= EXTREME_THRESHOLD -> {
                severity = HeadsUpSeverity.ATTENTION
                floor = EXTREME_THRESHOLD
                label = "extreme"
                detail = "Avoid midday sun; cover up and use SPF 30+"
            }

            peak >= VERY_HIGH_THRESHOLD -> {
                severity = HeadsUpSeverity.CAUTION
                floor = VERY_HIGH_THRESHOLD
                label = "very high"
                detail = "Seek shade at midday; SPF 30+"
            }

            else -> {
                severity = HeadsUpSeverity.INFO
                floor = HIGH_THRESHOLD
                label = "high"
                detail = "Hat and sunscreen around midday"
            }
        }

        // The run of consecutive readings at or above the category's floor around the peak.
        var first = peakIndex
        while (first > 0 && readings[first - 1].uv >= floor) {
            first -= 1
        }
        var last = peakIndex
        while (last < readings.lastIndex && readings[last + 1].uv >= floor) {
            last += 1
        }

        fun clockText(time: Instant?): String = time?.let(clock) ?: "now"
        val start = clockText(readings[first].time)
        val end = clockText(readings[last].time)

        // Caution reads "from 12:00"; the others give the span, or one moment when there is no span.
        val whenText = when {
            severity == HeadsUpSeverity.CAUTION -> "from $start"
            first == last -> if (readings[first].time == null) "now" else "around $start"
            else -> "$start–$end"
        }

        return HeadsUpDecision(
            severity = severity,
            title = "UV $label ($peak) $whenText",
            detail = detail,
            statusText = "UV $peak",
        )
    }
}
