package com.iloapps.nomaddashboard.core.model

import java.time.Instant

enum class ResourceMode { AUTO, NORMAL, LOW_DATA }

enum class AppearanceMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class DashboardCardId {
    CONNECTIVITY,
    POWER,
    TIME_TRACKING,
    TRAVEL_CONTEXT,
    STAYS,
    LOCAL_INFO,
    FUEL_PRICES,
    EMERGENCY_CARE,
    EMBASSY,
    TRAVEL_ALERTS,
    WEATHER,
    ;

    /**
     * Whether the opt-in feature behind this card is switched on in Settings. Cards without a
     * feature toggle are always enabled.
     */
    fun isFeatureEnabled(settings: AppSettings): Boolean =
        when (this) {
            TIME_TRACKING -> settings.projectTimeTrackingEnabled
            STAYS -> settings.visitedPlacesEnabled
            LOCAL_INFO -> settings.localInfoEnabled
            FUEL_PRICES -> settings.fuelPricesEnabled
            EMERGENCY_CARE -> settings.emergencyCareEnabled
            EMBASSY -> settings.citizenshipCountryCodes.isNotEmpty()
            CONNECTIVITY, POWER, TRAVEL_CONTEXT, TRAVEL_ALERTS, WEATHER -> true
        }

    companion object {
        private val nomadOrder = listOf(
            CONNECTIVITY,
            POWER,
            TIME_TRACKING,
            TRAVEL_CONTEXT,
            STAYS,
            LOCAL_INFO,
            FUEL_PRICES,
            EMERGENCY_CARE,
            EMBASSY,
            TRAVEL_ALERTS,
            WEATHER,
        )

        private val roadTripOrder = listOf(
            WEATHER,
            FUEL_PRICES,
            EMERGENCY_CARE,
            EMBASSY,
            POWER,
            TRAVEL_ALERTS,
            STAYS,
            CONNECTIVITY,
            LOCAL_INFO,
            TRAVEL_CONTEXT,
            TIME_TRACKING,
        )

        /** The baseline recommended order, which is the Nomad profile's. */
        val defaultOrder: List<DashboardCardId> = nomadOrder

        /** The recommended card order for a travel profile. */
        fun defaultOrder(profile: TravelProfile): List<DashboardCardId> =
            when (profile) {
                TravelProfile.NOMAD -> nomadOrder
                TravelProfile.ROAD_TRIP -> roadTripOrder
            }

        /**
         * Drops duplicates and adds every card missing from [order]. A missing card goes right after
         * the nearest card before it in the profile's recommended order that is present, so a saved
         * order from before a card existed gets it next to its recommended neighbour (an untouched
         * old default becomes exactly the new default). A card with no earlier neighbour present
         * goes first.
         */
        fun sanitizedOrder(
            order: List<DashboardCardId>,
            profile: TravelProfile = TravelProfile.NOMAD,
        ): List<DashboardCardId> {
            val seen = mutableSetOf<DashboardCardId>()
            val result = order.filter { seen.add(it) }.toMutableList()
            val recommended = defaultOrder(profile)

            recommended.forEachIndexed { index, card ->
                if (card in seen) return@forEachIndexed
                val predecessor = recommended.subList(0, index).lastOrNull { it in seen }
                val insertionIndex = predecessor?.let { result.indexOf(it) + 1 } ?: 0
                result.add(insertionIndex, card)
                seen.add(card)
            }

            return result
        }
    }
}

enum class DashboardCardWidthMode {
    WIDE,
    NARROW,
}

data class SurfSpotConfiguration(
    val name: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class AppSettings(
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    val resourceMode: ResourceMode = ResourceMode.AUTO,
    val travelProfile: TravelProfile = TravelProfile.NOMAD,
    val dashboardCardOrder: List<DashboardCardId> = DashboardCardId.defaultOrder,
    val dashboardCardWidthModes: Map<DashboardCardId, DashboardCardWidthMode> =
        DashboardCardId.defaultOrder.associateWith { DashboardCardWidthMode.WIDE },
    val dashboardCollapsedCards: Set<DashboardCardId> = emptySet(),
    val publicIpGeolocationEnabled: Boolean = true,
    val shareAnonymousAnalytics: Boolean = true,
    val useCurrentLocationForWeather: Boolean = false,
    val useCurrentLocationForVisitedPlaces: Boolean = false,
    val weatherForecastExpanded: Boolean = true,
    /** Fetches air quality for the weather location. Only takes effect while [useCurrentLocationForWeather] is on. */
    val airQualityEnabled: Boolean = true,
    val localInfoEnabled: Boolean = false,
    val fuelPricesEnabled: Boolean = false,
    val emergencyCareEnabled: Boolean = false,
    val visitedPlacesEnabled: Boolean = true,
    val projectTimeTrackingEnabled: Boolean = false,
    val projectTimeTrackingAutoStartMinutes: Int = 7 * 60,
    val projectTimeTrackingAutoStopMinutes: Int = 19 * 60,
    val surfSpot: SurfSpotConfiguration = SurfSpotConfiguration(name = "Tarifa", latitude = 36.0132, longitude = -5.6069),
    /** Shows the top heads-up item in the persistent status surface (Quick Settings tile text). */
    val statusShowsHeadsUp: Boolean = false,
    /**
     * The user's citizenships as upper-case ISO 3166-1 alpha-2 codes, in the order they were added.
     * Sensitive: stored locally only, used to find the user's embassy and foreign-ministry emergency
     * line, never sent anywhere, never logged and never part of analytics.
     */
    val citizenshipCountryCodes: List<String> = emptyList(),
    /**
     * Days the user may stay per visit, keyed by upper-case ISO 3166-1 alpha-2 country code, for
     * example a 90-day visa. Sensitive: stored locally only and never logged.
     */
    val stayAllowances: Map<String, Int> = emptyMap(),
    val schengenCounterEnabled: Boolean = true,
    val taxResidenceDaysEnabled: Boolean = true,
    /** Days in a calendar year after which a country may treat the user as tax resident. */
    val taxResidenceThresholdDays: Int = DefaultTaxResidenceThresholdDays,
    val arrivalBriefingEnabled: Boolean = true,
    /**
     * The country whose arrival briefing the user last dismissed, so it is not shown again until
     * they arrive somewhere else.
     */
    val arrivalBriefingAcknowledgedCountryCode: String? = null,
    val arrivalBriefingAcknowledgedAt: Instant? = null,
) {
    /** Whether the card order still matches the recommendation for the current travel profile. */
    val isUsingRecommendedOrder: Boolean
        get() = dashboardCardOrder == DashboardCardId.defaultOrder(travelProfile)

    /** Air quality shares the weather location consent: it is only fetched while device-location weather is on. */
    val isAirQualityActive: Boolean
        get() = airQualityEnabled && useCurrentLocationForWeather

    /**
     * Switches the travel profile. The card order follows the new profile's recommendation only when
     * the user had not customised it; a customised order is left alone.
     */
    fun applyTravelProfile(profile: TravelProfile): AppSettings =
        copy(
            travelProfile = profile,
            dashboardCardOrder = if (isUsingRecommendedOrder) {
                DashboardCardId.defaultOrder(profile)
            } else {
                dashboardCardOrder
            },
        )

    companion object {
        /** The most citizenships that can be stored; enough for the dual and triple nationals this is for. */
        const val MaximumCitizenships = 3
        const val DefaultTaxResidenceThresholdDays = 183
        const val MinimumTaxResidenceThresholdDays = 30
        const val MaximumTaxResidenceThresholdDays = 365
        const val MaximumStayAllowanceDays = 365

        /**
         * Upper-case two-letter codes in the given order, without blanks, duplicates or anything
         * that is not a letter pair, capped at [MaximumCitizenships].
         */
        fun sanitizedCitizenshipCodes(codes: List<String>): List<String> =
            codes.mapNotNull(::normalizedCountryCode).distinct().take(MaximumCitizenships)

        /** The upper-case two-letter form of a country code, or null for anything that is not a letter pair. */
        fun normalizedCountryCode(code: String): String? {
            val normalized = code.trim().uppercase()
            return normalized.takeIf { it.length == 2 && it.all { char -> char in 'A'..'Z' } }
        }

        fun sanitizedTaxResidenceThreshold(value: Int): Int =
            value.coerceIn(MinimumTaxResidenceThresholdDays, MaximumTaxResidenceThresholdDays)

        /**
         * Upper-case two-letter country codes mapped to an allowance in days. Entries with an invalid
         * code or no days (zero or negative) are dropped; allowances above a year are capped at 365.
         * When two keys normalize to the same code (`"ma"` and `"MA"`) the larger allowance wins.
         */
        fun sanitizedStayAllowances(allowances: Map<String, Int>): Map<String, Int> {
            val result = mutableMapOf<String, Int>()
            allowances.forEach { (key, days) ->
                val code = normalizedCountryCode(key) ?: return@forEach
                if (days <= 0) return@forEach
                result[code] = maxOf(result[code] ?: 0, minOf(days, MaximumStayAllowanceDays))
            }
            return result
        }
    }
}
