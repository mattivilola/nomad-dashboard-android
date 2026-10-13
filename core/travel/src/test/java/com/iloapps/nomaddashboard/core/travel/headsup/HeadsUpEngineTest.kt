package com.iloapps.nomaddashboard.core.travel.headsup

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.model.HolidayPeriod
import com.iloapps.nomaddashboard.core.model.LocalHolidayPhase
import com.iloapps.nomaddashboard.core.model.TravelAlertKind
import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalStatus
import com.iloapps.nomaddashboard.core.model.TravelProfile
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Test

/** The general, travel-alert, weather, holiday and fuel rules of the heads-up engine (macOS DashboardInsightEngineTests). */
class HeadsUpEngineTest {
    // region General

    @Test
    fun `an empty snapshot produces no heads-ups`() {
        assertThat(insights(DashboardSnapshot())).isEmpty()
    }

    @Test
    fun `heads-ups cover only the non-health cards and have one item per kind`() {
        val snapshot = snapshot(
            alerts = alerts(TravelAlertKind.ADVISORY to TravelAlertSeverity.WARNING),
            weather = weather(windKph = 70.0, slots = listOf(slot(hours = 1.0, rain = 90))),
            localInfo = localInfo(holiday(0)),
            fuel = fuel(diesel = station(FuelType.DIESEL)),
        )

        val all = insights(snapshot)

        assertThat(all.map { it.relatedCard }.toSet()).isEqualTo(
            setOf(DashboardCardId.TRAVEL_ALERTS, DashboardCardId.WEATHER, DashboardCardId.LOCAL_INFO, DashboardCardId.FUEL_PRICES),
        )
        assertThat(all.kinds()).containsNoDuplicates()
    }

    private val mixedSnapshot = snapshot(
        // advisory caution, security attention
        alerts = alerts(
            TravelAlertKind.ADVISORY to TravelAlertSeverity.CAUTION,
            TravelAlertKind.SECURITY to TravelAlertSeverity.CRITICAL,
        ),
        // wind caution, rain info
        weather = weather(windKph = 45.0, slots = listOf(slot(hours = 2.0, rain = 80))),
        // holiday caution
        localInfo = localInfo(holiday(0)),
        // fuel info
        fuel = fuel(diesel = station(FuelType.DIESEL)),
    )

    @Test
    fun `sorts by severity then the tie-break order and honors the limit`() {
        assertThat(insights(mixedSnapshot).kinds()).containsExactly(
            HeadsUpKind.REGIONAL_SECURITY,
            HeadsUpKind.TRAVEL_ADVISORY,
            HeadsUpKind.STRONG_WIND,
            HeadsUpKind.PUBLIC_HOLIDAY,
            HeadsUpKind.RAIN_SOON,
            HeadsUpKind.FUEL_NEARBY,
        ).inOrder()

        assertThat(insights(mixedSnapshot, limit = HeadsUpEngine.MAX_ITEMS).kinds()).containsExactly(
            HeadsUpKind.REGIONAL_SECURITY,
            HeadsUpKind.TRAVEL_ADVISORY,
            HeadsUpKind.STRONG_WIND,
        ).inOrder()
        assertThat(insights(mixedSnapshot, limit = 1)).hasSize(1)
        assertThat(insights(mixedSnapshot, limit = 0)).isEmpty()
        assertThat(HeadsUpEngine.MAX_ITEMS).isEqualTo(3)
    }

    @Test
    fun `the engine returns at most three items by default`() {
        val items = Engine.evaluate(mixedSnapshot, settings(), emptyList(), Now, ParisZone, UkFormats)

        assertThat(items.kinds()).containsExactly(
            HeadsUpKind.REGIONAL_SECURITY,
            HeadsUpKind.TRAVEL_ADVISORY,
            HeadsUpKind.STRONG_WIND,
        ).inOrder()
    }

    @Test
    fun `the road trip profile ranks the same heads-ups differently`() {
        val nomad = insights(mixedSnapshot, settings(profile = TravelProfile.NOMAD)).kinds()
        val roadTrip = insights(mixedSnapshot, settings(profile = TravelProfile.ROAD_TRIP)).kinds()

        assertThat(nomad).containsExactly(
            HeadsUpKind.REGIONAL_SECURITY,
            HeadsUpKind.TRAVEL_ADVISORY,
            HeadsUpKind.STRONG_WIND,
            HeadsUpKind.PUBLIC_HOLIDAY,
            HeadsUpKind.RAIN_SOON,
            HeadsUpKind.FUEL_NEARBY,
        ).inOrder()
        assertThat(roadTrip).containsExactly(
            HeadsUpKind.REGIONAL_SECURITY,
            HeadsUpKind.STRONG_WIND,
            HeadsUpKind.TRAVEL_ADVISORY,
            HeadsUpKind.PUBLIC_HOLIDAY,
            HeadsUpKind.FUEL_NEARBY,
            HeadsUpKind.RAIN_SOON,
        ).inOrder()
    }

    @Test
    fun `the tie-break order decides between heads-ups of the same severity`() {
        // Wind caution, advisory caution, holiday caution: wind leads for road trips, the advisory for nomads.
        val snapshot = snapshot(
            alerts = alerts(TravelAlertKind.ADVISORY to TravelAlertSeverity.CAUTION),
            weather = weather(windKph = 45.0),
            localInfo = localInfo(holiday(0)),
        )

        assertThat(insights(snapshot, settings(profile = TravelProfile.NOMAD)).kinds()).containsExactly(
            HeadsUpKind.TRAVEL_ADVISORY,
            HeadsUpKind.STRONG_WIND,
            HeadsUpKind.PUBLIC_HOLIDAY,
        ).inOrder()
        assertThat(insights(snapshot, settings(profile = TravelProfile.ROAD_TRIP)).kinds()).containsExactly(
            HeadsUpKind.STRONG_WIND,
            HeadsUpKind.TRAVEL_ADVISORY,
            HeadsUpKind.PUBLIC_HOLIDAY,
        ).inOrder()
    }

    @Test
    fun `severity stays primary for the road trip profile`() {
        // Wind caution would lead a road trip, but an attention security alert outranks it.
        val snapshot = snapshot(
            alerts = alerts(TravelAlertKind.SECURITY to TravelAlertSeverity.CRITICAL),
            weather = weather(windKph = 45.0),
            fuel = fuel(diesel = station(FuelType.DIESEL)),
        )

        assertThat(insights(snapshot, settings(profile = TravelProfile.ROAD_TRIP)).kinds()).containsExactly(
            HeadsUpKind.REGIONAL_SECURITY,
            HeadsUpKind.STRONG_WIND,
            HeadsUpKind.FUEL_NEARBY,
        ).inOrder()
    }

    @Test
    fun `every item carries compact status text`() {
        val snapshot = snapshot(
            alerts = alerts(
                TravelAlertKind.ADVISORY to TravelAlertSeverity.CAUTION,
                TravelAlertKind.SECURITY to TravelAlertSeverity.CAUTION,
            ),
            weather = weather(windKph = 45.0, slots = listOf(slot(hours = 2.0, rain = 80)), uvIndex = 9),
            localInfo = localInfo(holiday(0)),
            fuel = fuel(diesel = station(FuelType.DIESEL)),
            airQuality = airQuality(aqi = 86),
            travelContext = ParisLocation,
        )

        val nomad = insights(snapshot)

        // Every kind except the data cap (no Data Guard), the weather alert (no provider), daylight
        // (road trip only) and local rhythm (no period around this date).
        assertThat(nomad.kinds().toSet()).containsExactly(
            HeadsUpKind.TRAVEL_ADVISORY,
            HeadsUpKind.REGIONAL_SECURITY,
            HeadsUpKind.STRONG_WIND,
            HeadsUpKind.RAIN_SOON,
            HeadsUpKind.UV_INDEX,
            HeadsUpKind.AIR_QUALITY,
            HeadsUpKind.PUBLIC_HOLIDAY,
            HeadsUpKind.FUEL_NEARBY,
        )
        // The local-rhythm heads-up needs a country with a period around the date, which 2026-10-12 has none for.
        val rhythm = insights(
            snapshot(travelContext = rhythmLocation("CN")),
            at = parisInstant(2027, 1, 30, 12, 0),
        )
        assertThat(rhythm.kinds()).containsExactly(HeadsUpKind.LOCAL_RHYTHM)

        assertThat((nomad + rhythm).map { it.statusText }).containsNoneOf("", " ")
        // "Holiday today" is the longest at 13 characters, as on macOS.
        assertThat((nomad + rhythm).all { it.statusText.length <= HeadsUpText.STATUS_TEXT_CHARACTER_LIMIT + 1 }).isTrue()
    }

    @Test
    fun `data cap and weather alert are never produced on Android`() {
        val everything = snapshot(
            alerts = alerts(
                TravelAlertKind.ADVISORY to TravelAlertSeverity.CRITICAL,
                TravelAlertKind.SECURITY to TravelAlertSeverity.CRITICAL,
            ),
            weather = weather(windKph = 70.0, slots = listOf(slot(hours = 1.0, rain = 90))),
            localInfo = localInfo(holiday(0)),
            fuel = fuel(diesel = station(FuelType.DIESEL)),
        )

        for (profile in TravelProfile.entries) {
            val kinds = insights(everything, settings(profile = profile)).kinds()
            assertThat(kinds).containsNoneOf(HeadsUpKind.DATA_CAP, HeadsUpKind.WEATHER_ALERT)
        }
    }

    // endregion

    // region Travel alerts

    @Test
    fun `travel alert severity mapping`() {
        fun severities(severity: TravelAlertSeverity) =
            insights(snapshot(alerts = alerts(TravelAlertKind.ADVISORY to severity))).map { it.severity }

        assertThat(severities(TravelAlertSeverity.CLEAR)).isEmpty()
        assertThat(severities(TravelAlertSeverity.INFO)).isEmpty()
        assertThat(severities(TravelAlertSeverity.CAUTION)).containsExactly(HeadsUpSeverity.CAUTION)
        assertThat(severities(TravelAlertSeverity.WARNING)).containsExactly(HeadsUpSeverity.ATTENTION)
        assertThat(severities(TravelAlertSeverity.CRITICAL)).containsExactly(HeadsUpSeverity.ATTENTION)
    }

    @Test
    fun `the advisory uses its summary as the title`() {
        val item = insights(snapshot(alerts = alerts(TravelAlertKind.ADVISORY to TravelAlertSeverity.CAUTION))).single()

        assertThat(item.kind).isEqualTo(HeadsUpKind.TRAVEL_ADVISORY)
        assertThat(item.title).isEqualTo("France: exercise a high degree of caution")
        assertThat(item.statusText).isEqualTo("Advisory")
        assertThat(item.detail).isEqualTo("Source: Smartraveller")
        assertThat(item.relatedCard).isEqualTo(DashboardCardId.TRAVEL_ALERTS)
    }

    @Test
    fun `the advisory detail prefers the detail summary and is truncated`() {
        val long = "x".repeat(60)
        val item = insights(
            snapshot(alerts = alerts(TravelAlertKind.ADVISORY to TravelAlertSeverity.CAUTION, detailSummary = long)),
        ).single()

        assertThat(item.detail).isEqualTo("x".repeat(43) + "…")
    }

    @Test
    fun `security titles follow the severity`() {
        fun title(severity: TravelAlertSeverity) =
            insights(snapshot(alerts = alerts(TravelAlertKind.SECURITY to severity))).single()

        val watch = title(TravelAlertSeverity.CAUTION)
        assertThat(watch.kind).isEqualTo(HeadsUpKind.REGIONAL_SECURITY)
        assertThat(watch.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(watch.title).isEqualTo("Regional security watch")
        assertThat(watch.statusText).isEqualTo("Security")
        assertThat(watch.detail!!.length).isAtMost(HeadsUpText.DETAIL_CHARACTER_LIMIT)
        assertThat(watch.relatedCard).isEqualTo(DashboardCardId.TRAVEL_ALERTS)

        assertThat(title(TravelAlertSeverity.WARNING).title).isEqualTo("Regional security alert")
    }

    @Test
    fun `travel alerts skip stale checking unavailable and disabled signals`() {
        fun count(vararg signals: Pair<TravelAlertKind, TravelAlertSeverity>, status: TravelAlertSignalStatus, enabled: List<TravelAlertKind> = TravelAlertKind.entries) =
            insights(snapshot(alerts = alerts(*signals, status = status, enabledKinds = enabled))).size

        val critical = TravelAlertKind.ADVISORY to TravelAlertSeverity.CRITICAL
        assertThat(count(critical, status = TravelAlertSignalStatus.STALE)).isEqualTo(0)
        assertThat(count(critical, status = TravelAlertSignalStatus.UNAVAILABLE)).isEqualTo(0)
        assertThat(count(critical, status = TravelAlertSignalStatus.CHECKING)).isEqualTo(0)
        assertThat(
            count(critical, status = TravelAlertSignalStatus.READY, enabled = listOf(TravelAlertKind.SECURITY)),
        ).isEqualTo(0)
        assertThat(count(critical, status = TravelAlertSignalStatus.READY)).isEqualTo(1)
    }

    @Test
    fun `travel alerts produce one heads-up per signal type`() {
        val snapshot = snapshot(
            alerts = alerts(
                TravelAlertKind.ADVISORY to TravelAlertSeverity.WARNING,
                TravelAlertKind.SECURITY to TravelAlertSeverity.WARNING,
            ),
        )

        assertThat(insights(snapshot).kinds())
            .containsExactly(HeadsUpKind.TRAVEL_ADVISORY, HeadsUpKind.REGIONAL_SECURITY).inOrder()
    }

    // endregion

    // region Strong wind

    private fun windItem(weather: WeatherSnapshot) =
        insights(snapshot(weather = weather)).firstOrNull { it.kind == HeadsUpKind.STRONG_WIND }

    @Test
    fun `wind thresholds`() {
        assertThat(windItem(weather(windKph = 39.9))).isNull()
        assertThat(windItem(weather(windKph = 40.0))!!.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(windItem(weather(windKph = 59.9))!!.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(windItem(weather(windKph = 60.0))!!.severity).isEqualTo(HeadsUpSeverity.ATTENTION)
    }

    @Test
    fun `wind from the current reading says now`() {
        val item = windItem(weather(windKph = 45.0, windDegrees = 270.0))!!

        assertThat(item.title).isEqualTo("Wind up to 45 km/h")
        assertThat(item.statusText).isEqualTo("Wind 45")
        assertThat(item.detail).isEqualTo("From W now")
        assertThat(item.relatedCard).isEqualTo(DashboardCardId.WEATHER)
    }

    @Test
    fun `wind without a direction omits the compass`() {
        assertThat(windItem(weather(windKph = 45.0, windDegrees = null))!!.detail).isEqualTo("Now")
    }

    @Test
    fun `a wind peak in an upcoming slot uses the slot time and direction`() {
        val item = windItem(
            weather(
                windKph = 20.0,
                windDegrees = 90.0,
                slots = listOf(
                    slot(hours = 1.0, windKph = 30.0, windDegrees = 0.0),
                    slot(hours = 2.0, windKph = 64.4, windDegrees = 225.0),
                    slot(hours = 3.0, windKph = 50.0, windDegrees = 270.0),
                ),
            ),
        )!!

        assertThat(item.severity).isEqualTo(HeadsUpSeverity.ATTENTION)
        assertThat(item.title).isEqualTo("Wind up to 64 km/h")
        assertThat(item.detail).isEqualTo("From SW around 16:00")
    }

    @Test
    fun `a wind tie keeps the earliest reading`() {
        val item = windItem(
            weather(windKph = 50.0, windDegrees = 0.0, slots = listOf(slot(hours = 2.0, windKph = 50.0, windDegrees = 180.0))),
        )!!

        assertThat(item.detail).isEqualTo("From N now")
    }

    @Test
    fun `wind ignores past and beyond six hour slots`() {
        val weather = weather(
            windKph = 10.0,
            slots = listOf(slot(hours = -1.0, windKph = 90.0), slot(hours = 6.5, windKph = 90.0), slot(hours = 12.0, windKph = 90.0)),
        )

        assertThat(windItem(weather)).isNull()
    }

    @Test
    fun `wind includes a slot exactly at the six hour boundary`() {
        val item = windItem(weather(windKph = 10.0, slots = listOf(slot(hours = 6.0, windKph = 55.0, windDegrees = 0.0))))!!

        assertThat(item.detail).isEqualTo("From N around 20:00")
    }

    @Test
    fun `wind compass directions`() {
        fun detail(degrees: Double) = windItem(weather(windKph = 50.0, windDegrees = degrees))!!.detail

        assertThat(detail(0.0)).isEqualTo("From N now")
        assertThat(detail(359.0)).isEqualTo("From N now")
        assertThat(detail(45.0)).isEqualTo("From NE now")
        assertThat(detail(90.0)).isEqualTo("From E now")
        assertThat(detail(135.0)).isEqualTo("From SE now")
        assertThat(detail(180.0)).isEqualTo("From S now")
        assertThat(detail(225.0)).isEqualTo("From SW now")
        assertThat(detail(300.0)).isEqualTo("From NW now")
        assertThat(detail(-90.0)).isEqualTo("From W now")
    }

    @Test
    fun `a stale current wind reading is ignored but the forecast still counts`() {
        val staleFetch = Now.minus(Duration.ofHours(3))

        assertThat(windItem(weather(windKph = 80.0, fetchedAt = staleFetch))).isNull()

        val item = windItem(
            weather(
                windKph = 80.0,
                slots = listOf(slot(hours = 1.0, windKph = 42.0, windDegrees = 90.0)),
                fetchedAt = staleFetch,
            ),
        )!!
        assertThat(item.title).isEqualTo("Wind up to 42 km/h")
    }

    @Test
    fun `a weather snapshot that was never fetched has no current reading`() {
        assertThat(windItem(weather(windKph = 80.0, fetchedAt = null))).isNull()
    }

    @Test
    fun `the wind speed rounds half to even like the macOS formatter`() {
        assertThat(windItem(weather(windKph = 64.5))!!.title).isEqualTo("Wind up to 64 km/h")
        assertThat(windItem(weather(windKph = 65.5))!!.title).isEqualTo("Wind up to 66 km/h")
    }

    @Test
    fun `wind times follow the device zone`() {
        val weather = weather(windKph = 10.0, slots = listOf(slot(hours = 2.0, windKph = 50.0, windDegrees = 0.0)))

        val inLondon = insights(snapshot(weather = weather), zone = ZoneId.of("Europe/London"))
            .single { it.kind == HeadsUpKind.STRONG_WIND }

        // 14:00 UTC is 15:00 in London.
        assertThat(inLondon.detail).isEqualTo("From N around 15:00")
    }

    // endregion

    // region Rain

    private fun rainItem(weather: WeatherSnapshot) =
        insights(snapshot(weather = weather)).firstOrNull { it.kind == HeadsUpKind.RAIN_SOON }

    @Test
    fun `rain below the threshold produces nothing`() {
        assertThat(rainItem(weather(rainChancePercent = 59, slots = listOf(slot(hours = 1.0, rain = 59))))).isNull()
    }

    @Test
    fun `rain from the current reading says now`() {
        val item = rainItem(weather(rainChancePercent = 72, slots = listOf(slot(hours = 1.0, rain = 90))))!!

        assertThat(item.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(item.title).isEqualTo("Rain likely now")
        assertThat(item.statusText).isEqualTo("Rain now")
        assertThat(item.detail).isEqualTo("72% chance")
        assertThat(item.relatedCard).isEqualTo(DashboardCardId.WEATHER)
    }

    @Test
    fun `rain from the first qualifying upcoming slot`() {
        val item = rainItem(
            weather(
                rainChancePercent = 10,
                slots = listOf(slot(hours = 1.0, rain = 30), slot(hours = 2.0, rain = 60), slot(hours = 3.0, rain = 95)),
            ),
        )!!

        assertThat(item.title).isEqualTo("Rain likely from 16:00")
        assertThat(item.statusText).isEqualTo("Rain 16:00")
        assertThat(item.detail).isEqualTo("60% chance")
    }

    @Test
    fun `the rain slot covering now counts as now`() {
        val item = rainItem(
            weather(rainChancePercent = null, slots = listOf(slot(minutes = -30.0, rain = 80), slot(hours = 1.0, rain = 30))),
        )!!

        assertThat(item.title).isEqualTo("Rain likely now")
        assertThat(item.detail).isEqualTo("80% chance")
    }

    @Test
    fun `rain ignores past and beyond six hour slots`() {
        val weather = weather(
            rainChancePercent = 10,
            slots = listOf(slot(hours = -2.0, rain = 95), slot(hours = 6.5, rain = 95), slot(hours = 9.0, rain = 95)),
        )

        assertThat(rainItem(weather)).isNull()
    }

    @Test
    fun `a stale current rain reading is ignored`() {
        val stale = weather(rainChancePercent = 90, fetchedAt = Now.minus(Duration.ofHours(3)))

        assertThat(rainItem(stale)).isNull()
    }

    @Test
    fun `rain and wind can both appear independently`() {
        val snapshot = snapshot(weather = weather(windKph = 45.0, rainChancePercent = 80))

        assertThat(insights(snapshot).kinds()).containsExactly(HeadsUpKind.STRONG_WIND, HeadsUpKind.RAIN_SOON).inOrder()
    }

    // endregion

    // region Public holiday

    private fun holidayItems(
        period: HolidayPeriod?,
        phase: LocalHolidayPhase = LocalHolidayPhase.NEXT,
        at: Instant = Now,
        zone: ZoneId = ParisZone,
    ) = insights(snapshot(localInfo = localInfo(period, phase)), at = at, zone = zone)
        .filter { it.kind == HeadsUpKind.PUBLIC_HOLIDAY }

    @Test
    fun `a holiday today is a caution`() {
        val item = holidayItems(holiday(0, "Armistice")).single()

        assertThat(item.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(item.title).isEqualTo("Public holiday today")
        assertThat(item.statusText).isEqualTo("Holiday today")
        assertThat(item.detail).isEqualTo("Armistice · shops may close")
        assertThat(item.relatedCard).isEqualTo(DashboardCardId.LOCAL_INFO)
    }

    @Test
    fun `a holiday tomorrow and weekday names`() {
        val tomorrow = holidayItems(holiday(1, "Armistice")).single()
        assertThat(tomorrow.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(tomorrow.title).isEqualTo("Public holiday tomorrow")
        assertThat(tomorrow.statusText).isEqualTo("Holiday")
        assertThat(tomorrow.detail).isEqualTo("Armistice")

        // 2026-10-12 is a Monday.
        assertThat(holidayItems(holiday(2)).single().title).isEqualTo("Public holiday Wednesday")
        val inThree = holidayItems(holiday(3)).single()
        assertThat(inThree.title).isEqualTo("Public holiday Thursday")
        assertThat(inThree.severity).isEqualTo(HeadsUpSeverity.INFO)
    }

    @Test
    fun `weekday names follow the injected locale`() {
        val items = insights(
            snapshot(localInfo = localInfo(holiday(2))),
            formats = HeadsUpFormats(locale = Locale.FRANCE),
        )

        assertThat(items.single { it.kind == HeadsUpKind.PUBLIC_HOLIDAY }.title).isEqualTo("Public holiday mercredi")
    }

    @Test
    fun `a holiday further out or past produces nothing`() {
        assertThat(holidayItems(holiday(4))).isEmpty()
        assertThat(holidayItems(holiday(-1))).isEmpty()
        assertThat(holidayItems(null)).isEmpty()
    }

    @Test
    fun `holiday day boundaries use the injected zone`() {
        val period = holiday(0)

        // 00:30 and 23:30 Paris time on the holiday are both "today".
        assertThat(holidayItems(period, at = parisInstant(2026, 10, 12, 0, 30)).single().title).isEqualTo("Public holiday today")
        assertThat(holidayItems(period, at = parisInstant(2026, 10, 12, 23, 30)).single().title).isEqualTo("Public holiday today")

        // 23:30 the evening before the holiday is "tomorrow" in Paris.
        assertThat(holidayItems(period, at = parisInstant(2026, 10, 11, 23, 30)).single().title).isEqualTo("Public holiday tomorrow")

        // 00:30 Paris on the 12th is still the 11th in Los Angeles, so there the holiday is tomorrow.
        val atNight = parisInstant(2026, 10, 12, 0, 30)
        val losAngeles = ZoneId.of("America/Los_Angeles")
        assertThat(holidayItems(period, at = atNight, zone = losAngeles).single().title).isEqualTo("Public holiday tomorrow")
    }

    @Test
    fun `the stored holiday phase is recomputed from the dates`() {
        // Stored state says "tomorrow", but the period is actually today relative to now.
        val item = holidayItems(holiday(0), phase = LocalHolidayPhase.TOMORROW).single()

        assertThat(item.title).isEqualTo("Public holiday today")
    }

    @Test
    fun `a multi-day period that is on counts as today`() {
        val period = HolidayPeriod(
            name = "Long weekend",
            startDate = ParisToday.minusDays(2),
            endDate = ParisToday.plusDays(1),
        )

        assertThat(holidayItems(period, phase = LocalHolidayPhase.ON_BREAK).single().title).isEqualTo("Public holiday today")
    }

    @Test
    fun `long holiday names are truncated`() {
        val item = holidayItems(holiday(0, "The Annual Remembrance Day of the Republic")).single()

        assertThat(item.detail).isEqualTo("The Annual Remembrance Day… · shops may close")
    }

    // endregion

    // region Fuel

    private fun fuelItem(fuel: FuelPriceSnapshot, locale: Locale = Locale.UK) =
        insights(snapshot(fuel = fuel), formats = HeadsUpFormats(locale = locale))
            .firstOrNull { it.kind == HeadsUpKind.FUEL_NEARBY }

    @Test
    fun `the fuel heads-up formats price distance and radius`() {
        val item = fuelItem(fuel(diesel = station(FuelType.DIESEL, price = 1.689, distance = 2.4), radius = 50.0))!!

        assertThat(item.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(item.title).isEqualTo("Diesel €1.689 · 2.4 km")
        assertThat(item.statusText).isEqualTo("€1.689")
        assertThat(item.detail).isEqualTo("Cheapest within 50 km")
        assertThat(item.relatedCard).isEqualTo(DashboardCardId.FUEL_PRICES)
    }

    @Test
    fun `the fuel price follows the locale and leaves the per litre unit out`() {
        val item = fuelItem(fuel(diesel = station(FuelType.DIESEL, price = 1.429, distance = 4.8)), Locale.FRANCE)!!

        assertThat(item.title).isEqualTo("Diesel 1,429 € · 4.8 km")
        assertThat(item.title).doesNotContain("/L")
    }

    @Test
    fun `fuel distance rounds at ten kilometers`() {
        val item = fuelItem(fuel(diesel = station(FuelType.DIESEL, price = 1.5, distance = 12.4)))!!

        assertThat(item.title).endsWith("· 12 km")
    }

    @Test
    fun `fuel prefers diesel then gasoline`() {
        val both = fuelItem(fuel(diesel = station(FuelType.DIESEL), gasoline = station(FuelType.GASOLINE)))!!
        val gasolineOnly = fuelItem(fuel(gasoline = station(FuelType.GASOLINE)))!!

        assertThat(both.title).startsWith("Diesel")
        assertThat(gasolineOnly.title).startsWith("Gasoline")
    }

    @Test
    fun `fuel requires a ready status and a station`() {
        assertThat(fuelItem(fuel(status = FuelPriceStatus.UNAVAILABLE, diesel = station(FuelType.DIESEL)))).isNull()
        assertThat(fuelItem(fuel(status = FuelPriceStatus.NO_STATIONS_FOUND))).isNull()
        assertThat(fuelItem(fuel())).isNull()
    }

    // endregion
}
