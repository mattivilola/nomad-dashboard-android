package com.iloapps.nomaddashboard.core.travel.health

/**
 * How a section of the dashboard is doing. Color means status only: [READY] is teal, [CAUTION] sand,
 * [ATTENTION] coral, and [UNAVAILABLE] ("no reading yet") stays neutral.
 */
enum class HealthLevel {
    READY,
    CAUTION,
    ATTENTION,
    UNAVAILABLE,
}

/** A semantic icon for a health reading; the UI layer maps each one to its own drawable. */
enum class HealthSymbol {
    OFFLINE,
    WAITING,
    NETWORK_WARNING,
    JITTER,
    NETWORK,
    NETWORK_READY,
    NETWORK_UNVERIFIED,
    BATTERY_LOW,
    BATTERY_HALF,
    BATTERY_FULL,
    BATTERY_UNKNOWN,
    POWER_SAVE,
    CHARGING,
    PLUGGED_IN,
    OVERALL_ATTENTION,
    OVERALL_CAUTION,
    OVERALL_READY,
}

data class SectionHealth(
    val label: String,
    val level: HealthLevel,
    val reason: String,
    val symbol: HealthSymbol,
)

data class DashboardHealthSummary(
    val overall: SectionHealth,
    val network: SectionHealth,
    val power: SectionHealth,
)
