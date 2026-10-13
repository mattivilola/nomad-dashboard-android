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
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.model.FuelStationPrice
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.model.StartupLocationBootstrapState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FuelPricesSectionCardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<MapTarget>()

    private val readySnapshot = FuelPriceSnapshot(
        status = FuelPriceStatus.READY,
        sourceName = "Tankerkoenig",
        countryName = "Germany",
        searchRadiusKilometers = 50.0,
        diesel = station(FuelType.DIESEL, 1.429),
        gasoline = station(FuelType.GASOLINE, 1.549),
        detail = "Cheapest within 50 km.",
    )

    private fun station(type: FuelType, price: Double) = FuelStationPrice(
        fuelType = type,
        stationName = "Aral Tarifa",
        locality = "Tarifa",
        pricePerLiter = price,
        currencyCode = "EUR",
        distanceKilometers = 4.8,
        latitude = 36.01,
        longitude = -5.6,
    )

    private fun setCard(snapshot: FuelPriceSnapshot, hasMapsApiKey: Boolean = false, enabled: Boolean = true) {
        composeRule.setContent {
            NomadTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    FuelPricesSectionCard(
                        enabled = enabled,
                        snapshot = snapshot,
                        startupLocation = StartupLocationBootstrapState(),
                        onOpenSettings = {},
                        onOpenMap = { opened += it },
                        mapPreview = rememberMapPreviewController(onOpenExternal = { opened += it }, hasMapsApiKey = hasMapsApiKey),
                    )
                }
            }
        }
    }

    @Test
    fun listsDieselThenGasolineWithTheUnitDrawnSmall() {
        setCard(readySnapshot)

        composeRule.onNodeWithTag(FuelPricesCardTag).assertIsDisplayed()
        composeRule.onNodeWithTag(fuelRowTag(FuelType.DIESEL)).assertIsDisplayed()
        composeRule.onNodeWithTag(fuelRowTag(FuelType.GASOLINE)).assertIsDisplayed()
        // The price and its small "/L" are one text of two sizes, so a screen reader reads it as one.
        composeRule.onAllNodesWithText("/L", substring = true).assertCountEquals(2)
        composeRule.onNodeWithText("Cheapest within 50 km.").assertIsDisplayed()
    }

    @Test
    fun hasNoSeparateMapButtonInTheRow() {
        setCard(readySnapshot)

        composeRule.onAllNodesWithText("Map").assertCountEquals(0)
    }

    @Test
    fun tappingARowOpensGoogleMapsWhenThereIsNoMapsKey() {
        setCard(readySnapshot, hasMapsApiKey = false)

        composeRule.onNodeWithTag(fuelRowTag(FuelType.DIESEL)).performClick()

        assertEquals(listOf(MapTarget("Diesel · Aral Tarifa", 36.01, -5.6)), opened)
    }

    @Test
    fun tappingARowShowsTheMapPreviewWhenThereIsAMapsKey() {
        setCard(readySnapshot, hasMapsApiKey = true)

        composeRule.onNodeWithTag(fuelRowTag(FuelType.GASOLINE)).performClick()

        composeRule.onNodeWithTag(MapPreviewSheetTag).assertIsDisplayed()
        composeRule.onNodeWithText("Gasoline · Aral Tarifa").assertIsDisplayed()
        assertEquals(emptyList<MapTarget>(), opened)
    }

    @Test
    fun googleMapsStaysReachableFromTheRowMenu() {
        setCard(readySnapshot, hasMapsApiKey = true)

        composeRule.onNodeWithTag(fuelRowMenuTag(FuelType.DIESEL)).performClick()
        composeRule.onNodeWithText("Show map preview").assertIsDisplayed()
        composeRule.onNodeWithText("Open in Google Maps").performClick()

        assertEquals(listOf(MapTarget("Diesel · Aral Tarifa", 36.01, -5.6)), opened)
    }

    @Test
    fun theRowMenuIsLabelledWithTheRow() {
        setCard(readySnapshot)

        composeRule.onNodeWithContentDescription("More actions for Diesel, Aral Tarifa", substring = true).assertIsDisplayed()
    }

    @Test
    fun unavailableSourceShowsItsMessage() {
        setCard(
            FuelPriceSnapshot(
                status = FuelPriceStatus.CONFIGURATION_REQUIRED,
                detail = "Add a Tankerkoenig key in Settings.",
            ),
        )

        composeRule.onNodeWithText("Add a Tankerkoenig key in Settings.").assertIsDisplayed()
    }

    @Test
    fun aTurnedOffCardPointsToSettings() {
        setCard(FuelPriceSnapshot(), enabled = false)

        composeRule.onNodeWithText("Open Settings").assertIsDisplayed()
    }
}
