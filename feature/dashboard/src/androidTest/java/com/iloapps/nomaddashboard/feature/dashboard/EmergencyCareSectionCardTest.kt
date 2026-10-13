package com.iloapps.nomaddashboard.feature.dashboard

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import com.iloapps.nomaddashboard.core.model.EmergencyCareFacility
import com.iloapps.nomaddashboard.core.model.EmergencyCareLocationSource
import com.iloapps.nomaddashboard.core.model.EmergencyCareSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareStatus
import com.iloapps.nomaddashboard.core.model.HospitalOwnership
import com.iloapps.nomaddashboard.core.travel.safety.EmergencyNumberDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EmergencyCareSectionCardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<MapTarget>()

    private val punta = EmergencyCareFacility(
        placeId = "hospital-1",
        name = "Hospital Punta Europa",
        address = "Carretera Getares, Algeciras",
        distanceKilometers = 18.6,
        latitude = 36.1184,
        longitude = -5.4534,
    )

    private fun readySnapshot(
        vararg facilities: EmergencyCareFacility = arrayOf(punta),
        radius: Double = 25.0,
    ) = EmergencyCareSnapshot(
        status = EmergencyCareStatus.READY,
        countryCode = "ES",
        countryName = "Spain",
        searchRadiusKilometers = radius,
        locationSource = EmergencyCareLocationSource.DEVICE,
        facilities = facilities.toList(),
        detail = "Nearest hospital found within 10 km.",
        note = "Using device location.",
    )

    private fun setCard(
        snapshot: EmergencyCareSnapshot,
        hasMapsApiKey: Boolean = false,
        numbers: Boolean = true,
    ) {
        composeRule.setContent {
            NomadTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    EmergencyCareSectionCard(
                        enabled = true,
                        snapshot = snapshot,
                        emergencyNumbers = if (numbers) EmergencyNumberDirectory.numbersFor("ES") else null,
                        onOpenMap = { opened += it },
                        mapPreview = rememberMapPreviewController(onOpenExternal = { opened += it }, hasMapsApiKey = hasMapsApiKey),
                    )
                }
            }
        }
    }

    @Test
    fun rendersReadyFacilityWithItsMapMenu() {
        setCard(readySnapshot())

        composeRule.onNodeWithTag(EmergencyCareCardTag).assertIsDisplayed()
        composeRule.onNodeWithText("Hospital Punta Europa").assertIsDisplayed()
        composeRule.onNodeWithText("Within 25 km · Emergency 112").assertIsDisplayed()

        composeRule.onNodeWithTag(hospitalRowMenuTag("hospital-1")).performClick()
        composeRule.onNodeWithText("Open in Google Maps").assertIsDisplayed()
        composeRule.onNodeWithText("Open in Google Maps").performClick()

        assertEquals(listOf(MapTarget("Hospital Punta Europa", 36.1184, -5.4534)), opened)
    }

    @Test
    fun rendersConfigurationMessage() {
        setCard(
            EmergencyCareSnapshot(
                status = EmergencyCareStatus.CONFIGURATION_REQUIRED,
                detail = "Emergency care is unavailable in this build right now.",
            ),
            numbers = false,
        )

        composeRule.onNodeWithText("Configuration").assertIsDisplayed()
        composeRule.onNodeWithText("Emergency care is unavailable in this build right now.").assertIsDisplayed()
    }

    @Test
    fun subtitleFollowsTheSearchRadius() {
        setCard(readySnapshot(radius = 100.0))

        composeRule.onNodeWithText("Within 100 km · Emergency 112").assertIsDisplayed()
    }

    @Test
    fun listsHospitalsNearestFirstWithNeutralOwnershipChips() {
        setCard(
            readySnapshot(
                facility("far", "Far Private Clinic", 6.8, HospitalOwnership.PRIVATE),
                facility("near", "Near Public Hospital", 2.4, HospitalOwnership.PUBLIC),
                facility("unknown", "Unknown Hospital", 9.0, HospitalOwnership.UNKNOWN),
            ),
        )

        val nearTop = composeRule.onNodeWithText("Near Public Hospital").performScrollTo().fetchSemanticsNode().positionInRoot.y
        val farTop = composeRule.onNodeWithText("Far Private Clinic").performScrollTo().fetchSemanticsNode().positionInRoot.y
        assertTrue("The nearest hospital must be listed first.", nearTop < farTop)

        composeRule.onNodeWithText("Public").assertIsDisplayed()
        composeRule.onNodeWithText("Private").assertIsDisplayed()
        composeRule.onNodeWithText("2.4 km").performScrollTo().assertIsDisplayed()
        // Unknown ownership shows no chip.
        composeRule.onAllNodesWithText("Unknown").assertCountEquals(0)
    }

    @Test
    fun tappingARowOpensGoogleMapsWhenThereIsNoMapsKey() {
        setCard(readySnapshot(), hasMapsApiKey = false)

        composeRule.onNodeWithTag(hospitalRowTag("hospital-1")).performClick()

        assertEquals(listOf(MapTarget("Hospital Punta Europa", 36.1184, -5.4534)), opened)
    }

    @Test
    fun tappingARowShowsTheMapPreviewWhenThereIsAMapsKey() {
        setCard(readySnapshot(), hasMapsApiKey = true)

        composeRule.onNodeWithTag(hospitalRowTag("hospital-1")).performClick()

        composeRule.onNodeWithTag(MapPreviewSheetTag).assertIsDisplayed()
        composeRule.onNodeWithTag(MapPreviewOpenExternalTag).performClick()
        assertEquals(listOf(MapTarget("Hospital Punta Europa", 36.1184, -5.4534)), opened)
    }

    @Test
    fun theMenuOffersThePreviewOnlyWhenItCanBeShown() {
        setCard(readySnapshot(), hasMapsApiKey = true)

        composeRule.onNodeWithContentDescription("More actions for Hospital Punta Europa, 19 km").performClick()

        composeRule.onNodeWithText("Show map preview").assertIsDisplayed()
        composeRule.onNodeWithText("Open in Google Maps").assertIsDisplayed()
    }

    @Test
    fun theMenuLeavesOutThePreviewWithoutAMapsKey() {
        setCard(readySnapshot(), hasMapsApiKey = false)

        composeRule.onNodeWithContentDescription("More actions for Hospital Punta Europa, 19 km").performClick()

        composeRule.onAllNodesWithText("Show map preview").assertCountEquals(0)
        composeRule.onNodeWithText("Open in Google Maps").assertIsDisplayed()
    }

    private fun facility(id: String, name: String, distance: Double, ownership: HospitalOwnership) =
        EmergencyCareFacility(
            placeId = id,
            name = name,
            distanceKilometers = distance,
            latitude = 36.0,
            longitude = -5.5,
            ownership = ownership,
        )
}
