package com.iloapps.nomaddashboard.core.travel.cards

import com.iloapps.nomaddashboard.core.model.TravelAlertKind
import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalState
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalStatus
import com.iloapps.nomaddashboard.core.model.TravelAlertUnavailableReason
import com.iloapps.nomaddashboard.core.model.TravelAlertsSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The header badge of the Travel Alerts card. */
data class TravelAlertsBadge(
    val text: String,
    val tone: CardTone,
)

/** A sentence under the badge that qualifies it, such as "Updated 12 Oct · may be outdated". */
data class TravelAlertsNotice(
    val text: String,
    val kind: Kind,
) {
    enum class Kind { STALE, LIMITED }
}

/** One signal (advisory or regional security) of the card. */
data class TravelAlertRow(
    val kind: TravelAlertKind,
    val title: String,
    /** "Caution", "Stale", "Unavailable": the row's own status chip text. */
    val statusLabel: String,
    /** "Exercise caution", "Review before travel": what the level asks of the traveller. */
    val impactText: String,
    val summary: String,
    /** More text from the source; null when it only repeats [summary]. */
    val detailSummary: String?,
    /** "Smartraveller · Updated 7 Apr". */
    val metadataText: String,
    val sourceUrl: String?,
    val status: TravelAlertSignalStatus,
    val severity: TravelAlertSeverity?,
    val tone: CardTone,
)

/**
 * The Travel Alerts card: the badge carries the highest level once, staleness and unavailable sources
 * are notices beside it, and a row repeats its level only when the badge does not already say it.
 * Ported from the macOS `TravelAlertsCardPresentation`.
 */
data class TravelAlertsCardPresentation(
    val badge: TravelAlertsBadge,
    val rows: List<TravelAlertRow>,
    val staleNotice: TravelAlertsNotice?,
    val limitedNotice: TravelAlertsNotice?,
    /** True when the enabled kinds are off: the card has nothing to show but a pointer to Settings. */
    val isOff: Boolean,
) {
    val notices: List<TravelAlertsNotice>
        get() = listOfNotNull(staleNotice, limitedNotice)

    /**
     * Whether [row] shows a chip with its level. Only a signal with a level has one, and not when it only
     * repeats the header badge, so a level is stated once. Stale and unavailable rows say so in their
     * summary and in the notices instead.
     */
    fun showsChip(row: TravelAlertRow): Boolean =
        row.status == TravelAlertSignalStatus.READY && row.statusLabel != badge.text

    companion object {
        const val OFF_MESSAGE = "Enable traveller alerts in Settings."

        fun of(
            snapshot: TravelAlertsSnapshot,
            zone: ZoneId = ZoneId.systemDefault(),
        ): TravelAlertsCardPresentation {
            if (snapshot.enabledKinds.isEmpty()) {
                return TravelAlertsCardPresentation(
                    badge = TravelAlertsBadge("Off", CardTone.NEUTRAL),
                    rows = emptyList(),
                    staleNotice = null,
                    limitedNotice = null,
                    isOff = true,
                )
            }

            val rows = snapshot.enabledKinds.mapNotNull { kind -> snapshot.state(kind)?.let { row(it, zone) } }
            return TravelAlertsCardPresentation(
                badge = badge(snapshot),
                rows = rows,
                staleNotice = staleNotice(snapshot, zone),
                limitedNotice = if (snapshot.hasUnavailableStates) {
                    TravelAlertsNotice("Some sources unavailable", TravelAlertsNotice.Kind.LIMITED)
                } else {
                    null
                },
                isOff = false,
            )
        }

        /** The header line under the title: "Spain + 2 nearby countries". */
        fun subtitle(snapshot: TravelAlertsSnapshot, isCheckingStartupLocation: Boolean): String {
            val country = snapshot.primaryCountryName
            return when {
                isCheckingStartupLocation && snapshot.primaryCountryCode == null ->
                    "Checking device location before country-based alerts"
                country != null -> {
                    val nearby = (snapshot.coverageCountryCodes.size - 1).coerceAtLeast(0)
                    if (nearby > 0) "$country + $nearby nearby ${if (nearby == 1) "country" else "countries"}" else country
                }
                else -> "Monitoring travel signals"
            }
        }

        /**
         * The badge names the highest alert level. Staleness and unavailable sources are reported next to it
         * and only take the badge over when no level is known at all.
         */
        private fun badge(snapshot: TravelAlertsSnapshot): TravelAlertsBadge {
            val highest = snapshot.highestSeverity
            return when {
                highest != null -> TravelAlertsBadge(highest.badgeTitle(), highest.tone())
                snapshot.hasStaleStates -> TravelAlertsBadge("Stale", CardTone.NEUTRAL)
                snapshot.hasUnavailableStates -> TravelAlertsBadge("Limited", CardTone.CAUTION)
                else -> TravelAlertsBadge("Checking", CardTone.NEUTRAL)
            }
        }

        private fun staleNotice(snapshot: TravelAlertsSnapshot, zone: ZoneId): TravelAlertsNotice? {
            val stale = snapshot.states.filter { it.kind in snapshot.enabledKinds && it.status == TravelAlertSignalStatus.STALE }
            if (stale.isEmpty()) return null
            // The oldest good refresh is the one that bounds how outdated the card can be.
            val text = stale.mapNotNull { it.lastSuccessAt }.minOrNull()
                ?.let { "Updated ${dateText(it, zone)} · may be outdated" }
                ?: "May be outdated"
            return TravelAlertsNotice(text, TravelAlertsNotice.Kind.STALE)
        }

        private fun row(state: TravelAlertSignalState, zone: ZoneId): TravelAlertRow {
            val signal = state.signal
            val sourceName = signal?.sourceName ?: state.sourceName
            val metadata = listOf(sourceName, freshnessText(state, zone)).filter(String::isNotBlank).joinToString(" · ")
            val severity = signal?.severity

            val summary = when (state.status) {
                TravelAlertSignalStatus.CHECKING -> "Checking alerts..."
                TravelAlertSignalStatus.READY -> signal?.summary ?: "No current alerts."
                TravelAlertSignalStatus.STALE -> signal?.summary?.let { "Last known: $it" } ?: "Last known alert status unavailable."
                TravelAlertSignalStatus.UNAVAILABLE -> state.diagnosticSummary ?: state.reason?.summary() ?: "Source unavailable"
            }
            val detail = when (state.status) {
                TravelAlertSignalStatus.READY,
                TravelAlertSignalStatus.STALE,
                -> signal?.detailSummary?.takeIf(String::isNotBlank)?.takeUnless { it.equals(signal.summary, ignoreCase = true) }
                TravelAlertSignalStatus.CHECKING,
                TravelAlertSignalStatus.UNAVAILABLE,
                -> null
            }
            val sourceUrl = when (state.status) {
                TravelAlertSignalStatus.READY,
                TravelAlertSignalStatus.STALE,
                -> signal?.sourceUrl?.takeIf(String::isNotBlank)
                TravelAlertSignalStatus.CHECKING,
                TravelAlertSignalStatus.UNAVAILABLE,
                -> null
            }

            return TravelAlertRow(
                kind = state.kind,
                title = state.kind.displayName(),
                statusLabel = statusLabel(state),
                impactText = impactText(state),
                summary = summary,
                detailSummary = detail,
                metadataText = metadata,
                sourceUrl = sourceUrl,
                status = state.status,
                severity = severity,
                tone = when (state.status) {
                    TravelAlertSignalStatus.CHECKING -> CardTone.NEUTRAL
                    TravelAlertSignalStatus.UNAVAILABLE -> CardTone.CAUTION
                    TravelAlertSignalStatus.READY -> (severity ?: TravelAlertSeverity.CLEAR).tone()
                    TravelAlertSignalStatus.STALE -> (severity ?: TravelAlertSeverity.INFO).tone()
                },
            )
        }

        private fun statusLabel(state: TravelAlertSignalState): String =
            when (state.status) {
                TravelAlertSignalStatus.CHECKING -> "Checking"
                TravelAlertSignalStatus.READY -> state.signal?.severity?.badgeTitle() ?: "Ready"
                TravelAlertSignalStatus.STALE -> "Stale"
                TravelAlertSignalStatus.UNAVAILABLE -> "Unavailable"
            }

        private fun impactText(state: TravelAlertSignalState): String =
            when (state.status) {
                TravelAlertSignalStatus.CHECKING -> "Live check"
                TravelAlertSignalStatus.READY -> when (state.signal?.severity) {
                    TravelAlertSeverity.CLEAR -> "No elevated signal"
                    TravelAlertSeverity.INFO -> "Nearby watch"
                    TravelAlertSeverity.CAUTION -> "Exercise caution"
                    TravelAlertSeverity.WARNING -> "Review before travel"
                    TravelAlertSeverity.CRITICAL -> "Immediate attention"
                    null -> "Signal ready"
                }
                TravelAlertSignalStatus.STALE -> "Last known signal"
                TravelAlertSignalStatus.UNAVAILABLE -> "Source issue"
            }

        private fun freshnessText(state: TravelAlertSignalState, zone: ZoneId): String =
            when (state.status) {
                TravelAlertSignalStatus.CHECKING -> "Refreshing now"
                TravelAlertSignalStatus.READY -> state.signal?.updatedAt?.let { "Updated ${dateText(it, zone)}" } ?: "Updated recently"
                TravelAlertSignalStatus.STALE ->
                    state.lastSuccessAt?.let { "Last good refresh ${dateText(it, zone)}" } ?: "Last good refresh unavailable"
                TravelAlertSignalStatus.UNAVAILABLE ->
                    state.lastAttemptedAt?.let { "Checked ${dateText(it, zone)}" } ?: "Check attempted"
            }

        /** "12 Oct". */
        private fun dateText(instant: Instant, zone: ZoneId): String =
            DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH).withZone(zone).format(instant)

        private fun TravelAlertKind.displayName(): String =
            when (this) {
                TravelAlertKind.ADVISORY -> "Travel Advisory"
                TravelAlertKind.SECURITY -> "Regional Security"
            }

        private fun TravelAlertSeverity.badgeTitle(): String =
            when (this) {
                TravelAlertSeverity.CLEAR -> "Clear"
                TravelAlertSeverity.INFO -> "Info"
                TravelAlertSeverity.CAUTION -> "Caution"
                TravelAlertSeverity.WARNING -> "Warning"
                TravelAlertSeverity.CRITICAL -> "Critical"
            }

        /** Clear is good, info is plain text, caution is sand, warning and critical are coral. */
        private fun TravelAlertSeverity.tone(): CardTone =
            when (this) {
                TravelAlertSeverity.CLEAR -> CardTone.GOOD
                TravelAlertSeverity.INFO -> CardTone.NEUTRAL
                TravelAlertSeverity.CAUTION -> CardTone.CAUTION
                TravelAlertSeverity.WARNING,
                TravelAlertSeverity.CRITICAL,
                -> CardTone.ATTENTION
            }

        private fun TravelAlertUnavailableReason.summary(): String =
            when (this) {
                TravelAlertUnavailableReason.COUNTRY_REQUIRED -> "Country needed for nearby alerts"
                TravelAlertUnavailableReason.LOCATION_REQUIRED -> "Location needed for local alerts"
                TravelAlertUnavailableReason.SOURCE_UNAVAILABLE -> "Source unavailable"
                TravelAlertUnavailableReason.SOURCE_CONFIGURATION_REQUIRED -> "Source setup required"
            }
    }
}
