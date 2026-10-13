package com.iloapps.nomaddashboard.core.travel.glance

import com.iloapps.nomaddashboard.core.model.TimeTrackingOtherProjectId
import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
import com.iloapps.nomaddashboard.core.travel.summary.DashboardCardSummaryBuilder
import java.time.Duration
import java.util.UUID

/**
 * The compact time-tracking row under the dashboard header, shown only while time tracking is on: a
 * status dot with the pending time, one pause or resume control, the interruption counter and an
 * Allocate menu. Project names live in the menu so they cannot be cut off.
 *
 * Ported from the macOS `TimeTrackingQuickActionsPresentation` header pill. Android pauses and resumes
 * capture with one control (there is no separate stop), so the pill has a single primary control.
 */
data class GlanceTimeTracking(
    /** "Pending 6h 58m". */
    val pendingText: String,
    val activity: Activity,
    val primaryControl: PrimaryControl,
    val interruptionCount: Int,
    /** Up to [MAX_ALLOCATE_PROJECTS] projects, then Other. */
    val allocateEntries: List<AllocateEntry>,
    /** Whether there is captured time to allocate; the menu entries are disabled otherwise. */
    val canAllocate: Boolean,
) {
    /** The status dot: teal while running, sand while paused with time waiting, neutral otherwise. */
    enum class Activity(val title: String) {
        RUNNING("Running"),
        PAUSED("Paused"),
        STOPPED("Stopped"),
    }

    /** What the one control does. Pausing is `stopTracking` and resuming is `startTracking` in the repository. */
    enum class PrimaryControl(val title: String, val startsTracking: Boolean) {
        PAUSE("Pause", startsTracking = false),
        RESUME("Resume", startsTracking = true),
        PLAY("Play", startsTracking = true),
    }

    data class AllocateEntry(
        val projectId: UUID,
        val title: String,
        val isOther: Boolean,
    )

    val accessibilityLabel: String
        get() = "${activity.title}, ${pendingText.lowercase()}"

    companion object {
        const val MAX_ALLOCATE_PROJECTS = 5
        const val OTHER_TITLE = "Other"

        /**
         * @param projects the projects in the order the menu should offer them; archived ones and the
         * built-in Other project are skipped (Other is always the last entry).
         * @param isRunning whether capture is on right now.
         * @param hasPendingEntries whether closed segments are waiting for allocation.
         * @param pending the captured time waiting for allocation, including the running segment.
         */
        fun of(
            projects: List<TimeTrackingProject>,
            isRunning: Boolean,
            hasPendingEntries: Boolean,
            pending: Duration,
            interruptionCount: Int,
        ): GlanceTimeTracking {
            val activity = when {
                isRunning -> Activity.RUNNING
                hasPendingEntries -> Activity.PAUSED
                else -> Activity.STOPPED
            }
            val primary = when (activity) {
                Activity.RUNNING -> PrimaryControl.PAUSE
                Activity.PAUSED -> PrimaryControl.RESUME
                Activity.STOPPED -> PrimaryControl.PLAY
            }
            val entries = projects
                .filter { !it.isArchived && it.id != TimeTrackingOtherProjectId && it.name.isNotBlank() }
                .take(MAX_ALLOCATE_PROJECTS)
                .map { AllocateEntry(it.id, it.name.trim(), isOther = false) } +
                AllocateEntry(TimeTrackingOtherProjectId, OTHER_TITLE, isOther = true)

            return GlanceTimeTracking(
                pendingText = "Pending ${DashboardCardSummaryBuilder.durationText(pending)}",
                activity = activity,
                primaryControl = primary,
                interruptionCount = interruptionCount,
                allocateEntries = entries,
                canAllocate = isRunning || pending.seconds > 0,
            )
        }
    }
}
