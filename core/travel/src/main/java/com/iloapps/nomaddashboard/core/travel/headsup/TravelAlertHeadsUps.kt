package com.iloapps.nomaddashboard.core.travel.headsup

import com.iloapps.nomaddashboard.core.model.TravelAlertKind
import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalStatus
import com.iloapps.nomaddashboard.core.model.TravelAlertsSnapshot

/**
 * Travel advisory and regional security heads-ups from the travel alert signals.
 *
 * Android has no weather-alert provider, so there is no signal that could produce
 * [HeadsUpKind.WEATHER_ALERT]; the kind stays in the ranking so the order matches macOS.
 */
object TravelAlertHeadsUps {
    fun evaluate(alerts: TravelAlertsSnapshot): List<HeadsUpCandidate> =
        alerts.states.mapNotNull { state ->
            // Stale, checking and unavailable states are not trustworthy enough for a heads-up.
            val signal = state.signal
            val severity = signal?.let { severity(it.severity) }
            if (state.kind !in alerts.enabledKinds ||
                state.status != TravelAlertSignalStatus.READY ||
                signal == null ||
                severity == null
            ) {
                return@mapNotNull null
            }

            when (state.kind) {
                TravelAlertKind.ADVISORY -> HeadsUpCandidate(
                    HeadsUpKind.TRAVEL_ADVISORY,
                    HeadsUpDecision(
                        severity = severity,
                        title = signal.summary.trim('.', ' '),
                        detail = HeadsUpText.truncated(signal.detailSummary ?: "Source: ${signal.sourceName}"),
                        statusText = "Advisory",
                    ),
                )

                TravelAlertKind.SECURITY -> HeadsUpCandidate(
                    HeadsUpKind.REGIONAL_SECURITY,
                    HeadsUpDecision(
                        severity = severity,
                        title = if (severity == HeadsUpSeverity.ATTENTION) {
                            "Regional security alert"
                        } else {
                            "Regional security watch"
                        },
                        detail = HeadsUpText.truncated(signal.summary),
                        statusText = "Security",
                    ),
                )
            }
        }

    /** Clear and info carry no action for the traveller, so they produce no heads-up. */
    private fun severity(severity: TravelAlertSeverity): HeadsUpSeverity? =
        when (severity) {
            TravelAlertSeverity.CLEAR, TravelAlertSeverity.INFO -> null
            TravelAlertSeverity.CAUTION -> HeadsUpSeverity.CAUTION
            TravelAlertSeverity.WARNING, TravelAlertSeverity.CRITICAL -> HeadsUpSeverity.ATTENTION
        }
}
