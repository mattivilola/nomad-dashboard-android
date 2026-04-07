package com.iloapps.nomaddashboard.feature.dashboard

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.iloapps.nomaddashboard.core.model.TravelAlertKind
import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalSnapshot
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalState
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalStatus
import com.iloapps.nomaddashboard.core.model.TravelAlertsSnapshot
import com.iloapps.nomaddashboard.core.model.TravelAlertUnavailableReason
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class TravelAlertsSectionCardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersCheckingRowsFromControlledSnapshot() {
        composeRule.setContent {
            TravelAlertsSectionCard(
                snapshot = TravelAlertsSnapshot(
                    primaryCountryCode = "ES",
                    primaryCountryName = "Spain",
                    coverageCountryCodes = listOf("ES", "FR", "PT"),
                ),
            )
        }

        composeRule.onNodeWithTag(TravelAlertsCardTag).assertIsDisplayed()
        composeRule.onNodeWithText("Travel Advisory").assertIsDisplayed()
        composeRule.onNodeWithText("Regional Security").assertIsDisplayed()
        composeRule.onAllNodesWithText("Checking alerts...").assertCountEquals(2)
    }

    @Test
    fun rendersReadyAndUnavailableRowsFromControlledSnapshot() {
        composeRule.setContent {
            TravelAlertsSectionCard(
                snapshot = TravelAlertsSnapshot(
                    primaryCountryCode = "ES",
                    primaryCountryName = "Spain",
                    coverageCountryCodes = listOf("ES", "FR"),
                    states = listOf(
                        TravelAlertSignalState(
                            kind = TravelAlertKind.ADVISORY,
                            status = TravelAlertSignalStatus.READY,
                            signal = TravelAlertSignalSnapshot(
                                kind = TravelAlertKind.ADVISORY,
                                severity = TravelAlertSeverity.CAUTION,
                                title = "Travel advisory",
                                summary = "France is at Level 2 nearby.",
                                sourceName = "Smartraveller",
                                updatedAt = Instant.parse("2026-04-07T10:00:00Z"),
                            ),
                            sourceName = "Smartraveller",
                            lastAttemptedAt = Instant.parse("2026-04-07T10:00:00Z"),
                            lastSuccessAt = Instant.parse("2026-04-07T10:00:00Z"),
                        ),
                        TravelAlertSignalState(
                            kind = TravelAlertKind.SECURITY,
                            status = TravelAlertSignalStatus.UNAVAILABLE,
                            reason = TravelAlertUnavailableReason.SOURCE_CONFIGURATION_REQUIRED,
                            sourceName = "ReliefWeb",
                            diagnosticSummary = "ReliefWeb app name approval required.",
                        ),
                    ),
                    fetchedAt = Instant.parse("2026-04-07T10:00:00Z"),
                ),
            )
        }

        composeRule.onNodeWithText("France is at Level 2 nearby.").assertIsDisplayed()
        composeRule.onNodeWithText("ReliefWeb app name approval required.").assertIsDisplayed()
        composeRule.onNodeWithText("Limited").assertIsDisplayed()
    }
}
