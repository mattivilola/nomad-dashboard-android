package com.iloapps.nomaddashboard.feature.dashboard

import com.iloapps.nomaddashboard.core.model.withCardWidthToggled
import com.iloapps.nomaddashboard.core.model.withCardOrder
import com.iloapps.nomaddashboard.core.model.withCardCollapsed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardCardWidthMode
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.model.TravelProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The card chrome of the dashboard: collapsing, the turned-off row, Customize Layout, width packing and
 * the scroll-and-expand request. The screen is driven the way the view model drives it: the callbacks
 * write the settings and the screen reads them back.
 */
class DashboardCardChromeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var settings by mutableStateOf(AppSettings())
    private val collapsedChanges = mutableListOf<Pair<DashboardCardId, Boolean>>()
    private val settingsRequests = mutableListOf<SettingsDestination?>()
    private val reorders = mutableListOf<List<DashboardCardId>>()
    private val profileSelections = mutableListOf<TravelProfile>()
    private var resetCount = 0

    private fun show(
        isWideLayout: Boolean = false,
        scrollTarget: DashboardScrollTarget? = null,
        onScrollTargetHandled: () -> Unit = {},
        modifier: Modifier = Modifier,
    ) {
        composeRule.setContent {
            NomadTheme {
                Box(modifier = modifier) {
                    DashboardScreen(
                        state = DashboardUiState(settings = settings),
                        onRefresh = {},
                        hasLocationPermission = true,
                        onOpenSettings = { settingsRequests += it },
                        layoutActions = DashboardLayoutActions(
                            onCollapsedChange = { card, collapsed ->
                                collapsedChanges += card to collapsed
                                settings = settings.withCardCollapsed(card, collapsed)
                            },
                            onReorder = { order ->
                                reorders += order
                                settings = settings.withCardOrder(order)
                            },
                            onToggleWidth = { card -> settings = settings.withCardWidthToggled(card) },
                            onResetLayout = { resetCount += 1 },
                            onSelectTravelProfile = { profileSelections += it },
                        ),
                        isWideLayout = isWideLayout,
                        scrollTarget = scrollTarget,
                        onScrollTargetHandled = onScrollTargetHandled,
                    )
                }
            }
        }
    }

    /** The list composes only what is near the viewport; bring the tagged item into reach. */
    private fun scrollToTag(tag: String) {
        composeRule.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag(tag))
    }

    /** The collapsed summary inside the Time Tracking card, not the glance's own "Pending" row above it. */
    private val timeTrackingCardSummary =
        hasText("Pending 0m") and hasAnyAncestor(hasTestTag("dashboard_time_tracking_card"))

    private fun openOverflowItem(tag: String) {
        composeRule.onNodeWithTag(DashboardOverflowTag).performClick()
        composeRule.onNodeWithTag(tag).performClick()
    }

    @Test
    fun collapsingACardFoldsItToItsSummaryAndReportsTheChange() {
        settings = AppSettings(
            projectTimeTrackingEnabled = true,
            dashboardCardOrder = listOf(DashboardCardId.TIME_TRACKING),
        )
        show()

        // The glance (with its own "Pending" row) sits above the card, so bring the card into reach.
        scrollToTag("dashboard_time_tracking_card")
        composeRule.onNodeWithText("Resume capture").assertIsDisplayed()
        composeRule.onNodeWithTag(dashboardCardToggleTag(DashboardCardId.TIME_TRACKING)).performClick()

        assertEquals(listOf(DashboardCardId.TIME_TRACKING to true), collapsedChanges)
        composeRule.onNode(timeTrackingCardSummary).assertIsDisplayed()
        composeRule.onNodeWithText("Resume capture").assertDoesNotExist()
    }

    @Test
    fun expandingACollapsedCardShowsItsContentAndReportsTheChange() {
        settings = AppSettings(
            projectTimeTrackingEnabled = true,
            dashboardCardOrder = listOf(DashboardCardId.TIME_TRACKING),
            dashboardCollapsedCards = setOf(DashboardCardId.TIME_TRACKING),
        )
        show()

        scrollToTag("dashboard_time_tracking_card")
        composeRule.onNode(timeTrackingCardSummary).assertIsDisplayed()
        composeRule.onNodeWithText("Resume capture").assertDoesNotExist()
        composeRule.onNodeWithTag(dashboardCardToggleTag(DashboardCardId.TIME_TRACKING)).performClick()

        assertEquals(listOf(DashboardCardId.TIME_TRACKING to false), collapsedChanges)
        // The card grew below the viewport: the folded card sat at the end of the list.
        scrollToTag("dashboard_time_tracking_card")
        composeRule.onNodeWithText("Resume capture").assertIsDisplayed()
    }

    @Test
    fun turnedOffRowNamesTheCardsAndOpensTheFirstOnesSettingsGroup() {
        settings = AppSettings(
            localInfoEnabled = false,
            fuelPricesEnabled = false,
            dashboardCardOrder = listOf(
                DashboardCardId.CONNECTIVITY,
                DashboardCardId.LOCAL_INFO,
                DashboardCardId.FUEL_PRICES,
            ),
        )
        show()

        composeRule.onNodeWithTag(LocalInfoCardTag).assertDoesNotExist()
        scrollToTag("dashboard_turned_off_row")
        composeRule.onNodeWithText("Turned off: Local Info, Fuel Prices").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_turned_off_settings").performClick()

        assertEquals(listOf<SettingsDestination?>(SettingsDestination.LOCAL_INFO), settingsRequests)
    }

    @Test
    fun noTurnedOffRowWhileEveryFeatureIsOn() {
        settings = AppSettings(
            localInfoEnabled = true,
            dashboardCardOrder = listOf(DashboardCardId.CONNECTIVITY, DashboardCardId.LOCAL_INFO),
        )
        show()

        composeRule.onAllNodesWithTag("dashboard_turned_off_row").assertCountEquals(0)
    }

    @Test
    fun customizeLayoutShowsEveryCardIncludingTurnedOffOnesWithTheirControls() {
        show()

        composeRule.onAllNodesWithTag(customizeHandleTag(DashboardCardId.WEATHER)).assertCountEquals(0)
        openOverflowItem(DashboardCustomizeToggleTag)

        composeRule.onAllNodesWithTag("dashboard_turned_off_row").assertCountEquals(0)
        DashboardCardId.entries.forEach { card ->
            composeRule.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag(customizeHandleTag(card)))
            composeRule.onNodeWithTag(customizeHandleTag(card)).assertIsDisplayed()
            composeRule.onNodeWithTag(customizeWidthTag(card)).assertIsDisplayed()
            composeRule.onNodeWithTag(customizeMoveUpTag(card)).assertExists()
            composeRule.onNodeWithTag(customizeMoveDownTag(card)).assertExists()
        }
    }

    @Test
    fun movingACardDownReportsTheNewOrder() {
        show()
        openOverflowItem(DashboardCustomizeToggleTag)

        composeRule.onNodeWithTag(customizeMoveDownTag(DashboardCardId.CONNECTIVITY)).performClick()

        val order = reorders.single()
        assertEquals(DashboardCardId.POWER, order[0])
        assertEquals(DashboardCardId.CONNECTIVITY, order[1])
        assertEquals(DashboardCardId.entries.toSet(), order.toSet())
    }

    @Test
    fun longPressAndDragOnTheHandleReordersCardsAndSavesOnRelease() {
        settings = AppSettings(dashboardCardOrder = listOf(DashboardCardId.EMBASSY, DashboardCardId.STAYS))
        show()
        openOverflowItem(DashboardCustomizeToggleTag)

        composeRule.onNodeWithTag(customizeHandleTag(DashboardCardId.EMBASSY)).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(14) { moveBy(Offset(0f, 50f), delayMillis = 16) }
            assertTrue("Nothing should be saved while the card is still held.", reorders.isEmpty())
            up()
        }

        composeRule.waitForIdle()
        val order = reorders.last()
        assertEquals(listOf(DashboardCardId.STAYS, DashboardCardId.EMBASSY), order.take(2))
        assertEquals(DashboardCardId.entries.toSet(), order.toSet())
    }

    @Test
    fun widthToggleAndDoneWorkInCustomizeLayout() {
        show()
        openOverflowItem(DashboardCustomizeToggleTag)

        scrollToTag(customizeWidthTag(DashboardCardId.POWER))
        composeRule.onNodeWithTag(customizeWidthTag(DashboardCardId.POWER)).performClick()
        assertEquals(DashboardCardWidthMode.NARROW, settings.dashboardCardWidthModes[DashboardCardId.POWER])
        composeRule.onNodeWithText("Half width").assertExists()

        scrollToTag("dashboard_customize_done")
        composeRule.onNodeWithTag("dashboard_customize_done").performClick()
        composeRule.onAllNodesWithTag(customizeHandleTag(DashboardCardId.POWER)).assertCountEquals(0)
    }

    @Test
    fun overflowMenuResetsTheLayoutAndSwitchesTheTravelProfile() {
        show()

        openOverflowItem(DashboardResetLayoutTag)
        assertEquals(1, resetCount)

        openOverflowItem(dashboardProfileTag(TravelProfile.ROAD_TRIP))
        assertEquals(listOf(TravelProfile.ROAD_TRIP), profileSelections)
    }

    @Test
    fun scrollTargetExpandsACollapsedCardBeforeScrollingToIt() {
        settings = AppSettings(dashboardCollapsedCards = setOf(DashboardCardId.WEATHER))
        var handled = false
        show(
            scrollTarget = DashboardScrollTarget(DashboardCardId.WEATHER),
            onScrollTargetHandled = { handled = true },
            modifier = Modifier.height(400.dp),
        )

        composeRule.waitForIdle()

        assertEquals(listOf(DashboardCardId.WEATHER to false), collapsedChanges)
        assertTrue("The scroll request should be reported as handled.", handled)
        composeRule.onNodeWithTag("dashboard_weather_card").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Collapse Weather").assertExists()
    }

    @Test
    fun twoAdjacentHalfWidthCardsShareARowOnAWideLayout() {
        settings = AppSettings(
            dashboardCardOrder = listOf(DashboardCardId.POWER, DashboardCardId.WEATHER),
            dashboardCardWidthModes = mapOf(
                DashboardCardId.POWER to DashboardCardWidthMode.NARROW,
                DashboardCardId.WEATHER to DashboardCardWidthMode.NARROW,
            ),
        )
        show(isWideLayout = true, modifier = Modifier.width(800.dp))

        val power = composeRule.onNodeWithTag("dashboard_power_card").fetchSemanticsNode().boundsInRoot
        val weather = composeRule.onNodeWithTag("dashboard_weather_card").fetchSemanticsNode().boundsInRoot
        val list = composeRule.onNodeWithTag("dashboard_list").fetchSemanticsNode().boundsInRoot

        assertEquals(power.top, weather.top, 1f)
        assertTrue("Half-width cards should be about half the list width.", power.width < list.width * 0.6f)
        assertTrue("Half-width cards should be about half the list width.", weather.width < list.width * 0.6f)
    }

    @Test
    fun aLoneHalfWidthCardRendersAtFullWidth() {
        settings = AppSettings(
            dashboardCardOrder = listOf(DashboardCardId.POWER),
            dashboardCardWidthModes = mapOf(DashboardCardId.POWER to DashboardCardWidthMode.NARROW),
        )
        show(isWideLayout = true, modifier = Modifier.width(800.dp))

        val power = composeRule.onNodeWithTag("dashboard_power_card").fetchSemanticsNode().boundsInRoot
        val list = composeRule.onNodeWithTag("dashboard_list").fetchSemanticsNode().boundsInRoot

        assertEquals(list.width, power.width, 1f)
    }

    @Test
    fun aPhoneKeepsOneColumnEvenForHalfWidthCards() {
        settings = AppSettings(
            dashboardCardOrder = listOf(DashboardCardId.POWER, DashboardCardId.WEATHER),
            dashboardCardWidthModes = mapOf(
                DashboardCardId.POWER to DashboardCardWidthMode.NARROW,
                DashboardCardId.WEATHER to DashboardCardWidthMode.NARROW,
            ),
        )
        show(isWideLayout = false, modifier = Modifier.width(400.dp))

        val power = composeRule.onNodeWithTag("dashboard_power_card").fetchSemanticsNode().boundsInRoot
        val list = composeRule.onNodeWithTag("dashboard_list").fetchSemanticsNode().boundsInRoot

        assertEquals("A phone keeps one column, so the card spans the list.", list.width, power.width, 1f)
    }
}
