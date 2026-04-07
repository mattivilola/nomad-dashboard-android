package com.iloapps.nomaddashboard.core.model

import java.time.Instant

enum class TravelAlertKind {
    ADVISORY,
    SECURITY,
}

enum class TravelAlertSeverity {
    CLEAR,
    INFO,
    CAUTION,
    WARNING,
    CRITICAL,
    ;

    val rank: Int
        get() = when (this) {
            CLEAR -> 0
            INFO -> 1
            CAUTION -> 2
            WARNING -> 3
            CRITICAL -> 4
        }
}

enum class TravelAlertSignalStatus {
    CHECKING,
    READY,
    STALE,
    UNAVAILABLE,
}

enum class TravelAlertUnavailableReason {
    COUNTRY_REQUIRED,
    LOCATION_REQUIRED,
    SOURCE_UNAVAILABLE,
    SOURCE_CONFIGURATION_REQUIRED,
}

data class TravelAlertSignalSnapshot(
    val kind: TravelAlertKind,
    val severity: TravelAlertSeverity,
    val title: String,
    val summary: String,
    val sourceName: String,
    val sourceUrl: String? = null,
    val updatedAt: Instant,
    val affectedCountryCodes: List<String> = emptyList(),
    val itemCount: Int? = null,
)

data class TravelAlertSignalState(
    val kind: TravelAlertKind,
    val status: TravelAlertSignalStatus,
    val signal: TravelAlertSignalSnapshot? = null,
    val reason: TravelAlertUnavailableReason? = null,
    val diagnosticSummary: String? = null,
    val sourceName: String,
    val sourceUrl: String? = null,
    val lastAttemptedAt: Instant? = null,
    val lastSuccessAt: Instant? = null,
) {
    val resolvedSignal: TravelAlertSignalSnapshot?
        get() = when (status) {
            TravelAlertSignalStatus.READY,
            TravelAlertSignalStatus.STALE,
            -> signal
            TravelAlertSignalStatus.CHECKING,
            TravelAlertSignalStatus.UNAVAILABLE,
            -> null
        }

    val highestSeverity: TravelAlertSeverity?
        get() = resolvedSignal?.severity
}

data class TravelAlertsSnapshot(
    val enabledKinds: List<TravelAlertKind> = TravelAlertKind.entries,
    val primaryCountryCode: String? = null,
    val primaryCountryName: String? = null,
    val coverageCountryCodes: List<String> = emptyList(),
    val states: List<TravelAlertSignalState> = TravelAlertKind.entries.map { kind ->
        TravelAlertSignalState(
            kind = kind,
            status = TravelAlertSignalStatus.CHECKING,
            sourceName = when (kind) {
                TravelAlertKind.ADVISORY -> "Smartraveller"
                TravelAlertKind.SECURITY -> "ReliefWeb"
            },
            sourceUrl = when (kind) {
                TravelAlertKind.ADVISORY -> "https://www.smartraveller.gov.au"
                TravelAlertKind.SECURITY -> "https://reliefweb.int"
            },
        )
    },
    val fetchedAt: Instant? = null,
) {
    fun state(kind: TravelAlertKind): TravelAlertSignalState? = states.firstOrNull { it.kind == kind }

    fun signal(kind: TravelAlertKind): TravelAlertSignalSnapshot? = state(kind)?.resolvedSignal

    val highestSeverity: TravelAlertSeverity?
        get() = states.mapNotNull { it.highestSeverity }.maxByOrNull { it.rank }

    val hasStaleStates: Boolean
        get() = states.any { it.status == TravelAlertSignalStatus.STALE }

    val hasUnavailableStates: Boolean
        get() = states.any { it.status == TravelAlertSignalStatus.UNAVAILABLE }

    val allResolvedClear: Boolean
        get() = enabledKinds.isNotEmpty() &&
            states.size == enabledKinds.size &&
            states.all { state ->
                state.status == TravelAlertSignalStatus.READY &&
                    state.signal?.severity == TravelAlertSeverity.CLEAR
            }
}
