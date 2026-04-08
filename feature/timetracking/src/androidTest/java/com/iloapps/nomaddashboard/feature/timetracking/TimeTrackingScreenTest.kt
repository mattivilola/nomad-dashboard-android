package com.iloapps.nomaddashboard.feature.timetracking

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.TimeTrackingEntry
import com.iloapps.nomaddashboard.core.model.TimeTrackingOtherProjectId
import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
import com.iloapps.nomaddashboard.core.model.TimeTrackingRecord
import java.time.Instant
import java.util.UUID
import org.junit.Rule
import org.junit.Test

class TimeTrackingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersActiveTrackingState() {
        val project = TimeTrackingProject(
            id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            name = "Client Work",
        )
        val activeEntry = TimeTrackingRecord(
            entry = TimeTrackingEntry(
                id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
                projectId = TimeTrackingOtherProjectId,
                startAt = Instant.parse("2026-04-07T09:00:00Z"),
                endAt = null,
            ),
            project = TimeTrackingProject(
                id = TimeTrackingOtherProjectId,
                name = "Other",
            ),
        )

        composeRule.setContent {
            NomadTheme {
                TimeTrackingScreen(
                    state = TimeTrackingUiState(
                        settings = AppSettings(projectTimeTrackingEnabled = true),
                        projects = listOf(project),
                        activeEntry = activeEntry,
                    ),
                    hasNotificationPermission = true,
                    onProjectNameChanged = {},
                    onCreateProject = {},
                    onAllocateTrackedTime = {},
                    onStartTracking = {},
                    onStopTracking = {},
                )
            }
        }

        composeRule.onNodeWithTag("timetracking_capture_card").assertIsDisplayed()
        composeRule.onNodeWithText("Capture Running").assertIsDisplayed()
        composeRule.onNodeWithText("Quick Allocate").assertIsDisplayed()
    }
}
