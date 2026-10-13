package com.iloapps.nomaddashboard.core.travel.cards

import java.time.Duration

/** "42m", "3h", "6h 58m": whole minutes, no zero-padded parts. */
object TimeTrackingDurationFormatting {
    fun text(duration: Duration): String {
        val totalMinutes = Math.round(duration.coerceAtLeastZero().seconds / 60.0).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0 -> "${minutes}m"
            minutes == 0 -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }
}

internal fun Duration.coerceAtLeastZero(): Duration = if (isNegative) Duration.ZERO else this

/** Time already assigned to one project (or Other) today. */
data class AllocatedBucket(
    val id: String,
    val title: String,
    val duration: Duration,
    val interruptionCount: Int = 0,
    /** The duration without the estimated focus loss of its interruptions. */
    val focusAdjustedDuration: Duration = duration,
)

/**
 * Today's time split for the Time Tracking card: the segments of the thin allocation bar, the legend
 * under it and the focus-loss caption. Ported from the macOS `TimeTrackingAllocationPresentation`.
 */
data class TimeTrackingAllocationPresentation(
    /** Allocated buckets largest first, then the pending remainder. Zero-length buckets have no segment. */
    val segments: List<Segment>,
    /** At most [MAX_LEGEND_ENTRIES] allocated buckets, then the pending remainder. */
    val legend: List<LegendEntry>,
    /** Allocated buckets that did not fit the legend. */
    val hiddenLegendCount: Int,
    val barHelpText: String,
    /** "≈15m focus lost", or null without any focus loss. */
    val focusLossCaption: String?,
) {
    sealed interface SegmentKind {
        /** Time assigned to a project or Other; [toneIndex] picks a tone of the neutral ramp in order of size. */
        data class Allocated(val toneIndex: Int) : SegmentKind

        /** Tracked time that still needs an allocation. */
        data object Pending : SegmentKind
    }

    data class Segment(
        val id: String,
        val title: String,
        val duration: Duration,
        val durationText: String,
        /** Share of the bar, 0..1. */
        val fraction: Double,
        val kind: SegmentKind,
        val helpText: String,
    )

    data class LegendEntry(
        val id: String,
        val title: String,
        val durationText: String,
        val kind: SegmentKind,
        val helpText: String,
    )

    /** "+2 more" under the legend, or null when everything fits. */
    val hiddenLegendText: String?
        get() = hiddenLegendCount.takeIf { it > 0 }?.let { "+$it more" }

    companion object {
        const val MAX_LEGEND_ENTRIES = 4
        const val PENDING_ID = "pending"

        fun of(
            allocated: List<AllocatedBucket>,
            pending: Duration,
            estimatedFocusLoss: Duration,
        ): TimeTrackingAllocationPresentation {
            val buckets = allocated.sortedWith(compareByDescending<AllocatedBucket> { it.duration }.thenBy { it.title.lowercase() })
            val pendingDuration = pending.coerceAtLeastZero()
            val allocatedTotal = buckets.fold(Duration.ZERO) { total, bucket -> total + bucket.duration.coerceAtLeastZero() }
            val total = allocatedTotal + pendingDuration
            val totalSeconds = total.seconds.toDouble()

            val segments = mutableListOf<Segment>()
            val legend = mutableListOf<LegendEntry>()

            buckets.forEachIndexed { index, bucket ->
                val durationText = TimeTrackingDurationFormatting.text(bucket.duration)
                val help = helpText(bucket, durationText)
                val kind = SegmentKind.Allocated(toneIndex = index)

                if (bucket.duration.seconds > 0 && totalSeconds > 0) {
                    segments += Segment(
                        id = bucket.id,
                        title = bucket.title,
                        duration = bucket.duration,
                        durationText = durationText,
                        fraction = bucket.duration.seconds / totalSeconds,
                        kind = kind,
                        helpText = help,
                    )
                }
                if (legend.size < MAX_LEGEND_ENTRIES) {
                    legend += LegendEntry(bucket.id, bucket.title, durationText, kind, help)
                }
            }

            if (pendingDuration.seconds > 0) {
                val pendingText = TimeTrackingDurationFormatting.text(pendingDuration)
                val help = "Pending: $pendingText still to allocate"
                segments += Segment(
                    id = PENDING_ID,
                    title = "Pending",
                    duration = pendingDuration,
                    durationText = pendingText,
                    fraction = pendingDuration.seconds / totalSeconds,
                    kind = SegmentKind.Pending,
                    helpText = help,
                )
                legend += LegendEntry(PENDING_ID, "Pending", pendingText, SegmentKind.Pending, help)
            }

            val barHelp = "Allocated ${TimeTrackingDurationFormatting.text(allocatedTotal)} of " +
                "${TimeTrackingDurationFormatting.text(total)} tracked today" +
                if (pendingDuration.seconds > 0) " · ${TimeTrackingDurationFormatting.text(pendingDuration)} pending" else ""

            return TimeTrackingAllocationPresentation(
                segments = segments,
                legend = legend,
                hiddenLegendCount = (buckets.size - MAX_LEGEND_ENTRIES).coerceAtLeast(0),
                barHelpText = barHelp,
                focusLossCaption = estimatedFocusLoss.takeIf { it.seconds > 0 }
                    ?.let { "≈${TimeTrackingDurationFormatting.text(it)} focus lost" },
            )
        }

        private fun helpText(bucket: AllocatedBucket, durationText: String): String {
            var text = "${bucket.title}: $durationText"
            if (bucket.interruptionCount > 0) {
                val plural = if (bucket.interruptionCount == 1) "" else "s"
                text += " · ${bucket.interruptionCount} interruption$plural"
                text += " · focus ${TimeTrackingDurationFormatting.text(bucket.focusAdjustedDuration)}"
            }
            return text
        }
    }
}
