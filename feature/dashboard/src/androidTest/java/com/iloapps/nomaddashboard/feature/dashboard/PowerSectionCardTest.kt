package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertCountEquals
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.MetricHistoryPoint
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class PowerSectionCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val onBattery = PowerSnapshot(
        batteryPercent = 72,
        statusLabel = "On Battery",
        batteryHealthSummary = "Good",
        powerSourceLabel = "Battery",
        dischargeWatts = 11.4,
        temperatureCelsius = 31.2,
        voltageVolts = 3.85,
        minutesRemaining = 208,
        batteryPercentHistory = listOf(80.0, 76.0, 72.0).mapIndexed { index, value ->
            MetricHistoryPoint(Instant.parse("2026-10-12T10:00:00Z").plusSeconds(index * 900L), value)
        },
    )

    private fun show(power: PowerSnapshot) {
        composeRule.setContent {
            NomadTheme {
                Box(modifier = Modifier.width(360.dp)) {
                    PowerSectionCard(state = DashboardUiState(snapshot = DashboardSnapshot(power = power)))
                }
            }
        }
    }

    @Test
    fun onBatteryTheRowIsBatteryDrainAndTimeLeft() {
        show(onBattery)

        composeRule.onNodeWithTag("dashboard_power_card").assertIsDisplayed()
        composeRule.onNodeWithText("72%").assertIsDisplayed()
        composeRule.onNodeWithText("11.4 W").assertIsDisplayed()
        composeRule.onNodeWithText("3h 28m").assertIsDisplayed()
        composeRule.onNodeWithText("DRAIN").assertIsDisplayed()
        composeRule.onNodeWithText("TIME LEFT").assertIsDisplayed()
    }

    @Test
    fun theChargeHasASparkline() {
        show(onBattery)

        composeRule.onAllNodesWithContentDescription("Last 30 min", substring = true).assertCountEquals(1)
    }

    @Test
    fun theSubtitleIsNotRepeatedByTheValuesOnBattery() {
        show(onBattery)

        composeRule.onAllNodesWithText("Running on battery").assertCountEquals(0)
        composeRule.onAllNodesWithText("On Battery").assertCountEquals(1)
    }

    @Test
    fun chargingShowsTheTimeToFullAndNamesTheSourceOnce() {
        show(
            PowerSnapshot(
                batteryPercent = 50,
                charging = true,
                statusLabel = "Charging",
                powerSourceLabel = "AC",
                dischargeWatts = 7.2,
                minutesToFull = 13,
            ),
        )

        composeRule.onNodeWithText("TO FULL").assertIsDisplayed()
        composeRule.onNodeWithText("13m").assertIsDisplayed()
        composeRule.onAllNodesWithText("AC power").assertCountEquals(1)
        composeRule.onAllNodesWithText("DRAIN").assertCountEquals(0)
    }

    @Test
    fun aFullPluggedInBatteryReadsFull() {
        show(PowerSnapshot(batteryPercent = 100, charging = true, statusLabel = "Full", powerSourceLabel = "AC"))

        composeRule.onNodeWithText("CHARGE").assertIsDisplayed()
        composeRule.onNodeWithText("Full").assertIsDisplayed()
        composeRule.onNodeWithText("Plugged In").assertIsDisplayed()
    }

    @Test
    fun theDiagnosticsStayReachableInTheDetailsRow() {
        show(onBattery)

        composeRule.onAllNodesWithText("Temperature").assertCountEquals(0)
        composeRule.onNodeWithTag("power_details_toggle").performClick()

        composeRule.onNodeWithText("Health").assertIsDisplayed()
        composeRule.onNodeWithText("Good").assertIsDisplayed()
        composeRule.onNodeWithText("Source").assertIsDisplayed()
        composeRule.onNodeWithText("31.2°C").assertIsDisplayed()
        composeRule.onNodeWithText("3.85 V").assertIsDisplayed()
    }
}
