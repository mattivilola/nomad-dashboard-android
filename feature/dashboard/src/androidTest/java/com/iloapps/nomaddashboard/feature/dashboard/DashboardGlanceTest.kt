package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.InternetState
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.model.ResourceMode
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.model.TimeTrackingBucket
import com.iloapps.nomaddashboard.core.model.TimeTrackingEntry
import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
import com.iloapps.nomaddashboard.core.model.TimeTrackingRecord
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import com.iloapps.nomaddashboard.core.model.withCardCollapsed
import com.iloapps.nomaddashboard.core.travel.arrival.ArrivalBriefingPresentation
import com.iloapps.nomaddashboard.core.travel.arrival.ArrivalEvent
import com.iloapps.nomaddashboard.core.travel.glance.GlanceOverviewBuilder
import com.iloapps.nomaddashboard.core.travel.glance.GlanceQuickLink
import com.iloapps.nomaddashboard.core.travel.glance.GlanceVitalTile
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpItem
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpKind
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpSeverity
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The glance overview at the top of the dashboard: header, time-tracking row, verdict strip, clocks,
 * vital tiles, heads-up items, links and the arrival briefing. The screen is driven the way the view
 * model drives it: the callbacks write the state and the screen reads it back.
 */
class DashboardGlanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-10-12T09:30:00Z")
    private val madrid = ZoneId.of("Europe/Madrid")

    private var state by mutableStateOf(DashboardUiState())
    private val settingsRequests = mutableListOf<SettingsDestination?>()
    private val resourceModes = mutableListOf<ResourceMode>()
    private val acknowledged = mutableListOf<String>()
    private val allocated = mutableListOf<UUID>()
    private var companionOpened = 0
    private var startCount = 0
    private var stopCount = 0
    private var interruptionCount = 0
    private var refreshCount = 0
    private var reopenCount = 0

    private val online = ConnectivitySnapshot(
        internetState = InternetState.ONLINE,
        isOnline = true,
        latencyMs = 28.0,
        downloadMbps = 96.0,
        uploadMbps = 19.2,
        wifiName = "Nomad Hub",
    )

    private val healthySnapshot = DashboardSnapshot(
        connectivity = online,
        power = PowerSnapshot(batteryPercent = 78, statusLabel = "On Battery", powerSourceLabel = "Battery"),
        weather = WeatherSnapshot(currentTemperatureCelsius = 19.0, conditionDescription = "Clear"),
        travelContext = TravelContextSnapshot(deviceCity = "Valencia", deviceCountry = "Spain", city = "Madrid", country = "Spain"),
        lastRefresh = now,
    )

    private fun uiState(
        snapshot: DashboardSnapshot = healthySnapshot,
        settings: AppSettings = AppSettings(),
        headsUp: List<HeadsUpItem> = emptyList(),
        timeTracking: DashboardTimeTrackingUiState = DashboardTimeTrackingUiState(),
        arrival: ArrivalBriefingPresentation? = null,
        reopenable: ArrivalEvent? = null,
        homeTimeZoneId: String? = "Europe/Helsinki",
    ) = DashboardUiState(
        snapshot = snapshot,
        settings = settings,
        timeTracking = timeTracking,
        glance = GlanceOverviewBuilder().build(snapshot, settings, headsUp, homeTimeZoneId, now, madrid),
        arrival = arrival,
        reopenableArrival = reopenable,
    )

    private fun show(modifier: Modifier = Modifier) {
        composeRule.setContent {
            NomadTheme {
                Box(modifier = modifier) {
                    DashboardScreen(
                        state = state,
                        onRefresh = { refreshCount += 1 },
                        hasLocationPermission = true,
                        hasNotificationPermission = true,
                        onStartTracking = { startCount += 1 },
                        onStopTracking = { stopCount += 1 },
                        onReportInterruption = { interruptionCount += 1 },
                        onAllocateTrackedTime = { allocated += it },
                        onOpenSettings = { settingsRequests += it },
                        onOpenCompanion = { companionOpened += 1 },
                        onSelectResourceMode = { resourceModes += it },
                        onAcknowledgeArrival = { acknowledged += it },
                        onShowArrivalBriefing = { reopenCount += 1 },
                        layoutActions = DashboardLayoutActions(
                            onCollapsedChange = { card, collapsed ->
                                state = state.copy(settings = state.settings.withCardCollapsed(card, collapsed))
                            },
                        ),
                    )
                }
            }
        }
    }

    private fun scrollToTag(tag: String) {
        composeRule.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag(tag))
    }

    // region Header

    @Test
    fun theHeaderNamesThePlaceAndShowsTheLastRefreshTimeUnderTheButtons() {
        state = uiState()
        show()

        composeRule.onNodeWithTag("dashboard_header_headline").assertTextContains("Valencia")
        composeRule.onNodeWithTag("dashboard_refresh_status").assertTextContains("Last refresh", substring = true)
        composeRule.onNodeWithTag("dashboard_refresh_action").assertIsDisplayed()
        composeRule.onNodeWithTag(DashboardOverflowTag).assertIsDisplayed()
        composeRule.onNodeWithText("Nomad Dashboard").assertDoesNotExist()
    }

    @Test
    fun tappingRefreshRefreshesAndTheButtonShowsProgressWhileRefreshing() {
        state = uiState()
        show()

        composeRule.onNodeWithTag("dashboard_refresh_action").performClick()
        assertEquals(1, refreshCount)

        state = uiState(snapshot = healthySnapshot.copy(isRefreshing = true))
        composeRule.onNodeWithTag("dashboard_refresh_progress").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_refresh_action").performClick()
        assertEquals(1, refreshCount)
    }

    @Test
    fun theOverflowMenuStillOffersCustomizeResetAndTheTravelProfile() {
        state = uiState()
        show()

        composeRule.onNodeWithTag(DashboardOverflowTag).performClick()

        composeRule.onNodeWithTag(DashboardCustomizeToggleTag).assertIsDisplayed()
        composeRule.onNodeWithTag(DashboardResetLayoutTag).assertIsDisplayed()
        composeRule.onNodeWithText("Travel profile").assertIsDisplayed()
    }

    @Test
    fun anIpPartThatDoesNotFitStacksUnderTheDevicePartInsteadOfBeingCut() {
        state = uiState(
            snapshot = healthySnapshot.copy(
                travelContext = TravelContextSnapshot(
                    deviceCity = "Saint-Jean-de-Luz",
                    deviceRegion = "Nouvelle-Aquitaine",
                    deviceCountry = "France",
                    city = "Paris",
                    country = "France",
                ),
            ),
        )
        show(modifier = Modifier.width(230.dp))

        composeRule.onNodeWithTag("dashboard_header_location_device").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_header_location_ip").assertIsDisplayed()
        composeRule.onNodeWithText("IP Paris", substring = true, useUnmergedTree = true).assertIsDisplayed()
    }

    // endregion

    // region Time tracking row

    private val acme = TimeTrackingProject(name = "Acme redesign")

    private fun runningRecord(): TimeTrackingRecord =
        TimeTrackingRecord(
            entry = TimeTrackingEntry(
                projectId = acme.id,
                startAt = now.minusSeconds(3_600),
                bucket = TimeTrackingBucket.UNALLOCATED_MANUAL.name,
            ),
            project = acme,
        )

    @Test
    fun theTimeTrackingRowAppearsOnlyWhenTrackingIsOn() {
        state = uiState(settings = AppSettings(projectTimeTrackingEnabled = false))
        show()
        composeRule.onAllNodesWithTag(TimeTrackingRowTag).assertCountEquals(0)

        state = uiState(settings = AppSettings(projectTimeTrackingEnabled = true))
        composeRule.onNodeWithTag(TimeTrackingRowTag).assertIsDisplayed()
        composeRule.onNodeWithTag(TimeTrackingRowStatusTag).assertTextContains("Pending", substring = true)
        composeRule.onNodeWithTag(TimeTrackingRowInterruptionTag).assertIsDisplayed()
    }

    @Test
    fun theRowPausesWhileRunningAndResumesOtherwise() {
        state = uiState(
            settings = AppSettings(projectTimeTrackingEnabled = true),
            timeTracking = DashboardTimeTrackingUiState(activeEntry = runningRecord()),
        )
        show()

        composeRule.onNodeWithContentDescription("Pause").performClick()
        assertEquals(1, stopCount)
        assertEquals(0, startCount)

        state = uiState(settings = AppSettings(projectTimeTrackingEnabled = true))
        composeRule.onNodeWithContentDescription("Play").performClick()
        assertEquals(1, startCount)
    }

    @Test
    fun theInterruptionCounterReportsAnInterruption() {
        state = uiState(settings = AppSettings(projectTimeTrackingEnabled = true))
        show()

        composeRule.onNodeWithTag(TimeTrackingRowInterruptionTag).performClick()

        assertEquals(1, interruptionCount)
    }

    @Test
    fun theAllocateMenuListsUpToFiveProjectsAndOtherWithTheirFullNames() {
        val projects = (1..7).map { TimeTrackingProject(name = "Customer project number $it") }
        state = uiState(
            settings = AppSettings(projectTimeTrackingEnabled = true),
            timeTracking = DashboardTimeTrackingUiState(projects = projects, activeEntry = runningRecord()),
        )
        show()

        composeRule.onNodeWithTag(TimeTrackingRowAllocateTag).performClick()

        (1..5).forEach { composeRule.onNodeWithText("Customer project number $it").assertIsDisplayed() }
        composeRule.onNodeWithText("Customer project number 6").assertDoesNotExist()
        composeRule.onNodeWithText("Other").assertIsDisplayed()

        composeRule.onNodeWithText("Customer project number 3").performClick()
        assertEquals(listOf(projects[2].id), allocated)
    }

    @Test
    fun allocatingToOtherUsesTheBuiltInOtherProject() {
        state = uiState(
            settings = AppSettings(projectTimeTrackingEnabled = true),
            timeTracking = DashboardTimeTrackingUiState(activeEntry = runningRecord()),
        )
        show()

        composeRule.onNodeWithTag(TimeTrackingRowAllocateTag).performClick()
        composeRule.onNodeWithTag("dashboard_time_tracking_allocate_other").performClick()

        assertEquals(listOf(com.iloapps.nomaddashboard.core.model.TimeTrackingOtherProjectId), allocated)
    }

    @Test
    fun withNothingToAllocateTheMenuEntriesAreDisabled() {
        state = uiState(settings = AppSettings(projectTimeTrackingEnabled = true))
        show()

        composeRule.onNodeWithTag(TimeTrackingRowAllocateTag).performClick()

        composeRule.onNodeWithTag("dashboard_time_tracking_allocate_other").assertIsNotEnabled()
    }

    @Test
    fun theOverflowMenuReopensTheBriefingWhileItIsAvailableAndNotShown() {
        state = uiState(reopenable = ArrivalEvent("MA", "ES", java.time.LocalDate.of(2026, 10, 12)))
        show()

        composeRule.onNodeWithTag(DashboardOverflowTag).performClick()
        composeRule.onNodeWithTag(DashboardShowArrivalTag).performClick()

        assertEquals(1, reopenCount)
    }

    @Test
    fun theReopenItemIsHiddenWithoutAnArrivalToShowOrWhileTheCardIsVisible() {
        state = uiState(reopenable = null)
        show()
        composeRule.onNodeWithTag(DashboardOverflowTag).performClick()
        composeRule.onAllNodesWithTag(DashboardShowArrivalTag).assertCountEquals(0)
    }

    @Test
    fun theReopenItemIsHiddenWhileTheBriefingIsAlreadyShown() {
        state = uiState(arrival = welcome, reopenable = ArrivalEvent("MA", "ES", java.time.LocalDate.of(2026, 10, 12)))
        show()
        composeRule.onNodeWithTag(DashboardOverflowTag).performClick()
        composeRule.onAllNodesWithTag(DashboardShowArrivalTag).assertCountEquals(0)
    }

    @Test
    fun theTimeTrackingRowKeepsAllItsControlsOnOneLine() {
        state = uiState(
            settings = AppSettings(projectTimeTrackingEnabled = true),
            timeTracking = DashboardTimeTrackingUiState(activeEntry = runningRecord()),
        )
        show()

        val status = composeRule.onNodeWithTag(TimeTrackingRowStatusTag).fetchSemanticsNode().boundsInRoot
        val allocate = composeRule.onNodeWithTag(TimeTrackingRowAllocateTag).fetchSemanticsNode().boundsInRoot
        val counter = composeRule.onNodeWithTag(TimeTrackingRowInterruptionTag).fetchSemanticsNode().boundsInRoot

        // Same line: the controls overlap vertically, and Allocate is at the end of the line.
        assertTrue(allocate.top < status.bottom && allocate.bottom > status.top)
        assertTrue(counter.top < status.bottom && counter.bottom > status.top)
        assertTrue(allocate.left > counter.right)
    }

    // endregion

    // region Verdict, clocks, data usage

    @Test
    fun theVerdictStripStatesTheOverallVerdictAndItsDetail() {
        state = uiState()
        show()

        composeRule.onNodeWithTag(GlanceVerdictTag).assertIsDisplayed()
        composeRule.onNodeWithText(state.glance.verdict.title).assertIsDisplayed()
        composeRule.onNodeWithText(state.glance.verdict.detail).assertIsDisplayed()
    }

    @Test
    fun anOfflineDeviceIsToldItIsOfflineWithSavedEssentialsAvailable() {
        state = uiState(snapshot = healthySnapshot.copy(connectivity = ConnectivitySnapshot(internetState = InternetState.OFFLINE)))
        show()

        composeRule.onNodeWithText("You're offline").assertIsDisplayed()
        composeRule.onNodeWithText("Saved essentials are available").assertIsDisplayed()
    }

    @Test
    fun theDataUsageMenuOffersAutoNormalAndLowDataAndSavesTheChoice() {
        state = uiState(settings = AppSettings(resourceMode = ResourceMode.AUTO))
        show()

        composeRule.onNodeWithTag(DataUsageMenuTag).assertTextContains("Auto", substring = true)
        composeRule.onNodeWithTag(DataUsageMenuTag).performClick()
        ResourceMode.entries.forEach { composeRule.onNodeWithTag(dataUsageOptionTag(it)).assertIsDisplayed() }
        composeRule.onNodeWithTag(dataUsageOptionTag(ResourceMode.LOW_DATA)).performClick()

        assertEquals(listOf(ResourceMode.LOW_DATA), resourceModes)
    }

    @Test
    fun theClocksLineShowsHereAndHomeAndOpensTheHomeTimeZoneSetting() {
        state = uiState()
        show()

        composeRule.onNodeWithTag(GlanceClocksTag).assertIsDisplayed()
        composeRule.onNodeWithText("Here Valencia", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Home Helsinki", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(GlanceClocksSettingsTag).performClick()

        assertEquals(listOf<SettingsDestination?>(SettingsDestination.HOME_TIME_ZONE), settingsRequests)
    }

    // endregion

    // region Tiles

    @Test
    fun fourVitalTilesShowInAGridInOrder() {
        state = uiState()
        show()

        listOf(
            GlanceVitalTile.Kind.INTERNET,
            GlanceVitalTile.Kind.POWER,
            GlanceVitalTile.Kind.VPN,
            GlanceVitalTile.Kind.WEATHER,
        ).forEach { composeRule.onNodeWithTag(glanceTileTag(it)).assertIsDisplayed() }
    }

    @Test
    fun tappingATileExpandsItsCollapsedCardAndScrollsToIt() {
        state = uiState(
            settings = AppSettings(dashboardCollapsedCards = setOf(DashboardCardId.WEATHER, DashboardCardId.POWER)),
        )
        show()

        composeRule.onNodeWithTag(glanceTileTag(GlanceVitalTile.Kind.WEATHER)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            DashboardCardId.WEATHER !in state.settings.dashboardCollapsedCards
        }
        composeRule.onNodeWithTag("dashboard_weather_card").assertIsDisplayed()
        // Only the tapped tile's card was expanded.
        assertEquals(setOf(DashboardCardId.POWER), state.settings.dashboardCollapsedCards)
    }

    @Test
    fun theInternetPowerAndVpnTilesTargetTheirOwnCards() {
        state = uiState()
        val targets = state.glance.tiles.associate { it.kind to it.scrollTarget }

        assertEquals(DashboardCardId.CONNECTIVITY, targets[GlanceVitalTile.Kind.INTERNET])
        assertEquals(DashboardCardId.POWER, targets[GlanceVitalTile.Kind.POWER])
        assertEquals(DashboardCardId.TRAVEL_CONTEXT, targets[GlanceVitalTile.Kind.VPN])
        assertEquals(DashboardCardId.WEATHER, targets[GlanceVitalTile.Kind.WEATHER])
    }

    @Test
    fun tappingTheInternetTileExpandsTheConnectivityCard() {
        state = uiState(settings = AppSettings(dashboardCollapsedCards = setOf(DashboardCardId.CONNECTIVITY)))
        show()

        composeRule.onNodeWithTag(glanceTileTag(GlanceVitalTile.Kind.INTERNET)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            DashboardCardId.CONNECTIVITY !in state.settings.dashboardCollapsedCards
        }
        composeRule.onNodeWithTag(dashboardCardToggleTag(DashboardCardId.CONNECTIVITY)).assertIsDisplayed()
    }

    // endregion

    // region Heads-up and links

    private val rainSoon = HeadsUpItem(
        kind = HeadsUpKind.RAIN_SOON,
        severity = HeadsUpSeverity.INFO,
        title = "Rain likely from 15:00",
        statusText = "Rain 15:00",
        detail = "70% chance",
        relatedCard = DashboardCardId.WEATHER,
    )
    private val advisory = HeadsUpItem(
        kind = HeadsUpKind.TRAVEL_ADVISORY,
        severity = HeadsUpSeverity.CAUTION,
        title = "Spain: exercise caution",
        statusText = "Advisory",
        detail = null,
        relatedCard = DashboardCardId.TRAVEL_ALERTS,
    )

    @Test
    fun theHeadsUpListIsHiddenWhenThereAreNoItems() {
        state = uiState(headsUp = emptyList())
        show()

        composeRule.onAllNodesWithTag(GlanceHeadsUpTag).assertCountEquals(0)
    }

    @Test
    fun headsUpItemsShowTitleAndDetail() {
        state = uiState(headsUp = listOf(rainSoon, advisory))
        show()

        composeRule.onNodeWithTag(GlanceHeadsUpTag).assertIsDisplayed()
        composeRule.onNodeWithText("Rain likely from 15:00").assertIsDisplayed()
        composeRule.onNodeWithText("70% chance").assertIsDisplayed()
        composeRule.onNodeWithText("Spain: exercise caution").assertIsDisplayed()
    }

    @Test
    fun tappingAHeadsUpItemExpandsAndScrollsToItsRelatedCard() {
        state = uiState(
            settings = AppSettings(dashboardCollapsedCards = setOf(DashboardCardId.WEATHER)),
            headsUp = listOf(rainSoon),
        )
        show()

        composeRule.onNodeWithTag(glanceHeadsUpItemTag(HeadsUpKind.RAIN_SOON)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            DashboardCardId.WEATHER !in state.settings.dashboardCollapsedCards
        }
        composeRule.onNodeWithTag("dashboard_weather_card").assertIsDisplayed()
    }

    @Test
    fun workplacesAndSavedEssentialsOpenTheCompanionScreen() {
        state = uiState()
        show()

        GlanceQuickLink.entries.forEach { link ->
            composeRule.onNodeWithTag(glanceLinkTag(link)).assertIsDisplayed()
        }
        composeRule.onNodeWithTag(glanceLinkTag(GlanceQuickLink.WORKPLACES)).performClick()
        composeRule.onNodeWithTag(glanceLinkTag(GlanceQuickLink.SAVED_ESSENTIALS)).performClick()

        assertEquals(2, companionOpened)
    }

    // endregion

    // region Arrival briefing

    private val welcome = ArrivalBriefingPresentation(
        countryCode = "MA",
        title = "Welcome to Morocco",
        subtitle = "Arrived from Spain",
        rows = listOf(
            ArrivalBriefingPresentation.Row(
                id = "emergency",
                kind = ArrivalBriefingPresentation.Kind.EMERGENCY,
                title = "Emergency",
                text = "19",
                detail = "Ambulance/Fire 15 · Gendarmerie 177",
                tone = ArrivalBriefingPresentation.Tone.NEUTRAL,
                flag = null,
                isSelectable = true,
            ),
            ArrivalBriefingPresentation.Row(
                id = "power",
                kind = ArrivalBriefingPresentation.Kind.POWER,
                title = "Power",
                text = "Plug C, E · 220 V · 50 Hz",
                detail = null,
                tone = ArrivalBriefingPresentation.Tone.NEUTRAL,
                flag = "Changed",
                isSelectable = false,
            ),
        ),
        collapsedSummary = "Emergency 19",
    )

    @Test
    fun noArrivalMeansNoBriefingCard() {
        state = uiState(arrival = null)
        show()

        composeRule.onAllNodesWithTag(ArrivalCardTag).assertCountEquals(0)
    }

    @Test
    fun theArrivalBriefingShowsItsSectionsRightBelowTheGlance() {
        state = uiState(arrival = welcome)
        show()

        composeRule.onNodeWithTag(ArrivalCardTag).assertIsDisplayed()
        composeRule.onNodeWithText("Welcome to Morocco").assertIsDisplayed()
        composeRule.onNodeWithText("Arrived from Spain").assertIsDisplayed()
        composeRule.onNodeWithTag(arrivalRowTag("emergency")).assertIsDisplayed()
        composeRule.onNodeWithText("Plug C, E · 220 V · 50 Hz").assertIsDisplayed()
        composeRule.onNodeWithText("Changed").assertIsDisplayed()
    }

    @Test
    fun gotItAcknowledgesTheArrivalOfThatCountry() {
        state = uiState(arrival = welcome)
        show()

        composeRule.onNodeWithTag(ArrivalAcknowledgeTag).performClick()

        assertEquals(listOf("MA"), acknowledged)
    }

    @Test
    fun theBriefingCanBeTurnedOffFromTheCard() {
        state = uiState(arrival = welcome)
        show()

        composeRule.onNodeWithTag(ArrivalSettingsTag).performClick()

        assertEquals(listOf<SettingsDestination?>(SettingsDestination.ARRIVAL_BRIEFING), settingsRequests)
    }

    @Test
    fun foldingTheBriefingKeepsItsTitleAndHidesTheSections() {
        state = uiState(arrival = welcome)
        show()

        composeRule.onNodeWithTag(ArrivalToggleTag).performClick()

        composeRule.onNodeWithText("Welcome to Morocco").assertIsDisplayed()
        composeRule.onAllNodesWithTag(arrivalRowTag("emergency")).assertCountEquals(0)
    }

    @Test
    fun theLocationAndCompanionAreStillReachableAfterTheGlance() {
        state = uiState()
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = state,
                    onRefresh = {},
                    hasLocationPermission = false,
                    companionOverview = { Text("Companion overview") },
                )
            }
        }

        scrollToTag("dashboard_location_access_card")
        composeRule.onNodeWithTag("dashboard_location_access_card").assertIsDisplayed()
        composeRule.onNodeWithText("Companion overview").assertIsDisplayed()
        assertFalse(state.glance.tiles.isEmpty())
    }

    // endregion
}
