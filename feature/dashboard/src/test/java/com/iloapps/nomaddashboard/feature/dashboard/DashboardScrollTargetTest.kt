package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DashboardScrollTargetTest {
    private val card = DashboardCardId.WEATHER

    @Test
    fun anExpandedCardIsScrolledToWithoutExpandingIt() = runTest {
        val events = mutableListOf<String>()

        revealDashboardCard(
            card = card,
            isCollapsed = { false },
            expand = { events += "expand" },
            indexOfCard = { 7 },
            scrollToItem = { events += "scroll $it" },
        )

        assertThat(events).containsExactly("scroll 7")
    }

    @Test
    fun aCollapsedCardIsExpandedBeforeTheListScrolls() = runTest {
        val collapsed = mutableStateOf(true)
        val events = mutableListOf<String>()

        revealDashboardCard(
            card = card,
            isCollapsed = { collapsed.value },
            expand = {
                events += "expand"
                collapsed.value = false
                Snapshot.sendApplyNotifications()
            },
            indexOfCard = {
                events += "index"
                7
            },
            scrollToItem = { events += "scroll $it" },
        )

        assertThat(events).containsExactly("expand", "index", "scroll 7").inOrder()
    }

    @Test
    fun scrollingWaitsForTheSavedExpansionToLand() = runTest {
        val collapsed = mutableStateOf(true)
        val events = mutableListOf<String>()

        revealDashboardCard(
            card = card,
            isCollapsed = { collapsed.value },
            expand = {
                events += "expand"
                // The setting is saved asynchronously; it lands a little later.
                launch {
                    delay(200)
                    events += "saved"
                    collapsed.value = false
                    Snapshot.sendApplyNotifications()
                }
            },
            indexOfCard = { 4 },
            scrollToItem = { events += "scroll $it" },
        )

        assertThat(events).containsExactly("expand", "saved", "scroll 4").inOrder()
    }

    @Test
    fun scrollsAnywayWhenTheExpansionNeverLands() = runTest {
        val events = mutableListOf<String>()

        revealDashboardCard(
            card = card,
            isCollapsed = { true },
            expand = { events += "expand" },
            indexOfCard = { 2 },
            scrollToItem = { events += "scroll $it" },
            expandTimeoutMillis = 100,
        )

        assertThat(events).containsExactly("expand", "scroll 2").inOrder()
    }

    @Test
    fun aCardThatIsNotOnTheDashboardIsNotScrolledTo() = runTest {
        val events = mutableListOf<String>()

        revealDashboardCard(
            card = card,
            isCollapsed = { false },
            expand = { events += "expand" },
            indexOfCard = { -1 },
            scrollToItem = { events += "scroll $it" },
        )

        assertThat(events).isEmpty()
    }
}
