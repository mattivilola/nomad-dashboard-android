package com.iloapps.nomaddashboard.feature.widgets

import com.iloapps.nomaddashboard.core.data.widget.WidgetUpdater
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WidgetsModule {
    @Binds
    @Singleton
    abstract fun bindWidgetUpdater(impl: GlanceWidgetUpdater): WidgetUpdater
}
