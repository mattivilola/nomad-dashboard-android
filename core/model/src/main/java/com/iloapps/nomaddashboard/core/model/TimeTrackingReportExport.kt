package com.iloapps.nomaddashboard.core.model

import java.time.Duration

/** A portable, credential-free summary intended for a user-initiated share action. */
data class TimeTrackingReportExport(
    val subject: String,
    val text: String,
)

fun TimeTrackingReportSnapshot.toShareableExport(): TimeTrackingReportExport {
    val lines = buildList {
        add("Nomad Dashboard time report")
        add("")
        add("Today: ${formatExportDuration(todaysAllocatedDuration)} allocated · $interruptionsToday interruption${interruptionsToday.pluralSuffix()} · ${formatExportDuration(todaysEstimatedFocusTime)} estimated focus")
        if (dayReports.isEmpty()) {
            add("")
            add("No completed daily report is available yet.")
        } else {
            add("")
            add("Daily report")
            dayReports.sortedByDescending(TimeTrackingDayReport::date).forEach { day ->
                add("${day.date}: ${formatExportDuration(day.allocatedDuration)} allocated · ${day.interruptionCount} interruption${day.interruptionCount.pluralSuffix()} · ${formatExportDuration(day.estimatedFocusTime)} estimated focus")
                day.projectReports.forEach { project ->
                    add("  ${project.project.name}: ${formatExportDuration(project.reportedDuration)} logged · ${formatExportDuration(project.estimatedFocusTime)} estimated focus")
                }
            }
        }
    }
    return TimeTrackingReportExport(
        subject = "Nomad Dashboard time report",
        text = lines.joinToString(separator = "\n"),
    )
}

private fun formatExportDuration(duration: Duration): String {
    val totalMinutes = duration.seconds.coerceAtLeast(0) / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours == 0L) "$minutes min" else "${hours}h ${minutes}m"
}

private fun Int.pluralSuffix(): String = if (this == 1) "" else "s"
