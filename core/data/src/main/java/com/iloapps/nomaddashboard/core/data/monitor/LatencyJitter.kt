package com.iloapps.nomaddashboard.core.data.monitor

import com.iloapps.nomaddashboard.core.model.MetricHistoryPoint
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * Jitter as the mean absolute difference between successive latency readings, so it needs at least two
 * readings. With exactly two it is the same absolute difference macOS reports.
 */
internal fun latencyJitter(samples: List<Double>): Double? {
    if (samples.size < 2) return null
    return samples.zipWithNext { previous, next -> abs(next - previous) }.average()
}

/**
 * Jitter from the most recent [maximumSamples] readings that are no more than [window] older than the
 * newest one, so readings from an earlier session or network do not leak into the value.
 */
internal fun latencyJitter(
    history: List<MetricHistoryPoint>,
    newest: Instant,
    maximumSamples: Int = JitterSampleCount,
    window: Duration = JitterWindow,
): Double? =
    latencyJitter(
        history
            .filter { !it.recordedAt.isAfter(newest) && Duration.between(it.recordedAt, newest) <= window }
            .sortedBy { it.recordedAt }
            .takeLast(maximumSamples)
            .map { it.value },
    )

internal const val JitterSampleCount = 5
internal val JitterWindow: Duration = Duration.ofMinutes(5)
