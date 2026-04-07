package com.iloapps.nomaddashboard.review

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareFacility
import com.iloapps.nomaddashboard.core.model.EmergencyCareLocationSource
import com.iloapps.nomaddashboard.core.model.EmergencyCareSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareStatus
import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.model.FuelStationPrice
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.model.ProviderCredentialSettings
import com.iloapps.nomaddashboard.core.model.SignalLevel
import com.iloapps.nomaddashboard.core.model.SummaryTile
import com.iloapps.nomaddashboard.core.model.TimeTrackingDashboardState
import com.iloapps.nomaddashboard.core.model.TimeTrackingEntry
import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
import com.iloapps.nomaddashboard.core.model.TimeTrackingRecord
import com.iloapps.nomaddashboard.core.model.TravelAlertKind
import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalSnapshot
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalState
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalStatus
import com.iloapps.nomaddashboard.core.model.TravelAlertsSnapshot
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import com.iloapps.nomaddashboard.core.model.WeatherDayForecast
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import com.iloapps.nomaddashboard.feature.dashboard.DashboardUiState
import com.iloapps.nomaddashboard.feature.settings.SettingsUiState
import com.iloapps.nomaddashboard.feature.timetracking.TimeTrackingUiState
import com.iloapps.nomaddashboard.feature.visited.VisitedUiState
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

object ScreenshotReviewFixtures {
    private val fixtureSettings = AppSettings(
        publicIpGeolocationEnabled = true,
        useCurrentLocationForWeather = true,
        useCurrentLocationForVisitedPlaces = true,
        weatherForecastExpanded = true,
        fuelPricesEnabled = true,
        emergencyCareEnabled = true,
        visitedPlacesEnabled = true,
        projectTimeTrackingEnabled = true,
    )

    private val fixtureNow = Instant.parse("2026-04-07T09:30:00Z")

    private val clientWorkProject = TimeTrackingProject(
        id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
        name = "Client Work",
    )
    private val nomadResearchProject = TimeTrackingProject(
        id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
        name = "Nomad R&D",
    )

    fun dashboardState(): DashboardUiState =
        DashboardUiState(
            settings = fixtureSettings,
            snapshot = DashboardSnapshot(
                lastRefresh = fixtureNow,
                overallSummary = SummaryTile(
                    title = "Overall",
                    headline = "Ready",
                    detail = "Tarifa base · sunny weather · low battery drain",
                    level = SignalLevel.GOOD,
                ),
                networkSummary = SummaryTile(
                    title = "Network",
                    headline = "Stable",
                    detail = "42 ms latency on Nomad Cabin Wi-Fi",
                    level = SignalLevel.GOOD,
                ),
                powerSummary = SummaryTile(
                    title = "Power",
                    headline = "78%",
                    detail = "Healthy battery with 11.8W discharge",
                    level = SignalLevel.GOOD,
                ),
                connectivity = ConnectivitySnapshot(
                    internetState = "Online",
                    isOnline = true,
                    latencyMs = 42.0,
                    jitterMs = 8.0,
                    downloadMbps = 114.0,
                    uploadMbps = 19.0,
                    wifiName = "Nomad Cabin",
                    wifiSignalDbm = -56,
                    vpnActive = false,
                    timeZoneId = "Europe/Madrid",
                ),
                power = PowerSnapshot(
                    batteryPercent = 78,
                    charging = false,
                    batteryHealthSummary = "Battery health normal",
                    dischargeWatts = 11.8,
                ),
                travelContext = TravelContextSnapshot(
                    publicIp = "203.0.113.27",
                    city = "Tarifa",
                    region = "Andalusia",
                    country = "Spain",
                    countryCode = "ES",
                    latitude = 36.0132,
                    longitude = -5.6069,
                    timeZoneId = "Europe/Madrid",
                ),
                weather = WeatherSnapshot(
                    currentTemperatureCelsius = 18.4,
                    apparentTemperatureCelsius = 16.9,
                    windSpeedKph = 24.0,
                    windDirectionDegrees = 248.0,
                    rainChancePercent = 10,
                    summary = "Sunny with strong Levante wind",
                    dailyForecast = listOf(
                        WeatherDayForecast(LocalDate.of(2026, 4, 7), "Sunny", 14.0, 19.0, 5),
                        WeatherDayForecast(LocalDate.of(2026, 4, 8), "Partly cloudy", 13.0, 18.0, 15),
                        WeatherDayForecast(LocalDate.of(2026, 4, 9), "Light rain", 12.0, 17.0, 45),
                    ),
                ),
                travelAlerts = TravelAlertsSnapshot(
                    primaryCountryCode = "ES",
                    primaryCountryName = "Spain",
                    coverageCountryCodes = listOf("ES", "PT", "FR"),
                    states = listOf(
                        TravelAlertSignalState(
                            kind = TravelAlertKind.ADVISORY,
                            status = TravelAlertSignalStatus.READY,
                            signal = TravelAlertSignalSnapshot(
                                kind = TravelAlertKind.ADVISORY,
                                severity = TravelAlertSeverity.CAUTION,
                                title = "Exercise normal caution",
                                summary = "Monitor transport disruptions and coastal wind advisories.",
                                sourceName = "Smartraveller",
                                sourceUrl = "https://www.smartraveller.gov.au",
                                updatedAt = fixtureNow,
                                affectedCountryCodes = listOf("ES"),
                                itemCount = 1,
                            ),
                            sourceName = "Smartraveller",
                            sourceUrl = "https://www.smartraveller.gov.au",
                            lastAttemptedAt = fixtureNow,
                            lastSuccessAt = fixtureNow,
                        ),
                        TravelAlertSignalState(
                            kind = TravelAlertKind.SECURITY,
                            status = TravelAlertSignalStatus.READY,
                            signal = TravelAlertSignalSnapshot(
                                kind = TravelAlertKind.SECURITY,
                                severity = TravelAlertSeverity.INFO,
                                title = "Regional security low",
                                summary = "No high-severity incidents nearby in the current review region.",
                                sourceName = "ReliefWeb",
                                sourceUrl = "https://reliefweb.int",
                                updatedAt = fixtureNow,
                                affectedCountryCodes = listOf("ES", "PT", "FR"),
                                itemCount = 0,
                            ),
                            sourceName = "ReliefWeb",
                            sourceUrl = "https://reliefweb.int",
                            lastAttemptedAt = fixtureNow,
                            lastSuccessAt = fixtureNow,
                        ),
                    ),
                    fetchedAt = fixtureNow,
                ),
                fuelPrices = FuelPriceSnapshot(
                    status = FuelPriceStatus.READY,
                    sourceName = "Spanish Ministry Fuel Prices",
                    countryCode = "ES",
                    countryName = "Spain",
                    searchRadiusKilometers = 50.0,
                    diesel = FuelStationPrice(
                        fuelType = FuelType.DIESEL,
                        stationName = "Cepsa Tarifa",
                        locality = "Tarifa",
                        pricePerLiter = 1.529,
                        distanceKilometers = 2.8,
                        latitude = 36.018,
                        longitude = -5.603,
                        updatedAt = fixtureNow,
                    ),
                    gasoline = FuelStationPrice(
                        fuelType = FuelType.GASOLINE,
                        stationName = "Repsol N-340",
                        locality = "Tarifa",
                        pricePerLiter = 1.639,
                        distanceKilometers = 3.4,
                        latitude = 36.024,
                        longitude = -5.615,
                        updatedAt = fixtureNow,
                    ),
                    fetchedAt = fixtureNow,
                    detail = "Cheapest diesel and gasoline stations near the current coast base.",
                    note = "Prices update from the ministry feed.",
                ),
                emergencyCare = EmergencyCareSnapshot(
                    status = EmergencyCareStatus.READY,
                    countryCode = "ES",
                    countryName = "Spain",
                    locationSource = EmergencyCareLocationSource.DEVICE,
                    facility = EmergencyCareFacility(
                        placeId = "fixture-hospital",
                        name = "Hospital Punta Europa",
                        address = "Carretera Getares, Algeciras",
                        distanceKilometers = 18.6,
                        latitude = 36.1184,
                        longitude = -5.4534,
                        primaryType = "hospital",
                    ),
                    fetchedAt = fixtureNow,
                    detail = "Nearest hospital found within 10 km.",
                    note = "Using device location.",
                ),
                timeTracking = TimeTrackingDashboardState(
                    enabled = true,
                    headline = "Tracking ready",
                    detail = "2 completed sessions saved locally for Client Work and Nomad R&D.",
                ),
            ),
        )

    fun settingsState(): SettingsUiState =
        SettingsUiState(
            settings = fixtureSettings,
            providerCredentials = ProviderCredentialSettings(
                tankerkoenigApiKey = "tankerkoenig-demo-key-1234",
                reliefWebAppName = "nomad-dashboard-android",
            ),
        )

    fun visitedState(): VisitedUiState =
        VisitedUiState(
            settings = fixtureSettings,
            places = listOf(
                VisitedPlace(
                    city = "Tarifa",
                    region = "Andalusia",
                    country = "Spain",
                    countryCode = "ES",
                    latitude = 36.0132,
                    longitude = -5.6069,
                    sources = listOf(VisitedPlaceSource.DEVICE_LOCATION, VisitedPlaceSource.PUBLIC_IP_GEOLOCATION),
                    firstVisitedAt = Instant.parse("2026-04-01T08:15:00Z"),
                    lastVisitedAt = Instant.parse("2026-04-06T18:20:00Z"),
                ),
                VisitedPlace(
                    city = "Paris",
                    region = "Ile-de-France",
                    country = "France",
                    countryCode = "FR",
                    latitude = 48.8566,
                    longitude = 2.3522,
                    sources = listOf(VisitedPlaceSource.PUBLIC_IP_GEOLOCATION),
                    firstVisitedAt = Instant.parse("2026-03-11T09:30:00Z"),
                    lastVisitedAt = Instant.parse("2026-03-12T20:40:00Z"),
                ),
                VisitedPlace(
                    city = "Bologna",
                    region = "Emilia-Romagna",
                    country = "Italy",
                    countryCode = "IT",
                    latitude = 44.4949,
                    longitude = 11.3426,
                    sources = listOf(VisitedPlaceSource.DEVICE_LOCATION),
                    firstVisitedAt = Instant.parse("2026-02-18T07:45:00Z"),
                    lastVisitedAt = Instant.parse("2026-02-19T21:10:00Z"),
                ),
            ),
            countryDays = listOf(
                VisitedCountryDay(LocalDate.of(2026, 4, 1), "Spain", "ES", VisitedPlaceSource.DEVICE_LOCATION, false),
                VisitedCountryDay(LocalDate.of(2026, 4, 2), "Spain", "ES", VisitedPlaceSource.DEVICE_LOCATION, false),
                VisitedCountryDay(LocalDate.of(2026, 4, 3), "Spain", "ES", VisitedPlaceSource.PUBLIC_IP_GEOLOCATION, false),
                VisitedCountryDay(LocalDate.of(2026, 3, 11), "France", "FR", VisitedPlaceSource.PUBLIC_IP_GEOLOCATION, false),
                VisitedCountryDay(LocalDate.of(2026, 3, 12), "France", "FR", VisitedPlaceSource.PUBLIC_IP_GEOLOCATION, false),
                VisitedCountryDay(LocalDate.of(2026, 2, 18), "Italy", "IT", VisitedPlaceSource.DEVICE_LOCATION, false),
                VisitedCountryDay(LocalDate.of(2026, 2, 19), "Italy", "IT", VisitedPlaceSource.DEVICE_LOCATION, false),
            ),
        )

    fun timeTrackingState(): TimeTrackingUiState =
        TimeTrackingUiState(
            settings = fixtureSettings,
            projects = listOf(clientWorkProject, nomadResearchProject),
            recentEntries = listOf(
                TimeTrackingRecord(
                    entry = TimeTrackingEntry(
                        id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
                        projectId = clientWorkProject.id,
                        startAt = Instant.parse("2026-04-06T08:00:00Z"),
                        endAt = Instant.parse("2026-04-06T10:30:00Z"),
                    ),
                    project = clientWorkProject,
                ),
                TimeTrackingRecord(
                    entry = TimeTrackingEntry(
                        id = UUID.fromString("44444444-4444-4444-4444-444444444444"),
                        projectId = nomadResearchProject.id,
                        startAt = Instant.parse("2026-04-05T13:15:00Z"),
                        endAt = Instant.parse("2026-04-05T15:00:00Z"),
                    ),
                    project = nomadResearchProject,
                ),
            ),
            activeEntry = TimeTrackingRecord(
                entry = TimeTrackingEntry(
                    id = UUID.fromString("55555555-5555-5555-5555-555555555555"),
                    projectId = clientWorkProject.id,
                    startAt = Instant.parse("2026-04-07T07:45:00Z"),
                    endAt = null,
                ),
                project = clientWorkProject,
            ),
            selectedProjectId = clientWorkProject.id,
            draftProjectName = "",
            message = "Foreground tracking resumed after the last app launch.",
        )
}
