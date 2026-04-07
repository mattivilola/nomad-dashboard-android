package com.iloapps.nomaddashboard.feature.dashboard

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.iloapps.nomaddashboard.core.model.EmergencyCareFacility
import com.iloapps.nomaddashboard.core.model.EmergencyCareLocationSource
import com.iloapps.nomaddashboard.core.model.EmergencyCareSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareStatus
import org.junit.Rule
import org.junit.Test

class EmergencyCareSectionCardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersReadyFacilityAndMapsAction() {
        composeRule.setContent {
            EmergencyCareSectionCard(
                enabled = true,
                snapshot = EmergencyCareSnapshot(
                    status = EmergencyCareStatus.READY,
                    countryCode = "ES",
                    countryName = "Spain",
                    locationSource = EmergencyCareLocationSource.DEVICE,
                    facility = EmergencyCareFacility(
                        placeId = "hospital-1",
                        name = "Hospital Punta Europa",
                        address = "Carretera Getares, Algeciras",
                        distanceKilometers = 18.6,
                        latitude = 36.1184,
                        longitude = -5.4534,
                    ),
                    detail = "Nearest hospital found within 10 km.",
                    note = "Using device location.",
                ),
            )
        }

        composeRule.onNodeWithTag(EmergencyCareCardTag).assertIsDisplayed()
        composeRule.onNodeWithText("Hospital Punta Europa").assertIsDisplayed()
        composeRule.onNodeWithText("Open in Maps").assertIsDisplayed()
    }

    @Test
    fun rendersConfigurationMessage() {
        composeRule.setContent {
            EmergencyCareSectionCard(
                enabled = true,
                snapshot = EmergencyCareSnapshot(
                    status = EmergencyCareStatus.CONFIGURATION_REQUIRED,
                    detail = "Emergency care is unavailable in this build right now.",
                ),
            )
        }

        composeRule.onNodeWithText("Configuration").assertIsDisplayed()
        composeRule.onNodeWithText("Emergency care is unavailable in this build right now.").assertIsDisplayed()
    }
}
