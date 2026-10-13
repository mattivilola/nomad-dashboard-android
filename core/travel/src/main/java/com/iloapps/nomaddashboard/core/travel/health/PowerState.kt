package com.iloapps.nomaddashboard.core.travel.health

import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.travel.format.FormattedMetric
import com.iloapps.nomaddashboard.core.travel.format.TravelFormatters

/** Where the device gets its power from, derived from Android's battery status. */
enum class PowerState {
    /** No usable battery reading yet. */
    UNKNOWN,
    BATTERY,
    CHARGING,

    /** Plugged in and full (macOS "charged"). */
    CHARGED,
}

private const val STATUS_CHECKING = "Checking"
private const val STATUS_FULL = "Full"
private const val STATUS_PLUGGED_IN = "Plugged In"
private const val FULL_PERCENT = 100

/**
 * Android reports `charging` for both charging and full, and a separate "Plugged In" status for a
 * plugged-in battery that is not charging, so the macOS charged state is rebuilt from those.
 */
fun PowerSnapshot.powerState(): PowerState =
    when {
        charging -> if (statusLabel.equals(STATUS_FULL, ignoreCase = true) || (batteryPercent ?: 0) >= FULL_PERCENT) {
            PowerState.CHARGED
        } else {
            PowerState.CHARGING
        }

        statusLabel.equals(STATUS_PLUGGED_IN, ignoreCase = true) -> PowerState.CHARGED
        batteryPercent == null -> PowerState.UNKNOWN
        else -> PowerState.BATTERY
    }

/** True before the first battery reading arrived (the snapshot is still the default one). */
val PowerSnapshot.isCollecting: Boolean
    get() = batteryPercent == null && !charging && statusLabel.equals(STATUS_CHECKING, ignoreCase = true)

/**
 * The Power card's second and third metric and a one-line summary, ported from the macOS
 * `PowerMetricsPresentation`.
 *
 * @property drainTitle "Drain" on battery, "Adapter" on external power.
 * @property drainReading The discharge rate, with the unit apart. Null when there is nothing useful to
 *   show (Android cannot read the adapter's wattage), so the metric is omitted instead of guessed.
 * @property summary One line for compact surfaces such as the glance power tile.
 */
data class PowerMetricsPresentation(
    val drainTitle: String,
    val drainReading: FormattedMetric?,
    val timeLeftTitle: String,
    val timeLeftValue: String,
    val summary: String,
) {
    val drainValue: String?
        get() = drainReading?.text

    companion object {
        const val ESTIMATING = "Estimating"

        fun of(power: PowerSnapshot): PowerMetricsPresentation =
            when (power.powerState()) {
                PowerState.CHARGING -> PowerMetricsPresentation(
                    drainTitle = "Adapter",
                    drainReading = null,
                    timeLeftTitle = "To Full",
                    timeLeftValue = power.minutesToFull?.let(TravelFormatters::minutes) ?: ESTIMATING,
                    summary = power.minutesToFull?.let { "Charging · ${TravelFormatters.minutes(it)} to full" } ?: "Charging",
                )

                PowerState.CHARGED -> PowerMetricsPresentation(
                    drainTitle = "Adapter",
                    drainReading = null,
                    timeLeftTitle = "Charge",
                    timeLeftValue = "Full",
                    summary = "Plugged in",
                )

                PowerState.BATTERY -> PowerMetricsPresentation(
                    drainTitle = "Drain",
                    drainReading = power.dischargeWatts?.let(TravelFormatters::watts) ?: FormattedMetric("On battery"),
                    timeLeftTitle = "Time Left",
                    timeLeftValue = power.minutesRemaining?.let(TravelFormatters::minutes) ?: ESTIMATING,
                    summary = power.minutesRemaining?.let { "${TravelFormatters.minutes(it)} left" } ?: "On battery",
                )

                PowerState.UNKNOWN -> PowerMetricsPresentation(
                    drainTitle = "Drain",
                    drainReading = FormattedMetric(ESTIMATING),
                    timeLeftTitle = "Time Left",
                    timeLeftValue = ESTIMATING,
                    summary = ESTIMATING,
                )
            }
    }
}
