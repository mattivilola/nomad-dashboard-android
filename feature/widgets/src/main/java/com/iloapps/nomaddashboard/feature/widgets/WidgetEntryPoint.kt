package com.iloapps.nomaddashboard.feature.widgets

import com.iloapps.nomaddashboard.core.data.insights.InsightsEnvironment
import com.iloapps.nomaddashboard.core.data.widget.WidgetSnapshotStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** What a widget needs from the app's dependency graph; Glance widgets are not created by Hilt. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun snapshotStore(): WidgetSnapshotStore

    fun insightsEnvironment(): InsightsEnvironment
}
