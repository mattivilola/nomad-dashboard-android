package com.iloapps.nomaddashboard.feature.widgets

import android.content.Context
import com.iloapps.nomaddashboard.core.data.widget.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Redraws the Glance widgets when the data layer has saved a new snapshot. */
@Singleton
internal class GlanceWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetUpdater {
    override suspend fun updateAll() = NomadWidgets.updateAll(context)
}
