package com.iloapps.nomaddashboard.core.travel.cards

import com.iloapps.nomaddashboard.core.model.TimeTrackingFocusLossPerInterruption
import com.iloapps.nomaddashboard.core.model.TimeTrackingProjectReport
import com.iloapps.nomaddashboard.core.model.TimeTrackingReportSnapshot
import java.time.Duration
import java.time.LocalDate

/**
 * The Time Tracking card's numbers: Today, Focus and Pending as short durations, and the allocation of
 * today's time across projects.
 *
 * - Today is the time allocated today plus the pending buffer.
 * - Focus is Today without the estimated focus loss of the interruptions.
 * - Pending is the captured time that still needs a project.
 */
data class TimeTrackingCardPresentation(
    val today: String,
    val focus: String,
    val pending: String,
    val allocation: TimeTrackingAllocationPresentation,
    val interruptionCount: Int,
    /** The accessibility and long-press text of the interruption control. */
    val interruptionHelp: String,
) {
    companion object {
        /**
         * @param report The tracking report; today's per-project allocation comes from its day report.
         * @param pending The buffered time not yet allocated, including a running capture.
         */
        fun of(
            report: TimeTrackingReportSnapshot,
            pending: Duration,
            today: LocalDate = LocalDate.now(),
        ): TimeTrackingCardPresentation {
            val pendingDuration = pending.coerceAtLeastZero()
            val projectReports = report.dayReports.firstOrNull { it.date == today }?.projectReports.orEmpty()
            val tracked = report.todaysAllocatedDuration + pendingDuration
            val focus = (tracked - report.todaysEstimatedFocusLoss).coerceAtLeastZero()

            return TimeTrackingCardPresentation(
                today = TimeTrackingDurationFormatting.text(tracked),
                focus = TimeTrackingDurationFormatting.text(focus),
                pending = TimeTrackingDurationFormatting.text(pendingDuration),
                allocation = TimeTrackingAllocationPresentation.of(
                    allocated = projectReports.map(::bucket),
                    pending = pendingDuration,
                    estimatedFocusLoss = report.todaysEstimatedFocusLoss,
                ),
                interruptionCount = report.interruptionsToday,
                interruptionHelp = interruptionHelp(report),
            )
        }

        private fun bucket(report: TimeTrackingProjectReport): AllocatedBucket =
            AllocatedBucket(
                id = report.project.id.toString(),
                title = report.project.name,
                duration = report.reportedDuration,
                interruptionCount = report.interruptionCount,
                focusAdjustedDuration = report.estimatedFocusTime,
            )

        private fun interruptionHelp(report: TimeTrackingReportSnapshot): String {
            val count = report.interruptionsToday
            if (count == 0) {
                return "Report an interruption. Focus cooldown assumes ${TimeTrackingFocusLossPerInterruption.toMinutes()} minutes per interruption."
            }
            val plural = if (count == 1) "" else "s"
            return "$count interruption$plural today. Estimated focus loss " +
                "${TimeTrackingDurationFormatting.text(report.todaysEstimatedFocusLoss)}."
        }
    }
}
