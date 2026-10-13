package com.iloapps.nomaddashboard.core.data.insights

import com.iloapps.nomaddashboard.core.common.ApplicationScope
import com.iloapps.nomaddashboard.core.common.DefaultDispatcher
import com.iloapps.nomaddashboard.core.data.companion.CompanionRepository
import com.iloapps.nomaddashboard.core.data.repository.NomadDashboardRepository
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.travel.arrival.ArrivalBriefingBuilder
import com.iloapps.nomaddashboard.core.travel.arrival.ArrivalDetector
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpEngine
import com.iloapps.nomaddashboard.core.travel.health.DashboardHealthEvaluator
import com.iloapps.nomaddashboard.core.travel.safety.CurrentCountry
import com.iloapps.nomaddashboard.core.travel.status.StatusLinePresentation
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

/**
 * The single source of derived dashboard state shared by the dashboard screen, the Quick Settings tile
 * and the widgets: heads-up items, health, the status line and the arrival event.
 */
interface DashboardInsightsRepository {
    /**
     * Re-emits whenever the snapshot, settings, country days or home time zone change, and at least
     * every 60 s. Collectors share one evaluation; it is idle when nobody collects.
     */
    val insights: Flow<DashboardInsights>

    /** Marks the arrival briefing for [countryCode] as seen so it is not offered again until the next border. */
    suspend fun acknowledgeArrival(countryCode: String)

    /**
     * Takes a latency reading if the last one is older than [maximumAge], so a surface shown while the
     * dashboard is closed reflects the connection now. See [NomadDashboardRepository.probeLatencyIfStale].
     */
    suspend fun probeLatencyIfStale(maximumAge: Duration = Duration.ofSeconds(60))
}

@Singleton
class DefaultDashboardInsightsRepository @Inject constructor(
    private val dashboard: NomadDashboardRepository,
    private val companion: CompanionRepository,
    private val headsUpEngine: HeadsUpEngine,
    private val environment: InsightsEnvironment,
    private val ticker: InsightsTicker,
    private val clock: Clock,
    @DefaultDispatcher private val computeDispatcher: CoroutineDispatcher,
    @ApplicationScope scope: CoroutineScope,
) : DashboardInsightsRepository {
    override val insights: Flow<DashboardInsights> = combine(
        dashboard.snapshot,
        dashboard.settings,
        dashboard.visitedCountryDays,
        companion.data.map { it.preferences.homeTimeZoneId.trim().takeIf(String::isNotEmpty) }.distinctUntilChanged(),
        ticker.ticks(),
    ) { snapshot, settings, countryDays, homeTimeZoneId, _ ->
        evaluate(snapshot, settings, countryDays, homeTimeZoneId)
    }
        .flowOn(computeDispatcher)
        .shareIn(scope, SharingStarted.WhileSubscribed(StopTimeoutMillis), replay = 1)

    override suspend fun acknowledgeArrival(countryCode: String) {
        dashboard.updateSettings { ArrivalBriefingBuilder.acknowledged(it, countryCode, clock.instant()) }
    }

    override suspend fun probeLatencyIfStale(maximumAge: Duration) {
        dashboard.probeLatencyIfStale(maximumAge)
    }

    private fun evaluate(
        snapshot: DashboardSnapshot,
        settings: AppSettings,
        countryDays: List<VisitedCountryDay>,
        homeTimeZoneId: String?,
    ): DashboardInsights {
        val now = clock.instant()
        val zone = environment.zone()
        // A latency reading that old says nothing about the connection now.
        val current = snapshot.copy(connectivity = snapshot.connectivity.withoutStaleLatency(now))

        val headsUp = headsUpEngine.evaluate(current, settings, countryDays, now, zone, environment.formats())
        val travelContext = current.travelContext
        val currentCountry = CurrentCountry.code(
            deviceCountryCode = travelContext.deviceCountryCode,
            ipCountryCode = travelContext.countryCode,
            localInfoCountryCode = current.localInfo.countryCode,
        )
        return DashboardInsights(
            snapshot = current,
            headsUp = headsUp,
            health = DashboardHealthEvaluator.evaluate(current.connectivity, current.power),
            statusLine = StatusLinePresentation.of(current, settings, headsUp.firstOrNull()),
            arrival = ArrivalDetector.arrival(countryDays, currentCountry, settings, now, zone),
            homeTimeZoneId = homeTimeZoneId,
            zone = zone,
            evaluatedAt = now,
        )
    }

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}
