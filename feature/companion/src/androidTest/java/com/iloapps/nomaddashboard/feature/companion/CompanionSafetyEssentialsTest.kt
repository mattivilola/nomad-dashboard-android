package com.iloapps.nomaddashboard.feature.companion

import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import com.iloapps.nomaddashboard.core.model.CompanionImportPreview
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.NomadCompanionData
import com.iloapps.nomaddashboard.core.model.OfflineEssential
import com.iloapps.nomaddashboard.core.model.OfflineEssentialKind
import com.iloapps.nomaddashboard.core.travel.safety.EmbassyEssentials
import com.iloapps.nomaddashboard.core.travel.safety.EmergencyNumberDirectory
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CompanionSafetyEssentialsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val morocco = SafetyEssentials(
        countryName = "Morocco",
        emergencyNumbers = EmergencyNumberDirectory.numbersFor("MA"),
        embassy = EmbassyEssentials(
            nearestMission = "Embassy of Finland, Rabat",
            hotlines = listOf(EmbassyEssentials.Hotline("Ministry for Foreign Affairs", "+358 9 1605 5555")),
        ),
    )

    private val ownEssential = OfflineEssential(
        id = "hotel", kind = OfflineEssentialKind.NOTE, title = "Riad Yasmine", content = "Door code 4821",
        createdAtEpochMillis = 1L, updatedAtEpochMillis = 1L,
    )

    private fun showScreen(safety: SafetyEssentials, data: NomadCompanionData = NomadCompanionData(essentials = listOf(ownEssential))) {
        composeRule.setContent {
            NomadTheme {
                CompanionScreen(
                    snapshot = DashboardSnapshot(),
                    data = data,
                    onUpdate = {},
                    onAddEssential = { _, _, _, _ -> },
                    onUpdateEssential = {},
                    onAddPlanner = { _, _, _ -> },
                    onUpdatePlanner = {},
                    onRecordWorkplace = {},
                    onUpdateVisit = {},
                    onExport = { "" },
                    onPreviewImport = { CompanionImportPreview(accepted = false, message = "") },
                    onMergeImport = { CompanionImportPreview(accepted = false, message = "") },
                    safetyEssentials = safety,
                    modifier = Modifier.testTag("companion_list"),
                )
            }
        }
    }

    private fun scrollTo(tag: String): SemanticsNodeInteraction {
        composeRule.onNodeWithTag("companion_list").performScrollToNode(hasTestTag(tag))
        return composeRule.onNodeWithTag(tag)
    }

    @Test
    fun theBlockShowsTheLocalNumbersAndTheEmbassy() {
        showScreen(morocco)

        scrollTo(SafetyEssentialsTag).assertIsDisplayed()
        composeRule.onNodeWithText("Emergency numbers · Morocco").assertIsDisplayed()
        composeRule.onNodeWithText("Emergency 19").assertIsDisplayed()
        composeRule.onNodeWithText("Ambulance/Fire 15 · Gendarmerie 177").assertIsDisplayed()
        composeRule.onNodeWithText("Your embassy").assertIsDisplayed()
        composeRule.onNodeWithText("Embassy of Finland, Rabat").assertIsDisplayed()
        composeRule.onNodeWithText("Ministry for Foreign Affairs · +358 9 1605 5555").assertIsDisplayed()
        composeRule.onNodeWithText("Bundled with the app, so it works offline.").assertIsDisplayed()
    }

    @Test
    fun theBlockComesBeforeTheUsersOwnEssentials() {
        showScreen(morocco)

        val blockTop = scrollTo(SafetyEssentialsTag).getUnclippedBoundsInRoot().top
        val saveTop = composeRule.onNodeWithText("Save essential").getUnclippedBoundsInRoot().top
        assertTrue("safety block ($blockTop) should be above the form ($saveTop)", blockTop < saveTop)
    }

    @Test
    fun theUsersOwnEssentialsStayUnchanged() {
        showScreen(morocco)

        composeRule.onNodeWithTag("companion_list").performScrollToNode(hasText("Riad Yasmine"))
        composeRule.onNodeWithText("Riad Yasmine").assertExists()
        composeRule.onNodeWithText("Door code 4821").assertExists()
        composeRule.onNodeWithText("Save essential").assertExists()
    }

    @Test
    fun withoutAnySafetyDataTheBlockIsNotShownAndTheEssentialsRemain() {
        showScreen(SafetyEssentials.Empty)

        composeRule.onNodeWithTag("companion_list").performScrollToNode(hasText("Save essential"))
        composeRule.onAllNodesWithTag(SafetyEssentialsTag).assertCountEquals(0)
        composeRule.onNodeWithText("Offline essentials").assertExists()
    }

    @Test
    fun onlyTheNumbersShowWhenThereIsNoEmbassy() {
        showScreen(morocco.copy(embassy = null))

        scrollTo(SafetyEssentialsTag)
        composeRule.onAllNodesWithTag(SafetyEmbassyTag).assertCountEquals(0)
        composeRule.onNodeWithTag(SafetyEmergencyNumbersTag).assertIsDisplayed()
    }
}
