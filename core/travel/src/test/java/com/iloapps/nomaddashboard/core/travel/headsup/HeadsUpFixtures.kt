package com.iloapps.nomaddashboard.core.travel.headsup

import com.iloapps.nomaddashboard.core.model.AirQualityForecastSlot
import com.iloapps.nomaddashboard.core.model.AirQualityPollutant
import com.iloapps.nomaddashboard.core.model.AirQualitySnapshot
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.model.FuelStationPrice
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.model.HolidayPeriod
import com.iloapps.nomaddashboard.core.model.LocalHolidayPhase
import com.iloapps.nomaddashboard.core.model.LocalHolidayStatus
import com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot
import com.iloapps.nomaddashboard.core.model.LocalInfoStatus
import com.iloapps.nomaddashboard.core.model.TravelAlertKind
import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalSnapshot
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalState
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalStatus
import com.iloapps.nomaddashboard.core.model.TravelAlertsSnapshot
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.model.TravelProfile
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.WeatherHourlyForecastSlot
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import com.iloapps.nomaddashboard.core.travel.daylight.ClockTimeFormatter
import com.iloapps.nomaddashboard.core.travel.daylight.DaylightReference
import com.iloapps.nomaddashboard.core.travel.daylight.SolarDay
import com.iloapps.nomaddashboard.core.travel.rhythm.IcuLunisolarCalendars
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

// Shared fixtures for the heads-up engine tests, mirroring the macOS DashboardInsightEngineTests fixtures.

/** 2026-10-12 12:00 UTC, which is 14:00 in Paris (a Monday). */
internal val Now: Instant = Instant.ofEpochSecond(1_791_806_400)

internal val ParisZone: ZoneId = ZoneId.of("Europe/Paris")

internal val ParisToday: LocalDate = LocalDate.of(2026, 10, 12)

/** English (United Kingdom) text, as the macOS tests format everything: 24-hour clock, "8 Feb", pounds-style prices. */
internal val UkFormats = HeadsUpFormats(locale = Locale.UK)

internal val ParisReference = DaylightReference(48.8566, 2.3522, ParisZone)

internal val ParisSun: SolarDay = ParisReference.solarDay(Now)

internal fun parisSolarDay(offsetDays: Long): SolarDay = ParisReference.solarDay(ParisToday.plusDays(offsetDays))

internal val Engine = HeadsUpEngine(LocalRhythmCalendar(IcuLunisolarCalendars()))

/** 24-hour clock time in Paris, as the formats render a device-zone time. */
internal fun parisClock(instant: Instant): String = ClockTimeFormatter.Hour24.format(instant.atZone(ParisZone))

/** The clock time [hours] after [Now] in Paris. */
internal fun parisClock(hours: Double): String = parisClock(Now.plusSeconds((hours * 3_600).toLong()))

internal fun parisInstant(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
    LocalDateTime.of(year, month, day, hour, minute).atZone(ParisZone).toInstant()

/**
 * Settings for the engine tests: air quality is switched on (it needs the device-location weather
 * consent), the way a user who has set up weather has it.
 */
internal fun settings(
    profile: TravelProfile = TravelProfile.NOMAD,
    citizenships: List<String> = emptyList(),
    stayAllowances: Map<String, Int> = emptyMap(),
    taxResidenceThresholdDays: Int = AppSettings.DefaultTaxResidenceThresholdDays,
    schengenCounterEnabled: Boolean = true,
    taxResidenceDaysEnabled: Boolean = true,
    visitedPlacesEnabled: Boolean = true,
    airQualityEnabled: Boolean = true,
    useCurrentLocationForWeather: Boolean = true,
) = AppSettings(
    travelProfile = profile,
    citizenshipCountryCodes = citizenships,
    stayAllowances = stayAllowances,
    taxResidenceThresholdDays = taxResidenceThresholdDays,
    schengenCounterEnabled = schengenCounterEnabled,
    taxResidenceDaysEnabled = taxResidenceDaysEnabled,
    visitedPlacesEnabled = visitedPlacesEnabled,
    airQualityEnabled = airQualityEnabled,
    useCurrentLocationForWeather = useCurrentLocationForWeather,
)

internal fun insights(
    snapshot: DashboardSnapshot,
    settings: AppSettings = settings(),
    at: Instant = Now,
    countryDays: List<VisitedCountryDay> = emptyList(),
    limit: Int = 20,
    zone: ZoneId = ParisZone,
    formats: HeadsUpFormats = UkFormats,
): List<HeadsUpItem> = Engine.evaluate(
    snapshot = snapshot,
    settings = settings,
    countryDays = countryDays,
    now = at,
    zone = zone,
    formats = formats,
    limit = limit,
)

internal fun roadTripInsights(snapshot: DashboardSnapshot, at: Instant = Now): List<HeadsUpItem> =
    insights(snapshot, settings(profile = TravelProfile.ROAD_TRIP), at)

internal fun List<HeadsUpItem>.kinds(): List<HeadsUpKind> = map { it.kind }

internal fun List<HeadsUpItem>.item(kind: HeadsUpKind): HeadsUpItem? = firstOrNull { it.kind == kind }

internal fun snapshot(
    weather: WeatherSnapshot = WeatherSnapshot(),
    alerts: TravelAlertsSnapshot = TravelAlertsSnapshot(),
    localInfo: LocalInfoSnapshot = LocalInfoSnapshot(),
    fuel: FuelPriceSnapshot = FuelPriceSnapshot(),
    airQuality: AirQualitySnapshot? = null,
    travelContext: TravelContextSnapshot = TravelContextSnapshot(),
    connectivity: ConnectivitySnapshot = ConnectivitySnapshot(),
) = DashboardSnapshot(
    connectivity = connectivity,
    travelContext = travelContext,
    weather = weather,
    airQuality = airQuality,
    travelAlerts = alerts,
    localInfo = localInfo,
    fuelPrices = fuel,
)

// region Location

internal val ParisLocation = TravelContextSnapshot(deviceLatitude = 48.8566, deviceLongitude = 2.3522)

/** An IP location (coordinates and its own time zone). */
internal fun ipLocation(latitude: Double?, longitude: Double?, timeZoneId: String?) = TravelContextSnapshot(
    latitude = latitude,
    longitude = longitude,
    timeZoneId = timeZoneId,
)

/** A device in [countryCode] with no coordinates, for the local-rhythm rules. */
internal fun rhythmLocation(countryCode: String, region: String? = null) = TravelContextSnapshot(
    deviceCountryCode = countryCode,
    deviceRegion = region,
)

// endregion

// region Weather

internal fun weather(
    windKph: Double? = null,
    windDegrees: Double? = 270.0,
    rainChancePercent: Int? = null,
    slots: List<WeatherHourlyForecastSlot> = emptyList(),
    fetchedAt: Instant? = Now,
    uvIndex: Int? = null,
) = WeatherSnapshot(
    currentTemperatureCelsius = 18.0,
    apparentTemperatureCelsius = 17.0,
    windSpeedKph = windKph,
    windDirectionDegrees = windDegrees,
    rainChancePercent = rainChancePercent,
    conditionDescription = "Cloudy",
    uvIndex = uvIndex,
    hourlyForecast = slots,
    fetchedAt = fetchedAt,
)

internal fun slot(
    hours: Double = 0.0,
    minutes: Double = 0.0,
    rain: Int? = null,
    windKph: Double? = null,
    windDegrees: Double? = null,
    uv: Int? = null,
) = WeatherHourlyForecastSlot(
    at = Now.plusMillis(((hours * 3_600 + minutes * 60) * 1_000).toLong()),
    summary = "Cloudy",
    temperatureCelsius = 17.0,
    rainChancePercent = rain,
    windSpeedKph = windKph,
    windDirectionDegrees = windDegrees,
    uvIndex = uv,
)

internal fun airQuality(
    aqi: Int,
    main: AirQualityPollutant? = AirQualityPollutant.PM2_5,
    slots: List<AirQualityForecastSlot> = emptyList(),
    fetchedAt: Instant = Now,
) = AirQualitySnapshot(
    latitude = 48.9,
    longitude = 2.4,
    sourceName = "Open-Meteo",
    europeanAqi = aqi,
    mainPollutant = main,
    pm2_5 = 20.0,
    pm10 = 30.0,
    ozone = 50.0,
    nitrogenDioxide = 15.0,
    forecastSlots = slots,
    fetchedAt = fetchedAt,
)

internal fun aqiSlot(hours: Double, aqi: Int?) =
    AirQualityForecastSlot(time = Now.plusSeconds((hours * 3_600).toLong()), europeanAqi = aqi)

// endregion

// region Travel alerts

private fun alertSummary(kind: TravelAlertKind): String =
    when (kind) {
        TravelAlertKind.ADVISORY -> "France: exercise a high degree of caution."
        TravelAlertKind.SECURITY -> "Security reporting mentions France within the last 72 hours."
    }

/** Signals are kept on checking and unavailable states too, to prove the engine relies on the status. */
internal fun alerts(
    vararg signals: Pair<TravelAlertKind, TravelAlertSeverity>,
    status: TravelAlertSignalStatus = TravelAlertSignalStatus.READY,
    enabledKinds: List<TravelAlertKind> = TravelAlertKind.entries,
    detailSummary: String? = null,
): TravelAlertsSnapshot {
    val states = signals.map { (kind, severity) ->
        val source = if (kind == TravelAlertKind.ADVISORY) "Smartraveller" else "ReliefWeb"
        TravelAlertSignalState(
            kind = kind,
            status = status,
            signal = TravelAlertSignalSnapshot(
                kind = kind,
                severity = severity,
                title = "Title",
                summary = alertSummary(kind),
                detailSummary = detailSummary,
                sourceName = source,
                updatedAt = Now,
            ),
            sourceName = source,
            lastAttemptedAt = Now,
            lastSuccessAt = Now,
        )
    }
    return TravelAlertsSnapshot(
        enabledKinds = enabledKinds,
        primaryCountryCode = "FR",
        primaryCountryName = "France",
        coverageCountryCodes = listOf("FR"),
        states = states,
        fetchedAt = Now,
    )
}

// endregion

// region Local info and fuel

/** A public holiday [dayOffset] days from [ParisToday]. */
internal fun holiday(dayOffset: Long, name: String = "Armistice"): HolidayPeriod {
    val start = ParisToday.plusDays(dayOffset)
    return HolidayPeriod(name = name, startDate = start, endDate = start)
}

internal fun localInfo(
    publicHoliday: HolidayPeriod? = null,
    phase: LocalHolidayPhase = LocalHolidayPhase.NEXT,
    countryCode: String? = "FR",
) = LocalInfoSnapshot(
    status = LocalInfoStatus.READY,
    locality = "Paris",
    countryCode = countryCode,
    countryName = "France",
    timezone = "Europe/Paris",
    publicHoliday = publicHoliday?.let { LocalHolidayStatus(phase, it) },
    fetchedAt = Now,
    detail = null,
)

internal fun station(type: FuelType, price: Double = 1.689, distance: Double = 2.4) = FuelStationPrice(
    fuelType = type,
    stationName = "Station",
    pricePerLiter = price,
    currencyCode = "EUR",
    distanceKilometers = distance,
    latitude = 48.85,
    longitude = 2.35,
    updatedAt = Now,
)

internal fun fuel(
    status: FuelPriceStatus = FuelPriceStatus.READY,
    diesel: FuelStationPrice? = null,
    gasoline: FuelStationPrice? = null,
    radius: Double = 50.0,
) = FuelPriceSnapshot(
    status = status,
    sourceName = "Test",
    countryCode = "FR",
    countryName = "France",
    searchRadiusKilometers = radius,
    diesel = diesel,
    gasoline = gasoline,
    fetchedAt = Now,
    detail = "",
)

// endregion
