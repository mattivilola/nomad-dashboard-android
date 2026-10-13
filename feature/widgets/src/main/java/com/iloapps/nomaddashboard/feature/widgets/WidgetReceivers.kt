package com.iloapps.nomaddashboard.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** The base of the three widget receivers: keeps the periodic redraw scheduled while a widget is placed. */
abstract class NomadWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetRefreshScheduler.ensureScheduled(context)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshScheduler.cancelIfNoWidgets(context)
    }
}

class GlanceOverviewWidgetReceiver : NomadWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GlanceOverviewWidget()
}

class SafetyWidgetReceiver : NomadWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SafetyWidget()
}

class DaylightWidgetReceiver : NomadWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DaylightWidget()
}
