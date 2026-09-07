package com.iloapps.nomaddashboard.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import org.junit.Test

class TimeTrackingReportExportTest {
    @Test
    fun `share export contains stable daily and project summaries without credentials`() {
        val report = TimeTrackingReportSnapshot(
            interruptionsToday = 2,
            todaysAllocatedDuration = Duration.ofMinutes(135),
            todaysEstimatedFocusTime = Duration.ofMinutes(89),
            dayReports = listOf(
                TimeTrackingDayReport(
                    date = LocalDate.of(2026, 9, 7),
                    interruptionCount = 2,
                    allocatedDuration = Duration.ofMinutes(135),
                    estimatedFocusTime = Duration.ofMinutes(89),
                    projectReports = listOf(
                        TimeTrackingProjectReport(
                            project = TimeTrackingProject(
                                id = UUID.fromString("00000000-0000-0000-0000-000000000701"),
                                name = "Client work",
                            ),
                            reportedDuration = Duration.ofMinutes(90),
                            estimatedFocusTime = Duration.ofMinutes(67),
                        ),
                    ),
                ),
            ),
        )

        val export = report.toShareableExport()

        assertThat(export.subject).isEqualTo("Nomad Dashboard time report")
        assertThat(export.text).isEqualTo(
            """
            Nomad Dashboard time report

            Today: 2h 15m allocated · 2 interruptions · 1h 29m estimated focus

            Daily report
            2026-09-07: 2h 15m allocated · 2 interruptions · 1h 29m estimated focus
              Client work: 1h 30m logged · 1h 7m estimated focus
            """.trimIndent(),
        )
    }
}
