package com.iloapps.nomaddashboard.core.data.widget

/**
 * Redraws the home screen widgets from the saved snapshot. Implemented by the widget module, which
 * owns the Glance widgets; the data layer only asks for a redraw after it has saved a new snapshot.
 */
interface WidgetUpdater {
    suspend fun updateAll()
}
