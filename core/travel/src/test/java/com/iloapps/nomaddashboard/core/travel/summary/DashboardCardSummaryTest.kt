package com.iloapps.nomaddashboard.core.travel.summary

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.InternetState
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareFacility
import com.iloapps.nomaddashboard.core.model.EmergencyCareSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareStatus
import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.model.FuelStationPrice
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.model.HolidayPeriod
import com.iloapps.nomaddashboard.core.model.LocalHolidayPhase
import com.iloapps.nomaddashboard.core.model.LocalHolidayStatus
import com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot
import com.iloapps.nomaddashboard.core.model.LocalInfoStatus
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.model.TravelAlertsSnapshot
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import com.iloapps.nomaddashboard.core.travel.format.TravelFormatters
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionIndex
import com.iloapps.nomaddashboard.core.travel.safety.EmbassyOverviewBuilder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Test

class DashboardCardSummaryTest {
    private val locale = Locale.UK
    private val zone = ZoneId.of("Europe/Madrid")
    private val now = Instant.parse("2026-10-12T09:30:00Z")
    private val builder = DashboardCardSummaryBuilder(
        locale = locale,
        embassyOverviewBuilder = EmbassyOverviewBuilder(
            locale = locale,
            indexProvider = { DiplomaticMissionIndex(missions = emptyList()) },
        ),
    )

    private val settings = AppSettings(
        projectTimeTrackingEnabled = true,
        localInfoEnabled = true,
        fuelPricesEnabled = true,
        emergencyCareEnabled = true,
    )

    private val snapshot = DashboardSnapshot(
        connectivity = ConnectivitySnapshot(
            internetState = InternetState.ONLINE,
            isOnline = true,
            latencyMs = 28.0,
            downloadMbps = 96.2,
            uploadMbps = 19.4,
            wifiName = "Nomad Hub",
        ),
        power = PowerSnapshot(batteryPercent = 72, statusLabel = "On Battery", minutesRemaining = 208),
        travelContext = TravelContextSnapshot(publicIp = "203.0.113.42", countryCode = "ES", city = "Valencia", country = "Spain"),
        weather = WeatherSnapshot(
            currentTemperatureCelsius = 18.6,
            conditionDescription = "Partly Cloudy",
            fetchedAt = now,
        ),
        travelAlerts = TravelAlertsSnapshot(
            primaryCountryCode = "ES",
            primaryCountryName = "Spain",
            coverageCountryCodes = listOf("ES", "FR", "PT", "AD", "MA"),
        ),
        localInfo = LocalInfoSnapshot(
            status = LocalInfoStatus.READY,
            countryCode = "ES",
            publicHoliday = LocalHolidayStatus(
                phase = LocalHolidayPhase.NEXT,
                period = HolidayPeriod("Fiesta", LocalDate.of(2026, 10, 24)),
            ),
        ),
        fuelPrices = FuelPriceSnapshot(
            status = FuelPriceStatus.READY,
            diesel = station(FuelType.DIESEL, 1.429, 4.8),
            gasoline = station(FuelType.GASOLINE, 1.52, 3.0),
        ),
        emergencyCare = EmergencyCareSnapshot(
            status = EmergencyCareStatus.READY,
            facilities = listOf(hospital("Hospital Casa de Salut", 2.4), hospital("Hospital Far Away", 6.8)),
        ),
    )

    private fun station(type: FuelType, price: Double, distance: Double) = FuelStationPrice(
        fuelType = type,
        stationName = "Repsol",
        pricePerLiter = price,
        currencyCode = "EUR",
        distanceKilometers = distance,
        latitude = 39.4,
        longitude = -0.4,
    )

    private fun hospital(name: String, distance: Double) = EmergencyCareFacility(
        name = name,
        distanceKilometers = distance,
        latitude = 39.4,
        longitude = -0.4,
    )

    private fun summary(
        card: DashboardCardId,
        snapshot: DashboardSnapshot = this.snapshot,
        settings: AppSettings = this.settings,
        pending: Duration = Duration.ZERO,
    ) = builder.summarize(
        card,
        DashboardCardSummaryInput(snapshot, settings, now, zone, pendingTimeTracking = pending),
    )

    @Test
    fun `connectivity shows latency and rounded throughput`() {
        assertThat(summary(DashboardCardId.CONNECTIVITY).text).isEqualTo("28 ms · 96 ↓ 19 ↑")
    }

    @Test
    fun `connectivity shows what it has and collects otherwise`() {
        val latencyOnly = snapshot.copy(connectivity = snapshot.connectivity.copy(downloadMbps = null))
        val nothing = snapshot.copy(connectivity = ConnectivitySnapshot())

        assertThat(summary(DashboardCardId.CONNECTIVITY, latencyOnly).text).isEqualTo("28 ms")
        assertThat(summary(DashboardCardId.CONNECTIVITY, nothing).text).isEqualTo("Collecting")
    }

    @Test
    fun `power shows charge and time left`() {
        assertThat(summary(DashboardCardId.POWER).text).isEqualTo("72% · 3h 28m")
    }

    @Test
    fun `power while charging says to full and without a battery says estimating`() {
        val charging = snapshot.copy(
            power = PowerSnapshot(batteryPercent = 50, charging = true, statusLabel = "Charging", minutesToFull = 13),
        )
        val unknown = snapshot.copy(power = PowerSnapshot())
        val estimatingTime = snapshot.copy(power = PowerSnapshot(batteryPercent = 72, statusLabel = "On Battery"))

        assertThat(summary(DashboardCardId.POWER, charging).text).isEqualTo("50% · 13m to full")
        assertThat(summary(DashboardCardId.POWER, unknown).text).isEqualTo("Estimating")
        assertThat(summary(DashboardCardId.POWER, estimatingTime).text).isEqualTo("72%")
    }

    @Test
    fun `travel context shows the public ip and wifi name because the badge says VPN`() {
        assertThat(summary(DashboardCardId.TRAVEL_CONTEXT).text).isEqualTo("203.0.113.42 · Nomad Hub")
        assertThat(summary(DashboardCardId.TRAVEL_CONTEXT, snapshot.copy(travelContext = TravelContextSnapshot())).text)
            .isEqualTo("IP unavailable · Nomad Hub")
    }

    @Test
    fun `weather shows temperature and condition`() {
        assertThat(summary(DashboardCardId.WEATHER).text).isEqualTo("19° Partly Cloudy")
    }

    @Test
    fun `weather that is unavailable shows the explanation`() {
        val unavailable = snapshot.copy(weather = WeatherSnapshot(summary = "Grant location permission for weather."))

        assertThat(summary(DashboardCardId.WEATHER, unavailable).text).isEqualTo("Grant location permission for weather.")
    }

    @Test
    fun `travel alerts say where they apply because the badge has the level`() {
        assertThat(summary(DashboardCardId.TRAVEL_ALERTS).text).isEqualTo("Spain + 4 nearby")

        val alone = snapshot.copy(travelAlerts = snapshot.travelAlerts.copy(coverageCountryCodes = listOf("ES")))
        val off = snapshot.copy(travelAlerts = snapshot.travelAlerts.copy(enabledKinds = emptyList()))
        val checking = snapshot.copy(travelAlerts = TravelAlertsSnapshot())

        assertThat(summary(DashboardCardId.TRAVEL_ALERTS, alone).text).isEqualTo("Spain")
        assertThat(summary(DashboardCardId.TRAVEL_ALERTS, off).text).isEqualTo("Off")
        assertThat(summary(DashboardCardId.TRAVEL_ALERTS, checking).text).isEqualTo("Checking alerts")
    }

    @Test
    fun `fuel shows the fuel type price and distance`() {
        val price = TravelFormatters.fuelPrice(1.429, "EUR", locale)

        assertThat(summary(DashboardCardId.FUEL_PRICES).text).isEqualTo("Diesel $price · 4.8 km")
    }

    @Test
    fun `fuel falls back to gasoline then to the section detail and is off when disabled`() {
        val gasolineOnly = snapshot.copy(fuelPrices = snapshot.fuelPrices.copy(diesel = null))
        val unavailable = snapshot.copy(
            fuelPrices = FuelPriceSnapshot(status = FuelPriceStatus.CONFIGURATION_REQUIRED, detail = "Add a Tankerkönig key"),
        )

        assertThat(summary(DashboardCardId.FUEL_PRICES, gasolineOnly).text)
            .isEqualTo("Gasoline ${TravelFormatters.fuelPrice(1.52, "EUR", locale)} · 3.0 km")
        assertThat(summary(DashboardCardId.FUEL_PRICES, unavailable).text).isEqualTo("Add a Tankerkönig key")
        assertThat(summary(DashboardCardId.FUEL_PRICES, settings = settings.copy(fuelPricesEnabled = false)).text).isEqualTo("Off")
    }

    @Test
    fun `emergency leads with the number then the nearest distance`() {
        val summary = summary(DashboardCardId.EMERGENCY_CARE)

        assertThat(summary.text).isEqualTo("112 · nearest 2.4 km")
        assertThat(summary.help).isEqualTo("Nearest: Hospital Casa de Salut, 2.4 km · Emergency 112")
    }

    @Test
    fun `emergency without a known number names the nearest hospital`() {
        val unknown = snapshot.copy(
            travelContext = TravelContextSnapshot(countryCode = "ZZ"),
            localInfo = LocalInfoSnapshot(),
        )

        assertThat(summary(DashboardCardId.EMERGENCY_CARE, unknown).text).isEqualTo("Nearest 2.4 km · Hospital Casa de Salut")
    }

    @Test
    fun `emergency without hospitals shows the number or the section detail`() {
        val noHospitals = snapshot.copy(emergencyCare = EmergencyCareSnapshot(detail = "Enable emergency care in Settings"))
        val unknownCountry = noHospitals.copy(travelContext = TravelContextSnapshot(), localInfo = LocalInfoSnapshot())

        assertThat(summary(DashboardCardId.EMERGENCY_CARE, noHospitals).text).isEqualTo("112")
        assertThat(summary(DashboardCardId.EMERGENCY_CARE, unknownCountry).text).isEqualTo("Enable emergency care in Settings")
        assertThat(summary(DashboardCardId.EMERGENCY_CARE, settings = settings.copy(emergencyCareEnabled = false)).text)
            .isEqualTo("Off")
    }

    @Test
    fun `emergency uses the device country before the IP country`() {
        val abroad = snapshot.copy(
            travelContext = TravelContextSnapshot(deviceCountryCode = "JP", countryCode = "ES"),
            emergencyCare = EmergencyCareSnapshot(),
        )

        assertThat(summary(DashboardCardId.EMERGENCY_CARE, abroad).text).isEqualTo("110")
    }

    @Test
    fun `local info shows the next holiday date`() {
        assertThat(summary(DashboardCardId.LOCAL_INFO).text).isEqualTo("Holiday 24 Oct")
    }

    @Test
    fun `local info says today and tomorrow from the period dates`() {
        fun holiday(start: LocalDate, end: LocalDate = start) = snapshot.copy(
            localInfo = snapshot.localInfo.copy(
                publicHoliday = LocalHolidayStatus(LocalHolidayPhase.NEXT, HolidayPeriod("Fiesta", start, end)),
            ),
        )

        assertThat(summary(DashboardCardId.LOCAL_INFO, holiday(LocalDate.of(2026, 10, 12))).text).isEqualTo("Holiday today")
        assertThat(summary(DashboardCardId.LOCAL_INFO, holiday(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 13))).text)
            .isEqualTo("Holiday today")
        assertThat(summary(DashboardCardId.LOCAL_INFO, holiday(LocalDate.of(2026, 10, 13))).text).isEqualTo("Holiday tomorrow")
    }

    @Test
    fun `local info that is checking off or without a holiday uses the section state`() {
        val checking = snapshot.copy(localInfo = LocalInfoSnapshot(status = LocalInfoStatus.CHECKING))
        val noHoliday = snapshot.copy(
            localInfo = LocalInfoSnapshot(status = LocalInfoStatus.UNSUPPORTED, detail = "No holiday data for this country"),
        )
        val over = snapshot.copy(
            localInfo = snapshot.localInfo.copy(
                publicHoliday = LocalHolidayStatus(
                    LocalHolidayPhase.TODAY,
                    HolidayPeriod("Fiesta", LocalDate.of(2026, 10, 1)),
                ),
                detail = "Nothing coming up",
            ),
        )

        assertThat(summary(DashboardCardId.LOCAL_INFO, checking).text).isEqualTo("Checking")
        assertThat(summary(DashboardCardId.LOCAL_INFO, noHoliday).text).isEqualTo("No holiday data for this country")
        assertThat(summary(DashboardCardId.LOCAL_INFO, over).text).isEqualTo("Nothing coming up")
        assertThat(summary(DashboardCardId.LOCAL_INFO, settings = settings.copy(localInfoEnabled = false)).text).isEqualTo("Off")
    }

    @Test
    fun `time tracking shows pending time or off`() {
        assertThat(summary(DashboardCardId.TIME_TRACKING, pending = Duration.ofMinutes(6 * 60 + 58)).text).isEqualTo("Pending 6h 58m")
        assertThat(summary(DashboardCardId.TIME_TRACKING, settings = settings.copy(projectTimeTrackingEnabled = false)).text)
            .isEqualTo("Off")
    }

    @Test
    fun `durations drop empty parts`() {
        assertThat(DashboardCardSummaryBuilder.durationText(Duration.ofMinutes(42))).isEqualTo("42m")
        assertThat(DashboardCardSummaryBuilder.durationText(Duration.ofHours(3))).isEqualTo("3h")
        assertThat(DashboardCardSummaryBuilder.durationText(Duration.ofMinutes(418))).isEqualTo("6h 58m")
        assertThat(DashboardCardSummaryBuilder.durationText(Duration.ofSeconds(89))).isEqualTo("1m")
        assertThat(DashboardCardSummaryBuilder.durationText(Duration.ZERO)).isEqualTo("0m")
    }

    @Test
    fun `embassy leads with the consular phone number`() {
        val finnish = settings.copy(citizenshipCountryCodes = listOf("FI"))

        assertThat(summary(DashboardCardId.EMBASSY, settings = finnish).text).isEqualTo("+358 9 1605 5555")
        assertThat(summary(DashboardCardId.EMBASSY, settings = settings).text).isEqualTo("Off")
    }

    @Test
    fun `embassy at home says so`() {
        val spanish = settings.copy(citizenshipCountryCodes = listOf("ES"))

        assertThat(summary(DashboardCardId.EMBASSY, settings = spanish).text).isEqualTo("Home in Spain")
    }

    @Test
    fun `embassy asks for the location when the country is unknown`() {
        val noCountry = snapshot.copy(travelContext = TravelContextSnapshot(), localInfo = LocalInfoSnapshot())
        val withoutHotline = settings.copy(citizenshipCountryCodes = listOf("BE"))

        assertThat(summary(DashboardCardId.EMBASSY, noCountry, withoutHotline).text).isEqualTo("Location needed")
    }

    @Test
    fun `the stays card follows the visited places setting`() {
        assertThat(summary(DashboardCardId.STAYS, settings = settings.copy(visitedPlacesEnabled = false)).text).isEqualTo("Off")
    }

    @Test
    fun `every card has a summary`() {
        DashboardCardId.entries.forEach { card ->
            assertThat(summary(card).text).isNotEmpty()
        }
    }
}
