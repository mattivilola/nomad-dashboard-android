package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.InternetState
import com.iloapps.nomaddashboard.core.model.MetricHistoryPoint
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ConnectivitySectionCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val start = Instant.parse("2026-10-12T10:00:00Z")

    private fun history(vararg values: Double) =
        values.mapIndexed { index, value -> MetricHistoryPoint(start.plusSeconds(index * 600L), value) }

    private val connectivity = ConnectivitySnapshot(
        internetState = InternetState.ONLINE,
        isOnline = true,
        latencyMs = 28.0,
        jitterMs = 3.0,
        downloadMbps = 96.0,
        uploadMbps = 19.2,
        wifiName = "Nomad Hub",
        downloadHistoryMbps = history(80.0, 92.0, 96.0),
        uploadHistoryMbps = history(18.0, 19.0, 19.2),
        latencyHistoryMs = history(30.0, 27.0, 28.0),
    )

    private fun show(connectivity: ConnectivitySnapshot) {
        composeRule.setContent {
            NomadTheme {
                Box(modifier = Modifier.width(360.dp)) {
                    ConnectivitySectionCard(
                        state = DashboardUiState(snapshot = DashboardSnapshot(connectivity = connectivity)),
                    )
                }
            }
        }
    }

    @Test
    fun downUpAndLatencyShareOneRowWithTheirUnits() {
        show(connectivity)

        composeRule.onNodeWithTag("dashboard_connectivity_card").assertIsDisplayed()
        val down = composeRule.onNodeWithText("96.0 Mbps").fetchSemanticsNode().boundsInRoot
        val up = composeRule.onNodeWithText("19.2 Mbps").fetchSemanticsNode().boundsInRoot
        val latency = composeRule.onNodeWithText("28 ms").fetchSemanticsNode().boundsInRoot

        assertEquals(down.top, up.top, 1f)
        assertEquals(down.top, latency.top, 1f)
        composeRule.onNodeWithText("DOWN").assertIsDisplayed()
        composeRule.onNodeWithText("UP").assertIsDisplayed()
        composeRule.onNodeWithText("LATENCY").assertIsDisplayed()
    }

    @Test
    fun everyMetricHasASparklineFromItsHistory() {
        show(connectivity)

        composeRule.onAllNodesWithContentDescription("Last 20 min", substring = true).assertCountEquals(3)
    }

    @Test
    fun theCaptionCarriesTheJitterAndTheHostThatAnsweredTheProbe() {
        show(connectivity.copy(latencyProbeHost = "1.1.1.1"))

        composeRule.onNodeWithText("Jitter 3 ms via 1.1.1.1").assertIsDisplayed()
    }

    @Test
    fun theCaptionLeavesOutTheHostTheDataLayerDoesNotReport() {
        show(connectivity)

        composeRule.onNodeWithText("Jitter 3 ms").assertIsDisplayed()
    }

    @Test
    fun theCaptionSaysWhenJitterWillAppear() {
        show(connectivity.copy(jitterMs = null))

        composeRule.onNodeWithText("Jitter appears after the next refresh").assertIsDisplayed()
    }

    @Test
    fun offlineShowsNoJitterHintAndAnUnavailableLatency() {
        show(ConnectivitySnapshot(internetState = InternetState.OFFLINE))

        composeRule.onNodeWithText("Unavailable").assertIsDisplayed()
        composeRule.onNodeWithTag("connectivity_jitter_caption").assertDoesNotExist()
    }

    @Test
    fun theStatusBadgeAndNetworkNameStayInTheHeader() {
        show(connectivity)

        composeRule.onNodeWithText("Online").assertIsDisplayed()
        composeRule.onNodeWithText("Nomad Hub").assertIsDisplayed()
    }

    @Test
    fun withoutHistoryTheLayoutKeepsItsHeight() {
        show(connectivity.copy(downloadHistoryMbps = emptyList(), uploadHistoryMbps = emptyList(), latencyHistoryMs = emptyList()))

        composeRule.onAllNodesWithContentDescription("Last 20 min", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("96.0 Mbps").assertIsDisplayed()
    }
}
