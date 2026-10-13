package com.iloapps.nomaddashboard.feature.widgets.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.iloapps.nomaddashboard.core.data.insights.DashboardInsights
import com.iloapps.nomaddashboard.core.data.insights.DashboardInsightsRepository
import com.iloapps.nomaddashboard.core.data.repository.NomadDashboardRepository
import com.iloapps.nomaddashboard.feature.widgets.R
import com.iloapps.nomaddashboard.feature.widgets.WidgetIntents
import com.iloapps.nomaddashboard.feature.widgets.WidgetLinks
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Quick Settings tile: the Android counterpart of the macOS menu bar text. Its subtitle is the
 * one-line status ([TileSubtitle]), or the app name when nothing applies; tapping it opens the dashboard.
 *
 * It runs only while the Quick Settings panel is open and uses the app's local state: a cheap local
 * refresh of battery and connection when the state is old, and at most one short latency probe. No
 * background service, no network beyond that probe.
 */
@AndroidEntryPoint
class NomadStatusTileService : TileService() {
    @Inject
    lateinit var insights: DashboardInsightsRepository

    @Inject
    lateinit var dashboard: NomadDashboardRepository

    @Inject
    lateinit var clock: Clock

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        refreshJob?.cancel()
        refreshJob = scope.launch { refresh() }
    }

    override fun onStopListening() {
        refreshJob?.cancel()
        refreshJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = WidgetIntents.view(this, WidgetLinks.dashboard)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private suspend fun refresh() {
        val statusLine = try {
            withTimeoutOrNull(RefreshTimeout.toMillis()) { latestInsights() }?.statusLine
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        render(TileSubtitle.of(statusLine, appName()))
    }

    /** The insights of right now: local readings are refreshed when old, and latency measured when stale. */
    private suspend fun latestInsights(): DashboardInsights {
        val current = dashboard.snapshot.value
        val readingAge = current.localSignalsAt?.let { Duration.between(it, clock.instant()) }
        if (current.localSignalsAt == null && current.lastRefresh == null) {
            // A cold process: restore what the app last saved before reading the connection.
            dashboard.warmStart()
        }
        if (readingAge == null || readingAge > LocalReadingMaximumAge) {
            dashboard.refreshLocalSignals()
        }
        // Skipped quietly when the reading is fresh, when offline, or when the probe takes too long.
        withTimeoutOrNull(ProbeTimeout.toMillis()) { insights.probeLatencyIfStale() }
        // The shared flow replays its last value, which can be hours old: wait for one made after the readings above.
        val readingsTakenAt = clock.instant()
        return insights.insights.first { it.evaluatedAt >= readingsTakenAt }
    }

    private fun render(subtitle: String) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_label)
        tile.subtitle = subtitle
        tile.icon = Icon.createWithResource(this, R.drawable.ic_widget_tile_dashboard)
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()
    }

    private fun appName(): String = applicationInfo.loadLabel(packageManager).toString()

    private companion object {
        /** A reading older than this is taken again before the tile shows it. */
        val LocalReadingMaximumAge: Duration = Duration.ofSeconds(60)

        /** The latency probe may take at most this long; the tile then shows what it already knows. */
        val ProbeTimeout: Duration = Duration.ofSeconds(2)

        /** The whole refresh gives up after this and shows the app name. */
        val RefreshTimeout: Duration = Duration.ofSeconds(6)
    }
}
