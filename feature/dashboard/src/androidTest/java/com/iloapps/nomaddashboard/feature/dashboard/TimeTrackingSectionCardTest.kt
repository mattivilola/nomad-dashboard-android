package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import com.iloapps.nomaddashboard.core.model.TimeTrackingBucket
import com.iloapps.nomaddashboard.core.model.TimeTrackingDayReport
import com.iloapps.nomaddashboard.core.model.TimeTrackingEntry
import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
import com.iloapps.nomaddashboard.core.model.TimeTrackingProjectReport
import com.iloapps.nomaddashboard.core.model.TimeTrackingRecord
import com.iloapps.nomaddashboard.core.model.TimeTrackingReportSnapshot
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TimeTrackingSectionCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val projects = (1..6).map { TimeTrackingProject(name = "Client project number $it") } +
        TimeTrackingProject(name = "Other")

    private fun report(
        interruptions: Int = 0,
        focusLoss: Long = 0,
        projectMinutes: List<Long> = emptyList(),
    ): TimeTrackingReportSnapshot {
        val projectReports = projectMinutes.mapIndexed { index, minutes ->
            TimeTrackingProjectReport(
                project = projects[index],
                reportedDuration = Duration.ofMinutes(minutes),
                estimatedFocusTime = Duration.ofMinutes(minutes),
            )
        }
        val allocated = projectReports.fold(Duration.ZERO) { total, item -> total + item.reportedDuration }
        return TimeTrackingReportSnapshot(
            interruptionsToday = interruptions,
            todaysEstimatedFocusLoss = Duration.ofMinutes(focusLoss),
            todaysAllocatedDuration = allocated,
            todaysEstimatedFocusTime = allocated - Duration.ofMinutes(focusLoss),
            dayReports = listOf(
                TimeTrackingDayReport(date = LocalDate.now(), allocatedDuration = allocated, projectReports = projectReports),
            ),
        )
    }

    private fun pending(minutes: Long): List<TimeTrackingRecord> {
        val end = Instant.now()
        return listOf(
            TimeTrackingRecord(
                entry = TimeTrackingEntry(
                    projectId = projects.last().id,
                    startAt = end.minus(Duration.ofMinutes(minutes)),
                    endAt = end,
                    bucket = TimeTrackingBucket.UNALLOCATED_MANUAL.name,
                ),
                project = projects.last(),
            ),
        )
    }

    private fun show(
        state: DashboardTimeTrackingUiState,
        hasNotificationPermission: Boolean = true,
        onStart: () -> Unit = {},
        onStop: () -> Unit = {},
        onInterruption: () -> Unit = {},
        onAllocate: (UUID) -> Unit = {},
    ) {
        composeRule.setContent {
            NomadTheme {
                Box(modifier = Modifier.width(360.dp)) {
                    TimeTrackingSectionCard(
                        trackingState = state,
                        hasNotificationPermission = hasNotificationPermission,
                        autoWindowLabel = "09:00-17:00",
                        interruptionPulseKey = 0,
                        onStartTracking = onStart,
                        onStopTracking = onStop,
                        onReportInterruption = onInterruption,
                        onAllocateTrackedTime = onAllocate,
                    )
                }
            }
        }
    }

    @Test
    fun showsTodayFocusAndPendingAsShortDurations() {
        show(
            DashboardTimeTrackingUiState(
                projects = projects,
                pendingEntries = pending(60),
                report = report(interruptions = 2, focusLoss = 46, projectMinutes = listOf(120, 30)),
            ),
        )

        composeRule.onNodeWithTag("dashboard_time_tracking_card").assertIsDisplayed()
        composeRule.onNodeWithText("TODAY").assertIsDisplayed()
        composeRule.onNodeWithText("3h 30m").assertIsDisplayed()
        composeRule.onNodeWithText("FOCUS").assertIsDisplayed()
        composeRule.onNodeWithText("2h 44m").assertIsDisplayed()
        composeRule.onNodeWithText("PENDING").assertIsDisplayed()
        // The pending hour is the metric and the pending segment of the legend.
        composeRule.onAllNodesWithText("1h").assertCountEquals(2)
    }

    @Test
    fun theLegendNamesFourProjectsInFullThenPendingAndHowManyMore() {
        show(
            DashboardTimeTrackingUiState(
                projects = projects,
                pendingEntries = pending(30),
                report = report(projectMinutes = listOf(60, 50, 40, 30, 20, 10)),
            ),
        )

        composeRule.onNodeWithTag("time_tracking_allocation_bar").assertIsDisplayed()
        (1..4).forEach { composeRule.onNodeWithText("Client project number $it").assertIsDisplayed() }
        composeRule.onAllNodesWithText("Client project number 5").assertCountEquals(0)
        composeRule.onNodeWithText("Pending").assertIsDisplayed()
        composeRule.onNodeWithText("+2 more").assertIsDisplayed()
    }

    @Test
    fun theFocusLostCaptionSitsUnderTheInterruptionControl() {
        show(
            DashboardTimeTrackingUiState(
                projects = projects,
                report = report(interruptions = 1, focusLoss = 15, projectMinutes = listOf(60)),
            ),
        )

        composeRule.onNodeWithText("≈15m focus lost").assertIsDisplayed()
        composeRule.onNodeWithText("Report interruption").assertIsDisplayed()
    }

    @Test
    fun noInterruptionsMeansNoFocusLostCaption() {
        show(DashboardTimeTrackingUiState(projects = projects, report = report(projectMinutes = listOf(60))))

        composeRule.onAllNodesWithText("focus lost", substring = true).assertCountEquals(0)
    }

    @Test
    fun theInterruptionControlReportsAnInterruption() {
        var reported = 0
        show(DashboardTimeTrackingUiState(projects = projects, report = report(interruptions = 3)), onInterruption = { reported += 1 })

        composeRule.onNodeWithText("3").assertIsDisplayed()
        composeRule.onNodeWithTag("time_tracking_interruption").performClick()

        assertEquals(1, reported)
    }

    @Test
    fun resumeAndPauseStayInTheCard() {
        var started = 0
        show(DashboardTimeTrackingUiState(projects = projects), onStart = { started += 1 })

        composeRule.onNodeWithText("Resume capture").performClick()

        assertEquals(1, started)
    }

    @Test
    fun allocateListsEveryProjectInFullAndAllocatesTheChosenOne() {
        val chosen = mutableListOf<UUID>()
        show(
            DashboardTimeTrackingUiState(projects = projects, pendingEntries = pending(45), report = report()),
            onAllocate = { chosen += it },
        )

        composeRule.onNodeWithTag("time_tracking_allocate").assertIsEnabled().performClick()
        projects.forEach { composeRule.onNodeWithText(it.name).assertIsDisplayed() }
        composeRule.onNodeWithTag("time_tracking_allocate_item_${projects[5].id}").performClick()

        assertEquals(listOf(projects[5].id), chosen)
    }

    @Test
    fun allocateIsOffWithNothingToAllocate() {
        show(DashboardTimeTrackingUiState(projects = projects, report = report()))

        composeRule.onNodeWithTag("time_tracking_allocate").assertIsNotEnabled()
    }

    @Test
    fun theNotificationHintStaysWhenPermissionIsMissing() {
        show(DashboardTimeTrackingUiState(projects = projects), hasNotificationPermission = false)

        composeRule.onNodeWithText("Android 13+ notification permission is needed for the foreground capture notification.")
            .assertIsDisplayed()
    }
}
