package com.iloapps.nomaddashboard.core.travel.cards

import com.iloapps.nomaddashboard.core.model.MetricHistoryPoint

/**
 * The samples behind one sparkline and the span they cover.
 *
 * @property values Samples, oldest first.
 * @property spanLabel What the long-press hint says the line covers, for example "Last 30 min".
 */
data class MetricTrend(
    val values: List<Double>,
    val spanLabel: String,
) {
    companion object {
        /** The trend of [history], or null when it has fewer than two samples (nothing to draw). */
        fun of(history: List<MetricHistoryPoint>): MetricTrend? {
            val sorted = history.sortedBy(MetricHistoryPoint::recordedAt)
            if (sorted.size < 2) return null
            return MetricTrend(
                values = sorted.map(MetricHistoryPoint::value),
                spanLabel = SparklineSpan.label(sorted.first().recordedAt.epochSecond, sorted.last().recordedAt.epochSecond),
            )
        }
    }
}

/** The span text of a sparkline, ported from the macOS `SparklinePresentation`. */
object SparklineSpan {
    private const val SECONDS_PER_MINUTE = 60.0
    private const val SECONDS_PER_HOUR = 3_600.0

    /** "45 s", "8 min", "2 h": the span in the coarsest readable unit. */
    fun text(seconds: Long): String {
        val clamped = seconds.coerceAtLeast(0)
        if (clamped < 90) return "${clamped.coerceAtLeast(1)} s"

        val minutes = Math.round(clamped / SECONDS_PER_MINUTE).toInt()
        if (minutes < 90) return "$minutes min"

        val hours = Math.round(clamped / SECONDS_PER_HOUR * 10) / 10.0
        return if (hours == Math.rint(hours)) "${hours.toInt()} h" else String.format(java.util.Locale.ROOT, "%.1f h", hours)
    }

    /** "Last 30 min". */
    fun label(firstEpochSecond: Long, lastEpochSecond: Long): String = "Last ${text(lastEpochSecond - firstEpochSecond)}"
}
