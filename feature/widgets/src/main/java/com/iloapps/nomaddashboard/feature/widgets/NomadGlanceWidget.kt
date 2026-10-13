package com.iloapps.nomaddashboard.feature.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.iloapps.nomaddashboard.core.travel.widget.WidgetDisplay
import com.iloapps.nomaddashboard.core.travel.widget.WidgetSnapshot
import dagger.hilt.android.EntryPointAccessors
import java.time.Instant

/**
 * The common part of the three widgets: read the snapshot the app saved, decide the words with
 * [WidgetDisplay] at the moment of drawing, and hand both to [Content]. No network, no location: a
 * widget only ever shows what the app has already saved.
 */
internal abstract class NomadGlanceWidget : GlanceAppWidget() {
    /** The small tile, and a wide one for a widget stretched past [WideWidth]. */
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SmallSize, WideSize))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val snapshot = entryPoint.snapshotStore().load()
        val environment = entryPoint.insightsEnvironment()
        val display = WidgetDisplay(zone = environment.zone(), clockTime = environment.formats().clockTime)
        val now = Instant.now()
        provideContent { Content(snapshot, display, now) }
    }

    @Composable
    abstract fun Content(snapshot: WidgetSnapshot?, display: WidgetDisplay, now: Instant)

    @Composable
    fun isWide(): Boolean = LocalSize.current.width >= WideWidth

    private companion object {
        val SmallSize = DpSize(110.dp, 110.dp)
        val WideSize = DpSize(250.dp, 110.dp)
        val WideWidth = 250.dp
    }
}
