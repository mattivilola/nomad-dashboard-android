package com.iloapps.nomaddashboard

import android.app.Application
import com.iloapps.nomaddashboard.core.common.ApplicationScope
import com.iloapps.nomaddashboard.core.data.widget.WidgetSnapshotPublisher
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class NomadDashboardApplication : Application() {
    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var widgetSnapshotPublisher: Lazy<WidgetSnapshotPublisher>

    override fun onCreate() {
        super.onCreate()
        // The publisher's dependencies are built on the application scope's background dispatcher, so
        // starting the process for a widget or tile broadcast does not build them on the main thread.
        applicationScope.launch { widgetSnapshotPublisher.get().start() }
    }
}
