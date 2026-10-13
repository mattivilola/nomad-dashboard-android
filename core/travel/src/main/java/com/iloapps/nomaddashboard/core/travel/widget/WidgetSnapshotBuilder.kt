package com.iloapps.nomaddashboard.core.travel.widget

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.travel.answers.NearestEmbassyAnswer
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswers
import com.iloapps.nomaddashboard.core.travel.answers.SnapshotDaylight
import com.iloapps.nomaddashboard.core.travel.answers.overallHealth
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpEngine
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpFormats
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpItem
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpKind
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpSeverity
import java.time.Instant
import java.time.ZoneId

/**
 * Builds the [WidgetSnapshot] from the same inputs the dashboard uses. The place, emergency number and
 * embassy come from [QuickAnswers], so a widget and a spoken answer can never disagree.
 *
 * Pure: the clock and the zone are injected, so it can be rebuilt after every refresh and tested
 * without a device. Pair it with [WidgetSnapshotPublishPolicy] to decide when to write the result.
 */
class WidgetSnapshotBuilder(
    private val answers: QuickAnswers = QuickAnswers(),
) {
    /**
     * @param headsUp the ranked heads-up items for the selected travel profile, best first; evaluate
     *   them with [WidgetSnapshot.HEADS_UP_FETCH_LIMIT] as the limit. The daylight heads-up counts down
     *   by the minute and has its own widget, so it never takes a heads-up slot here (and never makes
     *   the snapshot change every minute).
     * @param zone the device's time zone, for a device location that has none of its own.
     */
    fun build(
        snapshot: DashboardSnapshot,
        settings: AppSettings,
        headsUp: List<HeadsUpItem>,
        now: Instant,
        zone: ZoneId,
    ): WidgetSnapshot {
        val overall = overallHealth(snapshot)

        val widgetHeadsUps = headsUp
            .filter { it.kind != HeadsUpKind.DAYLIGHT }
            .map { WidgetSnapshot.HeadsUp(title = it.title, kind = it.kind.name, severity = severity(it.severity)) }

        val emergencyAnswer = answers.emergencyNumbers(snapshot)
        val emergency = emergencyAnswer.numbers?.let { numbers ->
            emergencyAnswer.countryName?.let { WidgetSnapshot.Emergency(countryName = it, generalNumber = numbers.general) }
        }

        val embassyAnswer = answers.nearestEmbassy(snapshot, settings)
        val embassy = if (
            embassyAnswer.state == NearestEmbassyAnswer.State.CITIZENSHIP_NEEDED ||
            (embassyAnswer.mission == null && embassyAnswer.hotline == null)
        ) {
            null
        } else {
            WidgetSnapshot.Embassy(name = embassyAnswer.mission?.name, hotline = embassyAnswer.hotline?.phone)
        }

        return WidgetSnapshot.of(
            verdict = WidgetSnapshot.Verdict(title = overall.label, level = overall.level),
            placeName = answers.placeName(snapshot),
            headsUp = widgetHeadsUps,
            emergency = emergency,
            embassy = embassy,
            daylight = SnapshotDaylight.outlook(snapshot, now, zone)?.let(WidgetDaylight::of),
            savedAt = now,
        )
    }

    /**
     * The same, evaluating the heads-ups itself: [countryDays] feeds the stay, Schengen and
     * tax-residence items, which reach the widgets like any other heads-up.
     */
    fun build(
        snapshot: DashboardSnapshot,
        settings: AppSettings,
        countryDays: List<VisitedCountryDay>,
        headsUpEngine: HeadsUpEngine,
        now: Instant,
        zone: ZoneId,
        formats: HeadsUpFormats = HeadsUpFormats(locale = answers.locale),
    ): WidgetSnapshot = build(
        snapshot,
        settings,
        headsUpEngine.evaluate(
            snapshot, settings, countryDays, now, zone, formats, limit = WidgetSnapshot.HEADS_UP_FETCH_LIMIT,
        ),
        now,
        zone,
    )

    private fun severity(severity: HeadsUpSeverity): WidgetSnapshot.Severity =
        when (severity) {
            HeadsUpSeverity.INFO -> WidgetSnapshot.Severity.INFO
            HeadsUpSeverity.CAUTION -> WidgetSnapshot.Severity.CAUTION
            HeadsUpSeverity.ATTENTION -> WidgetSnapshot.Severity.ATTENTION
        }
}
