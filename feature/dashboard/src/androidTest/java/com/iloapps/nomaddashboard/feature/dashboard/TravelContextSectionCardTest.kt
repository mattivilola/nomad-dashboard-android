package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TravelContextSectionCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val connectivity = ConnectivitySnapshot(
        wifiName = "Nomad Hub",
        wifiSignalDbm = -56,
        wifiLinkSpeedMbps = 720,
        wifiFrequencyMhz = 5180,
        timeZoneId = "Europe/Madrid",
    )

    private val travelContext = TravelContextSnapshot(
        publicIp = "203.0.113.42",
        city = "Valencia",
        country = "Spain",
        countryCode = "ES",
        timeZoneId = "Europe/Madrid",
        latitude = 39.47,
        longitude = -0.38,
    )

    private var copied = 0
    private var mapOpened = 0
    private var permissionRequested = 0

    private fun show(
        connectivity: ConnectivitySnapshot = this.connectivity,
        travelContext: TravelContextSnapshot = this.travelContext,
        hasLocationPermission: Boolean = true,
        publicIpEnabled: Boolean = true,
    ) {
        composeRule.setContent {
            NomadTheme {
                Box(modifier = Modifier.width(360.dp)) {
                    TravelContextSectionCard(
                        state = DashboardUiState(
                            settings = AppSettings(publicIpGeolocationEnabled = publicIpEnabled),
                            snapshot = DashboardSnapshot(connectivity = connectivity, travelContext = travelContext),
                        ),
                        hasLocationPermission = hasLocationPermission,
                        onRequestLocationPermission = { permissionRequested += 1 },
                        onCopyPublicIp = { copied += 1 },
                        onOpenMap = { mapOpened += 1 },
                    )
                }
            }
        }
    }

    @Test
    fun showsFourRowsPublicIpWifiVpnAndLocation() {
        show()

        composeRule.onNodeWithTag("dashboard_travel_context_card").assertIsDisplayed()
        composeRule.onNodeWithText("Public IP").assertIsDisplayed()
        composeRule.onNodeWithText("203.0.113.42").assertIsDisplayed()
        composeRule.onNodeWithText("Wi-Fi").assertIsDisplayed()
        composeRule.onNodeWithText("Nomad Hub · −56 dBm · 720 Mbps").assertIsDisplayed()
        composeRule.onNodeWithText("VPN").assertIsDisplayed()
        composeRule.onNodeWithText("Inactive").assertIsDisplayed()
        composeRule.onNodeWithText("Location").assertIsDisplayed()
        composeRule.onNodeWithText("Valencia, Spain · Europe/Madrid").assertIsDisplayed()
    }

    @Test
    fun copyAndMapActionsAreOnTheirRows() {
        show()

        composeRule.onNodeWithContentDescription("Copy public IP").performClick()
        composeRule.onNodeWithContentDescription("Open map").performClick()

        assertEquals(1, copied)
        assertEquals(1, mapOpened)
    }

    @Test
    fun copyIsOffWithoutAPublicIp() {
        show(travelContext = TravelContextSnapshot(), publicIpEnabled = true)

        composeRule.onNodeWithContentDescription("Copy public IP").assertIsNotEnabled()
        composeRule.onNodeWithText("Refreshing…").assertIsDisplayed()
    }

    @Test
    fun aSwitchedOffIpLookupSaysSo() {
        show(travelContext = TravelContextSnapshot(), publicIpEnabled = false)

        composeRule.onNodeWithText("Off in Settings").assertIsDisplayed()
    }

    @Test
    fun theCardDoesNotRepeatTheWifiOrTheIpInASubtitleOrBadge() {
        show()

        composeRule.onAllNodesWithText("203.0.113.42").assertCountEquals(1)
        composeRule.onAllNodesWithText("Nomad Hub", substring = true).assertCountEquals(1)
    }

    @Test
    fun theDeviceVersusNetworkComparisonStaysReachable() {
        show(
            travelContext = travelContext.copy(
                deviceCity = "Lisbon",
                deviceCountry = "Portugal",
                deviceCountryCode = "PT",
            ),
        )

        composeRule.onNodeWithText("Mismatch").assertIsDisplayed()
        composeRule.onAllNodesWithText("Device").assertCountEquals(0)
        composeRule.onNodeWithTag("travel_context_comparison_toggle").performClick()

        composeRule.onNodeWithText("Device").assertIsDisplayed()
        composeRule.onNodeWithText("Lisbon, Portugal").assertIsDisplayed()
        composeRule.onNodeWithText("Valencia, Spain").assertIsDisplayed()
        composeRule.onNodeWithText("Time zone").assertIsDisplayed()
        composeRule.onNodeWithText("RSSI -56 · 720 Mbps · 5 GHz").assertIsDisplayed()
    }

    @Test
    fun missingLocationPermissionOffersToAllowIt() {
        show(hasLocationPermission = false)

        composeRule.onNodeWithTag("travel_context_allow_location").performClick()

        assertEquals(1, permissionRequested)
    }

    @Test
    fun withPermissionThereIsNoAllowAction() {
        show(hasLocationPermission = true)

        composeRule.onAllNodesWithText("Allow location").assertCountEquals(0)
    }
}
