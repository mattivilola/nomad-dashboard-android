package com.iloapps.nomaddashboard.feature.dashboard

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.iloapps.nomaddashboard.core.model.TravelAlertKind
import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalSnapshot
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalState
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalStatus
import com.iloapps.nomaddashboard.core.model.TravelAlertsSnapshot
import com.iloapps.nomaddashboard.core.model.TravelAlertUnavailableReason
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
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
                                detailSummary = "Exercise a high degree of caution in France due to the threat of terrorism.",
                                sourceName = "Smartraveller",
                                sourceUrl = "https://www.smartraveller.gov.au/destinations/europe/france",
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
        composeRule.onNodeWithText("Exercise a high degree of caution in France due to the threat of terrorism.").assertIsDisplayed()
        composeRule.onNodeWithText("More details").assertIsDisplayed()
        composeRule.onNodeWithText("ReliefWeb app name approval required.").assertIsDisplayed()
        // The badge is the highest level, stated once; the unavailable source is a caption beside it.
        composeRule.onAllNodesWithText("Caution").assertCountEquals(1)
        composeRule.onNodeWithText("Some sources unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("SOURCE ISSUE").assertIsDisplayed()
        composeRule.onAllNodesWithText("Limited").assertCountEquals(0)
    }

    @Test
    fun rendersCompactSummaryAndExpandedDetailSeparatelyForPrimaryAdvisory() {
        composeRule.setContent {
            TravelAlertsSectionCard(
                snapshot = TravelAlertsSnapshot(
                    enabledKinds = listOf(TravelAlertKind.ADVISORY),
                    primaryCountryCode = "FR",
                    primaryCountryName = "France",
                    coverageCountryCodes = listOf("FR", "ES"),
                    states = listOf(
                        TravelAlertSignalState(
                            kind = TravelAlertKind.ADVISORY,
                            status = TravelAlertSignalStatus.READY,
                            signal = TravelAlertSignalSnapshot(
                                kind = TravelAlertKind.ADVISORY,
                                severity = TravelAlertSeverity.CAUTION,
                                title = "Travel advisory",
                                summary = "France: exercise a high degree of caution.",
                                detailSummary = "Exercise a high degree of caution because you're travelling during a disruption.",
                                sourceName = "Smartraveller",
                                sourceUrl = "https://www.smartraveller.gov.au/destinations/europe/france",
                                updatedAt = Instant.parse("2026-04-07T10:00:00Z"),
                            ),
                            sourceName = "Smartraveller",
                            lastAttemptedAt = Instant.parse("2026-04-07T10:00:00Z"),
                            lastSuccessAt = Instant.parse("2026-04-07T10:00:00Z"),
                        ),
                    ),
                    fetchedAt = Instant.parse("2026-04-07T10:00:00Z"),
                ),
            )
        }

        composeRule.onNodeWithText("France: exercise a high degree of caution.").assertIsDisplayed()
        composeRule.onNodeWithText("Exercise a high degree of caution because you're travelling during a disruption.").assertIsDisplayed()
        composeRule.onNodeWithText("France + 1 nearby country").assertIsDisplayed()
    }

    @Test
    fun staleSignalsAreACaptionAndTheBadgeStillNamesTheLevel() {
        composeRule.setContent {
            TravelAlertsSectionCard(
                snapshot = TravelAlertsSnapshot(
                    enabledKinds = listOf(TravelAlertKind.ADVISORY),
                    primaryCountryCode = "ES",
                    primaryCountryName = "Spain",
                    coverageCountryCodes = listOf("ES"),
                    states = listOf(
                        TravelAlertSignalState(
                            kind = TravelAlertKind.ADVISORY,
                            status = TravelAlertSignalStatus.STALE,
                            signal = TravelAlertSignalSnapshot(
                                kind = TravelAlertKind.ADVISORY,
                                severity = TravelAlertSeverity.CAUTION,
                                title = "Travel advisory",
                                summary = "Exercise normal safety precautions.",
                                sourceName = "Smartraveller",
                                sourceUrl = "https://www.smartraveller.gov.au/destinations/europe/spain",
                                updatedAt = Instant.parse("2026-10-05T10:00:00Z"),
                            ),
                            sourceName = "Smartraveller",
                            lastSuccessAt = Instant.parse("2026-10-12T10:00:00Z"),
                        ),
                    ),
                ),
            )
        }

        val zone = ZoneId.systemDefault()
        val expected = "Updated ${DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH).withZone(zone).format(Instant.parse("2026-10-12T10:00:00Z"))} · may be outdated"
        composeRule.onNodeWithText(expected).assertIsDisplayed()
        composeRule.onNodeWithText("Last known: Exercise normal safety precautions.").assertIsDisplayed()
        composeRule.onNodeWithText("More details").assertIsDisplayed()
        composeRule.onAllNodesWithText("Stale").assertCountEquals(0)
        composeRule.onNodeWithText("Caution").assertIsDisplayed()
    }

    @Test
    fun aSecondSignalKeepsItsOwnLevelWhenTheBadgeNamesAnother() {
        composeRule.setContent {
            TravelAlertsSectionCard(
                snapshot = TravelAlertsSnapshot(
                    primaryCountryCode = "ES",
                    primaryCountryName = "Spain",
                    coverageCountryCodes = listOf("ES"),
                    states = listOf(
                        readyState(TravelAlertKind.ADVISORY, TravelAlertSeverity.CAUTION, "Advisory summary"),
                        readyState(TravelAlertKind.SECURITY, TravelAlertSeverity.CLEAR, "Security summary"),
                    ),
                ),
            )
        }

        composeRule.onAllNodesWithText("Caution").assertCountEquals(1)
        composeRule.onAllNodesWithText("Clear").assertCountEquals(1)
        composeRule.onNodeWithText("Advisory summary").assertIsDisplayed()
        composeRule.onNodeWithText("Security summary").assertIsDisplayed()
    }

    @Test
    fun turnedOffAlertsPointToSettings() {
        var opened = 0
        composeRule.setContent {
            TravelAlertsSectionCard(
                snapshot = TravelAlertsSnapshot(enabledKinds = emptyList()),
                onOpenSettings = { opened += 1 },
            )
        }

        composeRule.onNodeWithText("Off").assertIsDisplayed()
        composeRule.onNodeWithText("Enable traveller alerts in Settings.").assertIsDisplayed()
        composeRule.onNodeWithText("Open Settings").performClick()
        assertEquals(1, opened)
    }

    private fun readyState(kind: TravelAlertKind, severity: TravelAlertSeverity, summary: String) =
        TravelAlertSignalState(
            kind = kind,
            status = TravelAlertSignalStatus.READY,
            signal = TravelAlertSignalSnapshot(
                kind = kind,
                severity = severity,
                title = "Title",
                summary = summary,
                sourceName = "Source",
                updatedAt = Instant.parse("2026-10-12T10:00:00Z"),
            ),
            sourceName = "Source",
        )
}
