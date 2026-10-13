package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMission
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionIndex
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionKind
import com.iloapps.nomaddashboard.core.travel.safety.EmbassyOverview
import com.iloapps.nomaddashboard.core.travel.safety.EmbassyOverviewBuilder
import com.iloapps.nomaddashboard.core.travel.safety.MissionCoordinate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EmbassySectionCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val rabat = MissionCoordinate(34.020, -6.841)

    private fun mission(
        id: String,
        sending: String,
        host: String = "MA",
        kind: DiplomaticMissionKind = DiplomaticMissionKind.Embassy,
        name: String = "Embassy of $sending",
        city: String? = "Rabat",
        latitude: Double = 34.05,
        longitude: Double = -6.84,
        address: String? = null,
        website: String? = null,
        approximate: Boolean = true,
    ) = DiplomaticMission(
        id = id, sendingCountry = sending, hostCountry = host, kind = kind, name = name, city = city,
        latitude = latitude, longitude = longitude, address = address, website = website, isApproximateLocation = approximate,
    )

    private val builder = EmbassyOverviewBuilder(
        Locale.US,
        indexProvider = {
            DiplomaticMissionIndex(
                missions = listOf(
                    mission(
                        "FI-RAB", "FI", name = "Embassy of Finland in Rabat", address = "Rue 1, Rabat",
                        website = "https://finlandabroad.fi/rabat", approximate = false,
                    ),
                    mission(
                        "FI-CAS", "FI", kind = DiplomaticMissionKind.HonoraryConsulate,
                        name = "Honorary Consulate of Finland in Casablanca", city = "Casablanca",
                        latitude = 33.57, longitude = -7.59,
                    ),
                    mission("SE-RAB", "SE", name = "Embassy of Sweden in Rabat"),
                    mission("DE-RAB", "DE", name = "Embassy of Germany in Rabat"),
                ),
                source = "Wikidata (CC0)",
                generatedAt = "2026-10-05",
            )
        },
    )

    private val opened = mutableListOf<String>()
    private val dialed = mutableListOf<String>()
    private val copied = mutableListOf<String>()
    private val settings = mutableListOf<SettingsDestination?>()

    private fun show(overview: EmbassyOverview?) {
        composeRule.setContent {
            NomadTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    EmbassySectionCard(
                        overview = overview,
                        onOpenSettings = { settings += it },
                        onOpenUri = { opened += it },
                        onDial = { dialed += it },
                        onCopy = { copied += it },
                    )
                }
            }
        }
    }

    private fun overview(citizenships: List<String>, host: String?, coordinate: MissionCoordinate? = rabat) =
        builder.build(citizenships, host, coordinate)

    @Test
    fun abroadShowsTheConsularLineAndTheNearestListedMissions() {
        show(overview(listOf("FI"), "MA"))

        composeRule.onNodeWithTag(EmbassyCardTag).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Finland in Morocco").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Listed").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("+358 9 1605 5555").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("24/7 consular line", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Embassy of Finland in Rabat").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Embassy").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Rabat").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(embassyMissionTag("FI-CAS")).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Travel notification").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("All missions").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(EmbassyFooterTag).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun distancesShowTheTildeOnlyForApproximateMissions() {
        show(overview(listOf("FI"), "MA"))

        composeRule.onNodeWithText("3.3 km").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(embassyMissionTag("FI-CAS")).performScrollTo()
        composeRule.onNodeWithText("~85 km").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theConsularLineDialsAndCopies() {
        show(overview(listOf("FI"), "MA"))

        composeRule.onNodeWithTag(embassyCallTag("FI")).performClick()
        composeRule.onNodeWithTag(embassyCopyTag("FI")).performClick()

        assertEquals(listOf("+358 9 1605 5555"), dialed)
        assertEquals(listOf("+358 9 1605 5555"), copied)
    }

    @Test
    fun aMissionRowOpensItsMapsLinkAndTheMenuHoldsTheOtherActions() {
        val overview = overview(listOf("FI"), "MA")
        show(overview)

        composeRule.onNodeWithText("Embassy of Finland in Rabat").performClick()
        assertEquals(listOf(overview.missions.first().mapsUri), opened)
        assertTrue(opened.single().startsWith("geo:"))

        composeRule.onNodeWithTag(embassyMissionMenuTag("FI-RAB")).performClick()
        composeRule.onNodeWithText("Show in Maps").assertIsDisplayed()
        composeRule.onNodeWithText("Open website").assertIsDisplayed()
        composeRule.onNodeWithText("Copy address").performClick()
        assertEquals(listOf("Rue 1, Rabat"), copied)

        composeRule.onNodeWithTag(embassyMissionMenuTag("FI-RAB")).performClick()
        composeRule.onNodeWithText("Open website").performClick()
        assertEquals("https://finlandabroad.fi/rabat", opened.last())
    }

    @Test
    fun theLinksOpenTheRegistrationServiceAndTheMissionList() {
        show(overview(listOf("FI"), "MA"))

        composeRule.onNodeWithTag(embassyRegistrationTag("FI")).performScrollTo().performClick()
        composeRule.onNodeWithTag(embassyDirectoryTag("FI")).performScrollTo().performClick()

        assertEquals(listOf("https://matkustusilmoitus.fi/", "https://finlandabroad.fi/frontpage"), opened)
    }

    @Test
    fun aCitizenWithoutAMissionSeesTheNordicFallback() {
        // Norway has no mission in this dataset; Finland and Sweden are the nearest known Nordic missions.
        show(overview(listOf("NO"), "MA"))

        composeRule.onNodeWithText("Nordic help").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("no mission listed in Morocco", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Nearest known Nordic missions", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(embassyMissionTag("FI-RAB")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anEuCitizenSeesTheEuConsularProtectionNotice() {
        show(overview(listOf("IT"), "MA"))

        composeRule.onNodeWithText("EU help").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Nearest known EU missions", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun atHomeShowsTheHotlineAndTheHomeLine() {
        show(overview(listOf("FI"), "FI"))

        composeRule.onNodeWithText("Home").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("You're home in Finland — embassy details appear when you travel").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("+358 9 1605 5555").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithTag(EmbassyFooterTag).assertCountEquals(0)
    }

    @Test
    fun withoutACitizenshipTheCardPromptsAndOpensTheCitizenshipSetting() {
        show(overview(emptyList(), "MA"))

        composeRule.onNodeWithText("Off").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(EmbassyOverviewBuilder.OffMessage).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(EmbassyOpenSettingsTag).performClick()

        assertEquals(listOf<SettingsDestination?>(SettingsDestination.CITIZENSHIP), settings)
    }

    @Test
    fun anUnknownCountryAsksForLocationAndKeepsTheHotline() {
        show(overview(listOf("FI"), null))

        composeRule.onNodeWithText("Location Needed").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(EmbassyOverviewBuilder.LocationNeededTitle).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("+358 9 1605 5555").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(EmbassyOpenSettingsTag).performClick()

        assertEquals(listOf<SettingsDestination?>(null), settings)
    }

    @Test
    fun whileTheDatasetLoadsTheCardSaysSo() {
        show(null)

        composeRule.onNodeWithText("Checking listed missions…").performScrollTo().assertIsDisplayed()
    }
}
