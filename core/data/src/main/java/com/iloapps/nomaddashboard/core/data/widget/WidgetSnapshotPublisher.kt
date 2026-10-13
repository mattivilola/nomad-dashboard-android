package com.iloapps.nomaddashboard.core.data.widget

import com.iloapps.nomaddashboard.core.common.ApplicationScope
import com.iloapps.nomaddashboard.core.data.insights.DashboardInsights
import com.iloapps.nomaddashboard.core.data.insights.DashboardInsightsRepository
import com.iloapps.nomaddashboard.core.data.repository.NomadDashboardRepository
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswers
import com.iloapps.nomaddashboard.core.travel.widget.WidgetSnapshot
import com.iloapps.nomaddashboard.core.travel.widget.WidgetSnapshotBuilder
import com.iloapps.nomaddashboard.core.travel.widget.WidgetSnapshotPublishPolicy
import java.io.IOException
import java.time.Clock
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * Keeps the widgets up to date, the Android counterpart of the macOS `WidgetSnapshotPublisher`: after
 * the dashboard insights change it builds the compact [WidgetSnapshot], saves it when
 * [WidgetSnapshotPublishPolicy] says it is worth saving (the shown content changed, or the heartbeat is
 * due) and asks the widgets to redraw.
 *
 * Nothing leaves the device: the snapshot is a file in the app's private storage and holds no IP
 * address, no coordinates and no citizenship codes beyond what the widgets show.
 */
@Singleton
class WidgetSnapshotPublisher @Inject constructor(
    private val insights: DashboardInsightsRepository,
    private val dashboard: NomadDashboardRepository,
    private val store: WidgetSnapshotStore,
    private val updater: WidgetUpdater,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)
    private var lastWritten: WidgetSnapshot? = null
    private var builder: Pair<Locale, WidgetSnapshotBuilder>? = null

    /** Starts publishing for the life of the process. Calling it again does nothing. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            lastWritten = store.load()
            combine(insights.insights, dashboard.settings) { current, settings -> current to settings }
                .conflate()
                .collect { (current, settings) ->
                    publish(current, settings)
                    // The dashboard snapshot changes every few seconds; the widgets only need a look this often.
                    delay(EvaluationIntervalMillis)
                }
        }
    }

    /**
     * Builds the snapshot for [current] and saves it when the policy allows.
     *
     * @return whether a new snapshot was saved and the widgets were asked to redraw.
     */
    internal suspend fun publish(current: DashboardInsights, settings: AppSettings): Boolean {
        // Until the first local readings arrive the dashboard is a placeholder, not something to show.
        if (current.snapshot.localSignalsAt == null) return false

        val candidate = builderFor(Locale.getDefault()).build(
            snapshot = current.snapshot,
            settings = settings,
            headsUp = current.headsUp,
            now = clock.instant(),
            zone = current.zone,
        )
        if (!WidgetSnapshotPublishPolicy.shouldPublish(candidate, lastWritten)) return false

        try {
            store.save(candidate)
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            // The next evaluation tries again; the previous file is intact.
            return false
        }
        lastWritten = candidate
        updater.updateAll()
        return true
    }

    private fun builderFor(locale: Locale): WidgetSnapshotBuilder {
        builder?.takeIf { it.first == locale }?.let { return it.second }
        return WidgetSnapshotBuilder(QuickAnswers(locale)).also { builder = locale to it }
    }

    private companion object {
        const val EvaluationIntervalMillis = 5_000L
    }
}
