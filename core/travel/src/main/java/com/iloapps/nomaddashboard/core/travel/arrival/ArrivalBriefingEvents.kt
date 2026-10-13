package com.iloapps.nomaddashboard.core.travel.arrival

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.travel.stays.StayCalculator
import java.time.Instant
import java.time.ZoneId

/**
 * Decides which arrival to brief: the detected one, or one the traveller asked for from the overflow
 * menu ("Show arrival briefing"). Ported from the macOS `ArrivalBriefingEvents`.
 */
object ArrivalBriefingEvents {
    /**
     * The arrival to brief now. A briefing the traveller re-opened wins while it still belongs to the
     * country they are in, even if briefings are switched off or the country was already acknowledged.
     *
     * @param detected what [ArrivalDetector] found, which respects the setting and the acknowledgement.
     */
    fun event(reopened: ArrivalEvent?, detected: ArrivalEvent?, currentCountryCode: String?): ArrivalEvent? {
        val current = currentCountryCode?.let(AppSettings::normalizedCountryCode)
        return reopened?.takeIf { it.countryCode == current } ?: detected
    }

    /**
     * An event for re-opening the briefing of the current country, for example after "Got it", or null
     * when there is nothing to re-open: no current country, or a stay older than
     * [ArrivalDetector.ARRIVAL_WINDOW_DAYS] days (the briefing is about arriving, not about living there).
     *
     * The previous country is the most recent different country before today, else the current country
     * itself, so that nothing is flagged as changed.
     */
    fun reopened(
        countryDays: List<VisitedCountryDay>,
        currentCountryCode: String?,
        now: Instant,
        zone: ZoneId,
    ): ArrivalEvent? {
        val current = currentCountryCode?.let(AppSettings::normalizedCountryCode) ?: return null
        val today = now.atZone(zone).toLocalDate()

        // No recorded stay yet means the traveller arrived today and the day is not recorded.
        val stay = StayCalculator(countryDays, today, liveCountryCode = current).currentStay(current)
        if (stay != null && stay.dayCount - 1 > ArrivalDetector.ARRIVAL_WINDOW_DAYS) return null

        val previous = countryDays
            .filter { it.date < today }
            .sortedByDescending { it.date }
            .firstNotNullOfOrNull { day ->
                day.countryCode?.let(AppSettings::normalizedCountryCode)?.takeIf { it != current }
            }
        return ArrivalEvent(
            countryCode = current,
            previousCountryCode = previous ?: current,
            arrivedOn = stay?.startDate ?: today,
        )
    }
}
