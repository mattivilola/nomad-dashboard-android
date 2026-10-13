package com.iloapps.nomaddashboard.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException

/** The three home screen widgets. */
internal object NomadWidgets {
    private val receivers = listOf(
        GlanceOverviewWidgetReceiver::class.java,
        SafetyWidgetReceiver::class.java,
        DaylightWidgetReceiver::class.java,
    )

    /** Whether the user has placed at least one of the widgets. */
    fun hasPlacedWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return receivers.any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    }

    /**
     * Draws every placed widget again from the saved snapshot. Cheap when none is placed. A widget that
     * fails to draw never stops the others, nor the caller.
     */
    suspend fun updateAll(context: Context) {
        listOf(GlanceOverviewWidget(), SafetyWidget(), DaylightWidget()).forEach { widget ->
            try {
                widget.updateAll(context)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The next scheduled or published update draws it again.
            }
        }
    }
}
