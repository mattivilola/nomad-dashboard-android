package com.iloapps.nomaddashboard.core.travel.widget

import java.time.Duration

/**
 * When the app should write a new widget snapshot and refresh the widgets.
 *
 * The dashboard snapshot changes every few seconds but the widgets show much less, so a write only
 * happens when the shown content changed (at most once a minute) or, with nothing changed, as a
 * heartbeat that keeps "Updated 12:40" and the 6 hour out-of-date state honest.
 */
object WidgetSnapshotPublishPolicy {
    val MINIMUM_INTERVAL: Duration = Duration.ofSeconds(60)
    val HEARTBEAT_INTERVAL: Duration = Duration.ofMinutes(30)

    fun shouldPublish(candidate: WidgetSnapshot, previous: WidgetSnapshot?): Boolean {
        if (previous == null) return true

        val elapsed = Duration.between(previous.savedAt, candidate.savedAt)
        // A clock that moved backwards cannot be throttled against.
        if (elapsed.isNegative) return true

        return if (candidate.hasSameContent(previous)) {
            elapsed >= HEARTBEAT_INTERVAL
        } else {
            elapsed >= MINIMUM_INTERVAL
        }
    }
}
