package com.iloapps.nomaddashboard.core.travel.glance

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.TimeTrackingOtherProjectId
import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
import com.iloapps.nomaddashboard.core.travel.glance.GlanceTimeTracking.Activity
import com.iloapps.nomaddashboard.core.travel.glance.GlanceTimeTracking.PrimaryControl
import java.time.Duration
import org.junit.Test

class GlanceTimeTrackingTest {
    private fun tracking(
        projects: List<TimeTrackingProject> = emptyList(),
        isRunning: Boolean = false,
        hasPendingEntries: Boolean = false,
        pending: Duration = Duration.ZERO,
        interruptions: Int = 0,
    ) = GlanceTimeTracking.of(projects, isRunning, hasPendingEntries, pending, interruptions)

    @Test
    fun `pending time is written like the card summary`() {
        assertThat(tracking(pending = Duration.ofMinutes(418)).pendingText).isEqualTo("Pending 6h 58m")
        assertThat(tracking(pending = Duration.ofMinutes(42)).pendingText).isEqualTo("Pending 42m")
        assertThat(tracking().pendingText).isEqualTo("Pending 0m")
    }

    @Test
    fun `a running capture offers pause and a green dot`() {
        val state = tracking(isRunning = true, pending = Duration.ofMinutes(5))

        assertThat(state.activity).isEqualTo(Activity.RUNNING)
        assertThat(state.primaryControl).isEqualTo(PrimaryControl.PAUSE)
        assertThat(state.primaryControl.startsTracking).isFalse()
    }

    @Test
    fun `paused capture with waiting time offers resume`() {
        val state = tracking(hasPendingEntries = true, pending = Duration.ofMinutes(30))

        assertThat(state.activity).isEqualTo(Activity.PAUSED)
        assertThat(state.primaryControl).isEqualTo(PrimaryControl.RESUME)
        assertThat(state.primaryControl.startsTracking).isTrue()
    }

    @Test
    fun `an idle capture offers play`() {
        val state = tracking()

        assertThat(state.activity).isEqualTo(Activity.STOPPED)
        assertThat(state.primaryControl).isEqualTo(PrimaryControl.PLAY)
        assertThat(state.canAllocate).isFalse()
    }

    @Test
    fun `allocation is possible while running or with captured time`() {
        assertThat(tracking(isRunning = true).canAllocate).isTrue()
        assertThat(tracking(hasPendingEntries = true, pending = Duration.ofMinutes(1)).canAllocate).isTrue()
        assertThat(tracking(hasPendingEntries = true, pending = Duration.ZERO).canAllocate).isFalse()
    }

    @Test
    fun `the allocate menu lists up to five projects then other and keeps full names`() {
        val projects = (1..7).map { TimeTrackingProject(name = "Customer project number $it") } +
            TimeTrackingProject(id = TimeTrackingOtherProjectId, name = "Other")

        val entries = tracking(projects).allocateEntries

        assertThat(entries).hasSize(6)
        assertThat(entries.map { it.title }.take(5))
            .containsExactly(
                "Customer project number 1",
                "Customer project number 2",
                "Customer project number 3",
                "Customer project number 4",
                "Customer project number 5",
            ).inOrder()
        assertThat(entries.last().title).isEqualTo("Other")
        assertThat(entries.last().projectId).isEqualTo(TimeTrackingOtherProjectId)
        assertThat(entries.last().isOther).isTrue()
        assertThat(entries.dropLast(1).none { it.isOther }).isTrue()
    }

    @Test
    fun `archived and blank projects are not offered and other is always present`() {
        val projects = listOf(
            TimeTrackingProject(name = "Old", isArchived = true),
            TimeTrackingProject(name = "  "),
            TimeTrackingProject(name = " Acme "),
        )

        assertThat(tracking(projects).allocateEntries.map { it.title }).containsExactly("Acme", "Other").inOrder()
        assertThat(tracking().allocateEntries.map { it.title }).containsExactly("Other")
    }

    @Test
    fun `the interruption count passes through and the label names the state`() {
        val state = tracking(isRunning = true, pending = Duration.ofMinutes(418), interruptions = 3)

        assertThat(state.interruptionCount).isEqualTo(3)
        assertThat(state.accessibilityLabel).isEqualTo("Running, pending 6h 58m")
    }
}
