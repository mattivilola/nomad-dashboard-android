package com.iloapps.nomaddashboard.feature.dashboard

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MapPreviewSheetTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val place = MapTarget("Hospital Punta Europa", 36.1184, -5.4534)

    @Test
    fun theSheetNamesThePlaceAndOffersGoogleMaps() {
        var openedExternally = 0
        composeRule.setContent {
            NomadTheme {
                MapPreviewSheet(
                    title = place.title,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    onOpenExternal = { openedExternally++ },
                )
            }
        }

        composeRule.onNodeWithTag(MapPreviewSheetTag).assertIsDisplayed()
        composeRule.onNodeWithText("Hospital Punta Europa").assertIsDisplayed()
        composeRule.onNodeWithTag(MapPreviewOpenExternalTag).performClick()
        assertEquals(1, openedExternally)
    }

    @Test
    fun withAMapsKeyAValidPlaceIsPreviewed() {
        val external = mutableListOf<MapTarget>()
        val controller = MapPreviewController(hasMapsApiKey = true) { external += it }

        controller.preview(place)

        assertEquals(place, controller.target)
        assertTrue(external.isEmpty())
        assertTrue(controller.canPreview(place))
    }

    @Test
    fun withoutAMapsKeyThePlaceOpensInGoogleMapsInstead() {
        val external = mutableListOf<MapTarget>()
        val controller = MapPreviewController(hasMapsApiKey = false) { external += it }

        controller.preview(place)

        assertEquals(null, controller.target)
        assertEquals(listOf(place), external)
        assertFalse(controller.canPreview(place))
    }

    @Test
    fun aPlaceWithoutUsableCoordinatesOpensNothing() {
        val external = mutableListOf<MapTarget>()
        val controller = MapPreviewController(hasMapsApiKey = true) { external += it }
        val broken = MapTarget("Nowhere", Double.NaN, 200.0)

        controller.preview(broken)

        assertEquals(null, controller.target)
        assertTrue(external.isEmpty())
        assertFalse(broken.isCoordinateValid)
    }

    @Test
    fun dismissingClosesThePreview() {
        val controller = MapPreviewController(hasMapsApiKey = true) {}
        controller.preview(place)

        controller.dismiss()

        assertEquals(null, controller.target)
    }
}
