package com.iloapps.nomaddashboard.core.travel.glance

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import com.iloapps.nomaddashboard.core.travel.format.FormattedMetric
import com.iloapps.nomaddashboard.core.travel.format.TravelFormatters
import com.iloapps.nomaddashboard.core.travel.health.DashboardHealthSummary
import com.iloapps.nomaddashboard.core.travel.health.HealthLevel
import com.iloapps.nomaddashboard.core.travel.health.PowerMetricsPresentation
import com.iloapps.nomaddashboard.core.travel.health.PowerState
import com.iloapps.nomaddashboard.core.travel.health.powerState
import java.time.LocalDate

/** A semantic icon for a vital tile; the UI layer maps each one to its own drawable. */
enum class GlanceTileIcon {
    WIFI,
    WIFI_OFF,
    BATTERY,
    BATTERY_CHARGING,
    BATTERY_UNKNOWN,
    PLUGGED_IN,
    VPN_ACTIVE,
    VPN_INACTIVE,
    WEATHER,
}

/**
 * One of the four vital tiles of the glance overview. Tapping a tile expands its card and scrolls to
 * [scrollTarget].
 *
 * Android has no Data Guard, so the third tile is always the VPN tile that macOS shows while Data
 * Guard is off.
 *
 * @property value The headline value; a number and its unit are kept apart so the unit can be drawn smaller.
 * @property conditionWeatherCode The WMO weather code behind the weather tile's condition symbol.
 * @property level The status of the value; null means the tile carries no status and stays neutral.
 */
data class GlanceVitalTile(
    val kind: Kind,
    val title: String,
    /** Extra context next to the title, for example the Wi-Fi name. */
    val titleDetail: String?,
    val icon: GlanceTileIcon,
    val value: FormattedMetric,
    val conditionWeatherCode: Int?,
    val secondary: String,
    val level: HealthLevel?,
    val scrollTarget: DashboardCardId,
) {
    enum class Kind { INTERNET, POWER, VPN, WEATHER }

    val accessibilityLabel: String
        get() = listOfNotNull(title, titleDetail, value.text, secondary).joinToString(", ")

    companion object {
        const val UNKNOWN_VALUE = "—"

        /** Internet, Power, VPN and Weather, in that order. */
        fun tiles(
            snapshot: DashboardSnapshot,
            settings: AppSettings,
            health: DashboardHealthSummary,
            isOffline: Boolean,
            today: LocalDate,
        ): List<GlanceVitalTile> =
            listOf(
                internet(snapshot, health, isOffline),
                power(snapshot, health),
                vpn(snapshot),
                weather(snapshot, settings, today),
            )

        fun internet(snapshot: DashboardSnapshot, health: DashboardHealthSummary, isOffline: Boolean): GlanceVitalTile {
            val connectivity = snapshot.connectivity
            val latency = connectivity.latencyMs
            val download = connectivity.downloadMbps
            val upload = connectivity.uploadMbps

            val value = when {
                isOffline -> FormattedMetric("Offline")
                latency != null -> TravelFormatters.latency(latency)
                else -> FormattedMetric("Waiting")
            }
            val secondary = if (!isOffline && download != null && upload != null) {
                val down = TravelFormatters.megabitsPerSecond(download)
                val up = TravelFormatters.megabitsPerSecond(upload)
                "${down.number} ↓ · ${up.number} ↑ ${up.unit}"
            } else {
                health.network.reason
            }

            return GlanceVitalTile(
                kind = Kind.INTERNET,
                title = "Internet",
                titleDetail = connectivity.wifiName.trimmedNonEmpty(),
                icon = if (isOffline) GlanceTileIcon.WIFI_OFF else GlanceTileIcon.WIFI,
                value = value,
                conditionWeatherCode = null,
                secondary = secondary,
                level = if (isOffline) HealthLevel.ATTENTION else health.network.level.statusOrNull(),
                scrollTarget = DashboardCardId.CONNECTIVITY,
            )
        }

        fun power(snapshot: DashboardSnapshot, health: DashboardHealthSummary): GlanceVitalTile {
            val power = snapshot.power
            return GlanceVitalTile(
                kind = Kind.POWER,
                title = "Power",
                titleDetail = null,
                icon = when (power.powerState()) {
                    PowerState.CHARGING -> GlanceTileIcon.BATTERY_CHARGING
                    PowerState.CHARGED -> GlanceTileIcon.PLUGGED_IN
                    PowerState.BATTERY -> GlanceTileIcon.BATTERY
                    PowerState.UNKNOWN -> GlanceTileIcon.BATTERY_UNKNOWN
                },
                value = power.batteryPercent?.let { FormattedMetric(TravelFormatters.percentage(it.toDouble())) }
                    ?: FormattedMetric(UNKNOWN_VALUE),
                conditionWeatherCode = null,
                secondary = PowerMetricsPresentation.of(power).summary,
                level = health.power.level.statusOrNull(),
                scrollTarget = DashboardCardId.POWER,
            )
        }

        /** The VPN state: macOS shows it in the Data slot whenever Data Guard is off, and Android has no Data Guard. */
        fun vpn(snapshot: DashboardSnapshot): GlanceVitalTile {
            val connectivity = snapshot.connectivity
            val context = snapshot.travelContext
            val isActive = connectivity.vpnActive
            val isChecking = !isActive && connectivity.isChecking

            return GlanceVitalTile(
                kind = Kind.VPN,
                title = "VPN",
                titleDetail = null,
                icon = if (isActive) GlanceTileIcon.VPN_ACTIVE else GlanceTileIcon.VPN_INACTIVE,
                value = FormattedMetric(
                    when {
                        isActive -> "On"
                        isChecking -> "Checking"
                        else -> "Off"
                    },
                ),
                conditionWeatherCode = null,
                secondary = context.city.trimmedNonEmpty() ?: context.publicIp.trimmedNonEmpty() ?: "IP location unknown",
                level = if (isActive) HealthLevel.READY else null,
                scrollTarget = DashboardCardId.TRAVEL_CONTEXT,
            )
        }

        fun weather(snapshot: DashboardSnapshot, settings: AppSettings, today: LocalDate): GlanceVitalTile {
            val weather = snapshot.weather
            if (!weather.isAvailable) {
                return GlanceVitalTile(
                    kind = Kind.WEATHER,
                    title = "Weather",
                    titleDetail = null,
                    icon = GlanceTileIcon.WEATHER,
                    value = FormattedMetric(UNKNOWN_VALUE),
                    conditionWeatherCode = null,
                    secondary = if (hasWeatherLocation(snapshot, settings)) "Unavailable" else "Location needed",
                    level = null,
                    scrollTarget = DashboardCardId.WEATHER,
                )
            }

            return GlanceVitalTile(
                kind = Kind.WEATHER,
                title = "Weather",
                titleDetail = null,
                icon = GlanceTileIcon.WEATHER,
                value = FormattedMetric(weather.currentTemperatureCelsius?.let(TravelFormatters::temperature) ?: UNKNOWN_VALUE),
                conditionWeatherCode = weather.weatherCode,
                secondary = weatherSecondary(weather, today),
                level = null,
                scrollTarget = DashboardCardId.WEATHER,
            )
        }

        /**
         * Condition plus today's low and high ("Clear · 13° / 23°"). Rain and wind are covered by the
         * heads-up list, so they are not repeated here. Without a current condition it falls back to tomorrow.
         */
        fun weatherSecondary(weather: WeatherSnapshot, today: LocalDate): String {
            val condition = weather.conditionDescription.trimmedNonEmpty()
                ?: return weather.dailyForecast.firstOrNull { it.date == today.plusDays(1) }
                    ?.let { "Tomorrow · ${it.summary}" }
                    ?: "Unavailable"

            val forecast = weather.dailyForecast.firstOrNull { it.date == today }
            if (forecast == null || (forecast.minCelsius == null && forecast.maxCelsius == null)) return condition

            val minimum = forecast.minCelsius?.let(TravelFormatters::temperature) ?: "Estimating"
            val maximum = forecast.maxCelsius?.let(TravelFormatters::temperature) ?: "Estimating"
            return "$condition · $minimum / $maximum"
        }

        /** The weather source the data layer uses: the device location when allowed, else the IP location. */
        private fun hasWeatherLocation(snapshot: DashboardSnapshot, settings: AppSettings): Boolean {
            val context = snapshot.travelContext
            val device = settings.useCurrentLocationForWeather &&
                context.deviceLatitude != null && context.deviceLongitude != null
            return device || (context.latitude != null && context.longitude != null)
        }

        /** `UNAVAILABLE` means "no reading yet", which is neutral rather than a status. */
        private fun HealthLevel.statusOrNull(): HealthLevel? = takeUnless { it == HealthLevel.UNAVAILABLE }
    }
}

/** True once a weather reading arrived (the default snapshot stands for "no weather yet"). */
internal val WeatherSnapshot.isAvailable: Boolean
    get() = currentTemperatureCelsius != null || fetchedAt != null
