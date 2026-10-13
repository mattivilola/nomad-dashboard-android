package com.iloapps.nomaddashboard.core.datastore

import com.iloapps.nomaddashboard.core.model.ResourceMode
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.AppearanceMode
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardCardWidthMode
import com.iloapps.nomaddashboard.core.model.SurfSpotConfiguration
import com.iloapps.nomaddashboard.core.model.TravelProfile
import java.time.Instant

fun AppSettingsProto.toExternalModel(): AppSettings {
    val shouldRepairEmptyDefaults = settingsSchemaVersion == 0 && this == AppSettingsProto.getDefaultInstance()
    val defaults = AppSettings()
    val hasTravelFeatureSettings = settingsSchemaVersion >= TravelFeaturesSchemaVersion
    val profile = TravelProfile.entries.firstOrNull { it.name == travelProfile } ?: TravelProfile.NOMAD
    val persistedOrder = dashboardCardOrderList.mapNotNull { raw ->
        dashboardCardIdFor(raw)
    }
    // An order that still equals the pre-v2 shipped default was never customised, so it moves to the
    // current recommendation instead of having the new cards merged into the old layout.
    val order = if (settingsSchemaVersion < TravelFeaturesSchemaVersion && persistedOrder == LegacyDefaultCardOrder) {
        DashboardCardId.defaultOrder(profile)
    } else {
        DashboardCardId.sanitizedOrder(persistedOrder, profile)
    }

    val persistedWidths = dashboardCardWidthModesMap.mapNotNull { (key, value) ->
        val card = dashboardCardIdFor(key) ?: return@mapNotNull null
        val width = DashboardCardWidthMode.entries.firstOrNull { it.name == value } ?: return@mapNotNull null
        card to width
    }.toMap()

    val widths = order.associateWith { card ->
        persistedWidths[card] ?: DashboardCardWidthMode.WIDE
    }
    val autoStartMinutes = projectTimeTrackingAutoStartMinutes
    val autoStopMinutes = projectTimeTrackingAutoStopMinutes
    val useDefaultTrackingWindow = autoStartMinutes == 0 && autoStopMinutes == 0

    return AppSettings(
        resourceMode = ResourceMode.entries.firstOrNull { it.name == resourceMode } ?: ResourceMode.AUTO,
        appearanceMode = AppearanceMode.entries.firstOrNull { it.name == appearanceMode } ?: AppearanceMode.SYSTEM,
        dashboardCardOrder = order,
        travelProfile = profile,
        dashboardCardWidthModes = widths,
        dashboardCollapsedCards = dashboardCollapsedCardsList.mapNotNull(::dashboardCardIdFor).toSet(),
        publicIpGeolocationEnabled = if (shouldRepairEmptyDefaults) {
            defaults.publicIpGeolocationEnabled
        } else {
            publicIpGeolocationEnabled
        },
        shareAnonymousAnalytics = shareAnonymousAnalytics,
        useCurrentLocationForWeather = useCurrentLocationForWeather,
        useCurrentLocationForVisitedPlaces = useCurrentLocationForVisitedPlaces,
        weatherForecastExpanded = if (shouldRepairEmptyDefaults) {
            defaults.weatherForecastExpanded
        } else {
            weatherForecastExpanded
        },
        airQualityEnabled = if (hasTravelFeatureSettings) airQualityEnabled else defaults.airQualityEnabled,
        localInfoEnabled = localInfoEnabled,
        fuelPricesEnabled = fuelPricesEnabled,
        emergencyCareEnabled = emergencyCareEnabled,
        visitedPlacesEnabled = visitedPlacesEnabled,
        projectTimeTrackingEnabled = projectTimeTrackingEnabled,
        projectTimeTrackingAutoStartMinutes = if (useDefaultTrackingWindow) 7 * 60 else autoStartMinutes.coerceIn(0, 23 * 60 + 59),
        projectTimeTrackingAutoStopMinutes = if (useDefaultTrackingWindow) 19 * 60 else autoStopMinutes.coerceIn(0, 23 * 60 + 59),
        surfSpot = SurfSpotConfiguration(
            name = surfSpotName.ifBlank { defaults.surfSpot.name },
            latitude = surfSpotLatitude.takeIf { hasSurfSpotLatitude },
            longitude = surfSpotLongitude.takeIf { hasSurfSpotLongitude },
        ),
        statusShowsHeadsUp = statusShowsHeadsUp,
        citizenshipCountryCodes = AppSettings.sanitizedCitizenshipCodes(citizenshipCountryCodesList),
        stayAllowances = AppSettings.sanitizedStayAllowances(stayAllowancesMap),
        schengenCounterEnabled = if (hasTravelFeatureSettings) schengenCounterEnabled else defaults.schengenCounterEnabled,
        taxResidenceDaysEnabled = if (hasTravelFeatureSettings) taxResidenceDaysEnabled else defaults.taxResidenceDaysEnabled,
        taxResidenceThresholdDays = if (hasTravelFeatureSettings) {
            AppSettings.sanitizedTaxResidenceThreshold(taxResidenceThresholdDays)
        } else {
            defaults.taxResidenceThresholdDays
        },
        arrivalBriefingEnabled = if (hasTravelFeatureSettings) arrivalBriefingEnabled else defaults.arrivalBriefingEnabled,
        arrivalBriefingAcknowledgedCountryCode = AppSettings.normalizedCountryCode(arrivalBriefingAcknowledgedCountryCode),
        arrivalBriefingAcknowledgedAt = Instant.ofEpochMilli(arrivalBriefingAcknowledgedAtEpochMillis)
            .takeIf { hasArrivalBriefingAcknowledgedAt },
    )
}

fun AppSettings.toProto(): AppSettingsProto =
    AppSettingsProto.newBuilder()
        .setAppearanceMode(appearanceMode.name)
        .setResourceMode(resourceMode.name)
        .setTravelProfile(travelProfile.name)
        .addAllDashboardCardOrder(dashboardCardOrder.map { it.name })
        .putAllDashboardCardWidthModes(dashboardCardWidthModes.mapKeys { it.key.name }.mapValues { it.value.name })
        .addAllDashboardCollapsedCards(dashboardCollapsedCards.map { it.name }.sorted())
        .setPublicIpGeolocationEnabled(publicIpGeolocationEnabled)
        .setShareAnonymousAnalytics(shareAnonymousAnalytics)
        .setUseCurrentLocationForWeather(useCurrentLocationForWeather)
        .setUseCurrentLocationForVisitedPlaces(useCurrentLocationForVisitedPlaces)
        .setWeatherForecastExpanded(weatherForecastExpanded)
        .setAirQualityEnabled(airQualityEnabled)
        .setLocalInfoEnabled(localInfoEnabled)
        .setFuelPricesEnabled(fuelPricesEnabled)
        .setEmergencyCareEnabled(emergencyCareEnabled)
        .setVisitedPlacesEnabled(visitedPlacesEnabled)
        .setProjectTimeTrackingEnabled(projectTimeTrackingEnabled)
        .setProjectTimeTrackingAutoStartMinutes(projectTimeTrackingAutoStartMinutes)
        .setProjectTimeTrackingAutoStopMinutes(projectTimeTrackingAutoStopMinutes)
        .setSurfSpotName(surfSpot.name)
        .setHasSurfSpotLatitude(surfSpot.latitude != null)
        .setHasSurfSpotLongitude(surfSpot.longitude != null)
        .setSurfSpotLatitude(surfSpot.latitude ?: 0.0)
        .setSurfSpotLongitude(surfSpot.longitude ?: 0.0)
        .setStatusShowsHeadsUp(statusShowsHeadsUp)
        .addAllCitizenshipCountryCodes(AppSettings.sanitizedCitizenshipCodes(citizenshipCountryCodes))
        .putAllStayAllowances(AppSettings.sanitizedStayAllowances(stayAllowances))
        .setSchengenCounterEnabled(schengenCounterEnabled)
        .setTaxResidenceDaysEnabled(taxResidenceDaysEnabled)
        .setTaxResidenceThresholdDays(AppSettings.sanitizedTaxResidenceThreshold(taxResidenceThresholdDays))
        .setArrivalBriefingEnabled(arrivalBriefingEnabled)
        .setArrivalBriefingAcknowledgedCountryCode(
            arrivalBriefingAcknowledgedCountryCode?.let(AppSettings::normalizedCountryCode).orEmpty(),
        )
        .setHasArrivalBriefingAcknowledgedAt(arrivalBriefingAcknowledgedAt != null)
        .setArrivalBriefingAcknowledgedAtEpochMillis(arrivalBriefingAcknowledgedAt?.toEpochMilli() ?: 0L)
        .setSettingsSchemaVersion(CurrentSettingsSchemaVersion)
        .build()

private const val CurrentSettingsSchemaVersion = 2

/**
 * First schema that stores the travel profile and the stay, arrival briefing and air quality settings.
 * Older data has those fields absent, so true-by-default ones fall back to the model defaults and an
 * untouched card order moves to the recommendation.
 */
private const val TravelFeaturesSchemaVersion = 2

/** The default card order shipped before travel profiles, for detecting a layout the user never customised. */
private val LegacyDefaultCardOrder = listOf(
    DashboardCardId.WEATHER,
    DashboardCardId.TRAVEL_ALERTS,
    DashboardCardId.CONNECTIVITY,
    DashboardCardId.TRAVEL_CONTEXT,
    DashboardCardId.LOCAL_INFO,
    DashboardCardId.FUEL_PRICES,
    DashboardCardId.POWER,
    DashboardCardId.TIME_TRACKING,
    DashboardCardId.EMERGENCY_CARE,
)

private fun dashboardCardIdFor(raw: String): DashboardCardId? =
    when (raw) {
        "LOCAL_PRICE_LEVEL" -> DashboardCardId.LOCAL_INFO
        else -> DashboardCardId.entries.firstOrNull { it.name == raw }
    }
