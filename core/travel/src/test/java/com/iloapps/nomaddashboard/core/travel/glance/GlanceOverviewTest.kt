package com.iloapps.nomaddashboard.core.travel.glance

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.InternetState
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.model.WeatherDayForecast
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import com.iloapps.nomaddashboard.core.travel.daylight.ClockTimeFormatter
import com.iloapps.nomaddashboard.core.travel.format.FormattedMetric
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpItem
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpKind
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpSeverity
import com.iloapps.nomaddashboard.core.travel.health.DashboardHealthEvaluator
import com.iloapps.nomaddashboard.core.travel.health.HealthLevel
import com.iloapps.nomaddashboard.core.travel.health.HealthSymbol
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Test

class GlanceOverviewTest {
    // 2026-10-12 09:30 UTC: 11:30 in Madrid, 12:30 in Helsinki.
    private val now = Instant.parse("2026-10-12T09:30:00Z")
    private val madrid = ZoneId.of("Europe/Madrid")
    private val today = LocalDate.of(2026, 10, 12)

    // region Header

    @Test
    fun `header headline is the device city with detail variants from long to short`() {
        val header = GlanceHeader.of(
            device = GlancePlace("Saint-Jean-de-Luz", "Nouvelle-Aquitaine", "France"),
            ip = GlancePlace("Paris", "Île-de-France", "France"),
        )

        assertThat(header.headline).isEqualTo("Saint-Jean-de-Luz")
        assertThat(header.detailVariants).containsExactly(
            GlanceHeader.DetailVariant("Nouvelle-Aquitaine, France", "Paris, France"),
            GlanceHeader.DetailVariant("France", "Paris, France"),
            GlanceHeader.DetailVariant("France", "Paris"),
        ).inOrder()
    }

    @Test
    fun `header falls back to the IP city and shows only the IP part`() {
        val header = GlanceHeader.of(device = null, ip = GlancePlace("Lisbon", country = "Portugal"))

        assertThat(header.headline).isEqualTo("Lisbon")
        assertThat(header.detailVariants).containsExactly(
            GlanceHeader.DetailVariant(null, "Lisbon, Portugal"),
            GlanceHeader.DetailVariant(null, "Lisbon"),
        ).inOrder()
    }

    @Test
    fun `header falls back to generic text without any location`() {
        val header = GlanceHeader.of(device = null, ip = null)

        assertThat(header.headline).isEqualTo(GlanceHeader.FALLBACK_HEADLINE)
        assertThat(header.headline).isEqualTo("Travel-ready system telemetry")
        assertThat(header.detailVariants).isEmpty()
    }

    @Test
    fun `header detail skips parts that repeat the city and collapses duplicates`() {
        val header = GlanceHeader.of(
            device = GlancePlace("Singapore", "Singapore", "Singapore"),
            ip = GlancePlace("Singapore", country = "Singapore"),
        )

        assertThat(header.headline).isEqualTo("Singapore")
        assertThat(header.detailVariants).containsExactly(
            GlanceHeader.DetailVariant(null, "Singapore, Singapore"),
            GlanceHeader.DetailVariant(null, "Singapore"),
        ).inOrder()
    }

    @Test
    fun `header detail without a region starts at the country`() {
        val header = GlanceHeader.of(
            device = GlancePlace("Tbilisi", country = "Georgia"),
            ip = GlancePlace("Frankfurt", country = "Germany"),
        )

        assertThat(header.detailVariants).containsExactly(
            GlanceHeader.DetailVariant("Georgia", "Frankfurt, Germany"),
            GlanceHeader.DetailVariant("Georgia", "Frankfurt"),
        ).inOrder()
    }

    @Test
    fun `header ignores blank location parts`() {
        val header = GlanceHeader.of(
            device = GlancePlace("  ", null, "Spain"),
            ip = GlancePlace("Madrid", country = " "),
        )

        assertThat(header.headline).isEqualTo("Madrid")
        assertThat(header.detailVariants).containsExactly(GlanceHeader.DetailVariant("Spain", "Madrid"))
    }

    @Test
    fun `the caption shortens in steps so the IP part is never cut`() {
        val header = GlanceHeader.of(
            device = GlancePlace("Saint-Jean-de-Luz", "Nouvelle-Aquitaine", "France"),
            ip = GlancePlace("Paris", country = "France"),
        )

        assertThat(header.variantThatFits { true }?.device).isEqualTo("Nouvelle-Aquitaine, France")
        assertThat(header.variantThatFits { it.device == "France" }?.ip).isEqualTo("Paris, France")
        assertThat(header.variantThatFits { it.ip == "Paris" }?.device).isEqualTo("France")
        // When nothing fits, the shortest variant wins.
        assertThat(header.variantThatFits { false }).isEqualTo(GlanceHeader.DetailVariant("France", "Paris"))
        assertThat(GlanceHeader.of(null, null).variantThatFits { true }).isNull()
    }

    @Test
    fun `the header reads device and IP places from the travel context`() {
        val header = GlanceHeader.of(
            TravelContextSnapshot(
                deviceCity = "Valencia",
                deviceRegion = "Valencian Community",
                deviceCountry = "Spain",
                city = "Madrid",
                country = "Spain",
            ),
        )

        assertThat(header.headline).isEqualTo("Valencia")
        assertThat(header.detailVariants.first())
            .isEqualTo(GlanceHeader.DetailVariant("Valencian Community, Spain", "Madrid, Spain"))
    }

    // endregion

    // region Verdict

    private fun verdict(
        connectivity: ConnectivitySnapshot = onlineConnectivity(),
        power: PowerSnapshot = chargedPower(),
    ): GlanceVerdict {
        val health = DashboardHealthEvaluator.evaluate(connectivity, power)
        return GlanceVerdict.of(health, connectivity.vpnActive, connectivity.isOffline)
    }

    @Test
    fun `verdict on an unvalidated network is workable with a sign-in hint`() {
        val verdict = verdict(connectivity = onlineConnectivity(internetState = InternetState.CAPTIVE_OR_UNVALIDATED, latency = null))

        assertThat(verdict.title).isEqualTo("Workable")
        assertThat(verdict.detail).isEqualTo("Network needs sign-in or isn't verified")
        assertThat(verdict.level).isEqualTo(HealthLevel.CAUTION)
    }

    @Test
    fun `verdict reports offline regardless of health`() {
        val verdict = verdict(connectivity = onlineConnectivity(internetState = InternetState.OFFLINE))

        assertThat(verdict.title).isEqualTo("You're offline")
        assertThat(verdict.detail).isEqualTo("Saved essentials are available")
        assertThat(verdict.symbol).isEqualTo(HealthSymbol.OFFLINE)
        assertThat(verdict.level).isEqualTo(HealthLevel.CAUTION)
    }

    @Test
    fun `verdict is ready to work with the network reason and the VPN`() {
        val plain = verdict()
        val withVpn = verdict(connectivity = onlineConnectivity(vpn = true))

        assertThat(plain.title).isEqualTo("Ready to work")
        assertThat(plain.detail).isEqualTo("Stable on Nomad Hub")
        assertThat(plain.level).isEqualTo(HealthLevel.READY)
        assertThat(withVpn.detail).isEqualTo("Stable on Nomad Hub · VPN on")
    }

    @Test
    fun `verdict is workable with the overall reason on caution`() {
        val verdict = verdict(connectivity = onlineConnectivity(latency = 80.0))

        assertThat(verdict.title).isEqualTo("Workable")
        assertThat(verdict.detail).isEqualTo("Network: Latency 80 ms")
        assertThat(verdict.level).isEqualTo(HealthLevel.CAUTION)
    }

    @Test
    fun `verdict needs attention with the overall reason`() {
        val verdict = verdict(connectivity = onlineConnectivity(latency = 200.0))

        assertThat(verdict.title).isEqualTo("Needs attention")
        assertThat(verdict.detail).isEqualTo("Network: Latency 200 ms")
        assertThat(verdict.level).isEqualTo(HealthLevel.ATTENTION)
    }

    @Test
    fun `verdict is checking while conditions are unknown`() {
        val verdict = verdict(connectivity = onlineConnectivity(latency = null))

        assertThat(verdict.title).isEqualTo("Checking conditions")
        assertThat(verdict.level).isEqualTo(HealthLevel.UNAVAILABLE)
    }

    // endregion

    // region Clocks

    private fun clocks(device: String?, home: String?, at: Instant = now, zone: ZoneId = madrid) =
        GlanceClocks.of(device, home, at, zone, ClockTimeFormatter.Hour24)

    @Test
    fun `here city prefers the device city over the time zone city`() {
        assertThat(GlanceClocks.hereCity("Saint-Jean-de-Luz", "Europe/Paris")).isEqualTo("Saint-Jean-de-Luz")
        assertThat(GlanceClocks.hereCity(null, "America/Los_Angeles")).isEqualTo("Los Angeles")
        assertThat(GlanceClocks.hereCity(" ", "Europe/Paris")).isEqualTo("Paris")
    }

    @Test
    fun `clocks read here and home with their local times`() {
        val clocks = clocks(device = "Valencia", home = "Europe/Helsinki")

        assertThat(clocks.here.text).isEqualTo("Here Valencia 11:30")
        assertThat(clocks.home?.text).isEqualTo("Home Helsinki 12:30")
        assertThat(clocks.relativeDayText).isEqualTo("Same day at home")
    }

    @Test
    fun `clocks compare the calendar day with home`() {
        val night = Instant.parse("2026-10-12T22:30:00Z")

        // Madrid has already reached 13 Oct (00:30), Los Angeles is still on 12 Oct (15:30).
        val hereIsLater = clocks(device = null, home = "America/Los_Angeles", at = night)
        assertThat(hereIsLater.relativeDayText).isEqualTo("Tomorrow at home")
        assertThat(hereIsLater.home?.city).isEqualTo("Los Angeles")

        // Los Angeles is on 12 Oct (15:30) while Auckland is already on 13 Oct (11:30).
        val hereIsEarlier = clocks(device = null, home = "Pacific/Auckland", at = night, zone = ZoneId.of("America/Los_Angeles"))
        assertThat(hereIsEarlier.relativeDayText).isEqualTo("Yesterday at home")
        assertThat(hereIsEarlier.here.city).isEqualTo("Los Angeles")
    }

    @Test
    fun `clocks without a valid home zone show only here`() {
        assertThat(clocks(device = "Valencia", home = null).home).isNull()
        assertThat(clocks(device = "Valencia", home = "Not/AZone").relativeDayText).isNull()
        assertThat(clocks(device = "Valencia", home = " ").home).isNull()
    }

    // endregion

    // region Internet and power tiles

    @Test
    fun `internet tile shows latency throughput and the network name`() {
        val tile = internetTile(onlineConnectivity(latency = 20.0, wifiName = "Cafe Wifi", down = 2.0, up = 0.5))

        assertThat(tile.title).isEqualTo("Internet")
        assertThat(tile.titleDetail).isEqualTo("Cafe Wifi")
        assertThat(tile.value).isEqualTo(FormattedMetric("20", "ms"))
        assertThat(tile.value.text).isEqualTo("20 ms")
        assertThat(tile.secondary).isEqualTo("2.0 ↓ · 0.5 ↑ Mbps")
        assertThat(tile.level).isEqualTo(HealthLevel.READY)
        assertThat(tile.scrollTarget).isEqualTo(DashboardCardId.CONNECTIVITY)
        assertThat(internetTile(onlineConnectivity(down = 96.0, up = 19.2)).secondary).isEqualTo("96.0 ↓ · 19.2 ↑ Mbps")
    }

    @Test
    fun `internet tile without a name or throughput falls back to the network reason`() {
        val tile = internetTile(onlineConnectivity(wifiName = null, down = null, up = null))

        assertThat(tile.titleDetail).isNull()
        assertThat(tile.secondary).isEqualTo("Connection looks stable")
    }

    @Test
    fun `internet tile shows waiting before the first latency sample`() {
        val tile = internetTile(onlineConnectivity(latency = null))

        assertThat(tile.value.text).isEqualTo("Waiting")
        assertThat(tile.level).isNull()
    }

    @Test
    fun `internet tile shows offline as attention`() {
        val tile = internetTile(onlineConnectivity(internetState = InternetState.OFFLINE, latency = null))

        assertThat(tile.value.text).isEqualTo("Offline")
        assertThat(tile.level).isEqualTo(HealthLevel.ATTENTION)
        assertThat(tile.icon).isEqualTo(GlanceTileIcon.WIFI_OFF)
        assertThat(tile.secondary).isEqualTo("No network route")
    }

    @Test
    fun `internet tile is tinted by the network health`() {
        assertThat(internetTile(onlineConnectivity(latency = 80.0)).level).isEqualTo(HealthLevel.CAUTION)
        assertThat(internetTile(onlineConnectivity(latency = 200.0)).level).isEqualTo(HealthLevel.ATTENTION)
    }

    @Test
    fun `power tile shows charge and summary with the health tone`() {
        val onBattery = powerTile(batteryPower(72, minutes = 150))
        val lowBattery = powerTile(batteryPower(10, minutes = 20))
        val charging = powerTile(PowerSnapshot(batteryPercent = 50, charging = true, statusLabel = "Charging", minutesToFull = 13))
        val pluggedIn = powerTile(PowerSnapshot(batteryPercent = 100, charging = true, statusLabel = "Full"))

        assertThat(onBattery.value.text).isEqualTo("72%")
        assertThat(onBattery.secondary).isEqualTo("2h 30m left")
        assertThat(onBattery.scrollTarget).isEqualTo(DashboardCardId.POWER)
        assertThat(onBattery.icon).isEqualTo(GlanceTileIcon.BATTERY)
        assertThat(lowBattery.level).isEqualTo(HealthLevel.ATTENTION)
        assertThat(charging.secondary).isEqualTo("Charging · 13m to full")
        assertThat(charging.level).isEqualTo(HealthLevel.READY)
        assertThat(charging.icon).isEqualTo(GlanceTileIcon.BATTERY_CHARGING)
        assertThat(pluggedIn.secondary).isEqualTo("Plugged in")
        assertThat(pluggedIn.icon).isEqualTo(GlanceTileIcon.PLUGGED_IN)
    }

    @Test
    fun `power tile handles missing battery data`() {
        val tile = powerTile(PowerSnapshot())

        assertThat(tile.value.text).isEqualTo("—")
        assertThat(tile.secondary).isEqualTo("Estimating")
        assertThat(tile.level).isNull()
    }

    // endregion

    // region VPN tile

    @Test
    fun `the VPN tile is the third tile and shows the VPN state with the IP city`() {
        val active = GlanceVitalTile.vpn(
            snapshot(connectivity = onlineConnectivity(vpn = true), context = TravelContextSnapshot(city = "Zurich")),
        )
        val inactive = GlanceVitalTile.vpn(snapshot(connectivity = onlineConnectivity(vpn = false)))

        assertThat(active.kind).isEqualTo(GlanceVitalTile.Kind.VPN)
        assertThat(active.value.text).isEqualTo("On")
        assertThat(active.secondary).isEqualTo("Zurich")
        assertThat(active.level).isEqualTo(HealthLevel.READY)
        assertThat(active.scrollTarget).isEqualTo(DashboardCardId.TRAVEL_CONTEXT)
        assertThat(inactive.value.text).isEqualTo("Off")
        assertThat(inactive.level).isNull()
        assertThat(inactive.secondary).isEqualTo("IP location unknown")
    }

    @Test
    fun `the VPN tile falls back to the public IP and to checking before the first reading`() {
        val withIp = GlanceVitalTile.vpn(snapshot(context = TravelContextSnapshot(publicIp = "203.0.113.42")))
        val checking = GlanceVitalTile.vpn(snapshot(connectivity = ConnectivitySnapshot()))

        assertThat(withIp.secondary).isEqualTo("203.0.113.42")
        assertThat(checking.value.text).isEqualTo("Checking")
        assertThat(checking.level).isNull()
    }

    // endregion

    // region Weather tile

    private fun forecast(date: LocalDate, summary: String = "Partly Cloudy", min: Double? = 12.0, max: Double? = 24.0) =
        WeatherDayForecast(date = date, summary = summary, minCelsius = min, maxCelsius = max, rainChancePercent = 0)

    private fun weather(
        condition: String = "Partly Cloudy",
        days: List<WeatherDayForecast> = emptyList(),
        temperature: Double? = 18.0,
    ) = WeatherSnapshot(
        currentTemperatureCelsius = temperature,
        conditionDescription = condition,
        weatherCode = 2,
        rainChancePercent = 90,
        windSpeedKph = 70.0,
        dailyForecast = days,
        fetchedAt = now,
    )

    @Test
    fun `weather tile shows temperature condition and today's range`() {
        val tile = GlanceVitalTile.weather(
            snapshot(weather = weather(days = listOf(forecast(today)))),
            AppSettings(),
            today,
        )

        assertThat(tile.value.text).isEqualTo("18°")
        assertThat(tile.conditionWeatherCode).isEqualTo(2)
        assertThat(tile.secondary).isEqualTo("Partly Cloudy · 12° / 24°")
        assertThat(tile.level).isNull()
        assertThat(tile.scrollTarget).isEqualTo(DashboardCardId.WEATHER)
    }

    @Test
    fun `weather tile does not repeat rain or wind`() {
        assertThat(GlanceVitalTile.weatherSecondary(weather(), today)).isEqualTo("Partly Cloudy")
    }

    @Test
    fun `weather tile uses only the condition when today's range is unknown`() {
        val otherDay = weather(days = listOf(forecast(today.plusDays(3))))
        val noBounds = weather(days = listOf(forecast(today, min = null, max = null)))
        val oneBound = weather(days = listOf(forecast(today, min = null, max = 24.0)))

        assertThat(GlanceVitalTile.weatherSecondary(otherDay, today)).isEqualTo("Partly Cloudy")
        assertThat(GlanceVitalTile.weatherSecondary(noBounds, today)).isEqualTo("Partly Cloudy")
        assertThat(GlanceVitalTile.weatherSecondary(oneBound, today)).isEqualTo("Partly Cloudy · Estimating / 24°")
    }

    @Test
    fun `weather tile falls back to tomorrow without a condition`() {
        val withTomorrow = weather(condition = " ", days = listOf(forecast(today.plusDays(1), summary = "Sunny")))

        assertThat(GlanceVitalTile.weatherSecondary(withTomorrow, today)).isEqualTo("Tomorrow · Sunny")
        assertThat(GlanceVitalTile.weatherSecondary(weather(condition = " "), today)).isEqualTo("Unavailable")
    }

    @Test
    fun `weather tile explains why weather is unavailable`() {
        val unavailable = snapshot()
        val withIpLocation = snapshot(context = TravelContextSnapshot(latitude = 40.0, longitude = -3.0))
        val deviceOnly = snapshot(context = TravelContextSnapshot(deviceLatitude = 40.0, deviceLongitude = -3.0))

        val none = GlanceVitalTile.weather(unavailable, AppSettings(), today)
        assertThat(none.value.text).isEqualTo("—")
        assertThat(none.secondary).isEqualTo("Location needed")
        assertThat(GlanceVitalTile.weather(withIpLocation, AppSettings(), today).secondary).isEqualTo("Unavailable")
        // The device location only counts while the user lets weather use it.
        assertThat(GlanceVitalTile.weather(deviceOnly, AppSettings(useCurrentLocationForWeather = false), today).secondary)
            .isEqualTo("Location needed")
        assertThat(GlanceVitalTile.weather(deviceOnly, AppSettings(useCurrentLocationForWeather = true), today).secondary)
            .isEqualTo("Unavailable")
    }

    @Test
    fun `tiles are ordered internet power VPN weather`() {
        val snapshot = snapshot()
        val health = DashboardHealthEvaluator.evaluate(snapshot.connectivity, snapshot.power)
        val tiles = GlanceVitalTile.tiles(snapshot, AppSettings(), health, isOffline = false, today = today)

        assertThat(tiles.map { it.kind }).containsExactly(
            GlanceVitalTile.Kind.INTERNET,
            GlanceVitalTile.Kind.POWER,
            GlanceVitalTile.Kind.VPN,
            GlanceVitalTile.Kind.WEATHER,
        ).inOrder()
        assertThat(tiles.map { it.scrollTarget }).containsExactly(
            DashboardCardId.CONNECTIVITY,
            DashboardCardId.POWER,
            DashboardCardId.TRAVEL_CONTEXT,
            DashboardCardId.WEATHER,
        ).inOrder()
    }

    @Test
    fun `tiles name their value and context for accessibility`() {
        val tile = internetTile(onlineConnectivity(latency = 20.0, wifiName = "Cafe Wifi", down = 2.0, up = 0.5))

        assertThat(tile.accessibilityLabel).isEqualTo("Internet, Cafe Wifi, 20 ms, 2.0 ↓ · 0.5 ↑ Mbps")
    }

    // endregion

    // region Heads-up, links and the whole overview

    private fun item(kind: HeadsUpKind, severity: HeadsUpSeverity, card: DashboardCardId = DashboardCardId.WEATHER) =
        HeadsUpItem(kind, severity, title = kind.name, statusText = "x", detail = "detail", relatedCard = card)

    @Test
    fun `heads-up row tint follows the item severity and info stays neutral`() {
        assertThat(GlanceHeadsUpRow.level(HeadsUpSeverity.ATTENTION)).isEqualTo(HealthLevel.ATTENTION)
        assertThat(GlanceHeadsUpRow.level(HeadsUpSeverity.CAUTION)).isEqualTo(HealthLevel.CAUTION)
        assertThat(GlanceHeadsUpRow.level(HeadsUpSeverity.INFO)).isEqualTo(HealthLevel.READY)
    }

    @Test
    fun `heads-up row keeps the item content and explains its target`() {
        val row = GlanceHeadsUpRow.of(
            HeadsUpItem(
                kind = HeadsUpKind.STRONG_WIND,
                severity = HeadsUpSeverity.CAUTION,
                title = "Wind up to 45 km/h",
                statusText = "Wind 45",
                detail = "From W around 16:00",
                relatedCard = DashboardCardId.WEATHER,
            ),
        )

        assertThat(row.kind).isEqualTo(HeadsUpKind.STRONG_WIND)
        assertThat(row.title).isEqualTo("Wind up to 45 km/h")
        assertThat(row.detail).isEqualTo("From W around 16:00")
        assertThat(row.relatedCard).isEqualTo(DashboardCardId.WEATHER)
        assertThat(row.accessibilityHint).isEqualTo("Shows the Weather card")
    }

    @Test
    fun `every card has a display title`() {
        assertThat(DashboardCardId.entries.map { it.displayTitle }.all { it.isNotBlank() }).isTrue()
        assertThat(DashboardCardId.EMBASSY.displayTitle).isEqualTo("Your Embassy")
        assertThat(DashboardCardId.LOCAL_INFO.displayTitle).isEqualTo("Local Info")
    }

    @Test
    fun `the overview passes through at most three heads-up items and offers the Android links`() {
        val items = listOf(
            item(HeadsUpKind.STRONG_WIND, HeadsUpSeverity.ATTENTION),
            item(HeadsUpKind.RAIN_SOON, HeadsUpSeverity.CAUTION),
            item(HeadsUpKind.PUBLIC_HOLIDAY, HeadsUpSeverity.INFO, DashboardCardId.LOCAL_INFO),
            item(HeadsUpKind.FUEL_NEARBY, HeadsUpSeverity.INFO, DashboardCardId.FUEL_PRICES),
        )

        val overview = builder().build(snapshot(), AppSettings(), items, "Europe/Helsinki", now, madrid)

        assertThat(overview.headsUp.map { it.kind }).containsExactly(
            HeadsUpKind.STRONG_WIND,
            HeadsUpKind.RAIN_SOON,
            HeadsUpKind.PUBLIC_HOLIDAY,
        ).inOrder()
        assertThat(overview.quickLinks.map { it.title }).containsExactly("Workplaces", "Saved essentials").inOrder()
        assertThat(builder().build(snapshot(), AppSettings(), emptyList(), null, now, madrid).headsUp).isEmpty()
    }

    @Test
    fun `the overview composes the header verdict clocks and refresh text`() {
        val snapshot = snapshot(
            context = TravelContextSnapshot(deviceCity = "Valencia", deviceCountry = "Spain", city = "Madrid", country = "Spain"),
            lastRefresh = now,
        )

        val overview = builder().build(snapshot, AppSettings(), emptyList(), "Europe/Helsinki", now, madrid)

        assertThat(overview.header.headline).isEqualTo("Valencia")
        assertThat(overview.verdict.title).isEqualTo("Ready to work")
        assertThat(overview.clocks.here.text).isEqualTo("Here Valencia 11:30")
        assertThat(overview.clocks.home?.text).isEqualTo("Home Helsinki 12:30")
        assertThat(overview.refresh.statusText).isEqualTo("Last refresh 11:30")
        assertThat(overview.refresh.buttonTitle).isEqualTo("Refresh")
        assertThat(overview.refresh.isButtonEnabled).isTrue()
        assertThat(overview.tiles).hasSize(4)
    }

    @Test
    fun `the refresh text reflects waiting and refreshing`() {
        val waiting = builder().build(snapshot(), AppSettings(), emptyList(), null, now, madrid).refresh
        val refreshing = builder().build(snapshot(isRefreshing = true), AppSettings(), emptyList(), null, now, madrid).refresh

        assertThat(waiting.statusText).isEqualTo("Waiting for first refresh")
        assertThat(refreshing.statusText).isEqualTo("Refreshing dashboard…")
        assertThat(refreshing.isButtonEnabled).isFalse()
    }

    // endregion

    // region Fixtures

    private fun builder() = GlanceOverviewBuilder(Locale.UK, ClockTimeFormatter.Hour24)

    private fun onlineConnectivity(
        internetState: InternetState = InternetState.ONLINE,
        latency: Double? = 20.0,
        wifiName: String? = "Nomad Hub",
        down: Double? = 2.0,
        up: Double? = 0.5,
        vpn: Boolean = false,
    ) = ConnectivitySnapshot(
        internetState = internetState,
        isOnline = internetState == InternetState.ONLINE,
        latencyMs = latency,
        jitterMs = null,
        downloadMbps = down,
        uploadMbps = up,
        wifiName = wifiName,
        wifiSignalDbm = -50,
        vpnActive = vpn,
    )

    private fun chargedPower() = PowerSnapshot(batteryPercent = 100, charging = true, statusLabel = "Full")

    private fun batteryPower(percent: Int, minutes: Int?) =
        PowerSnapshot(batteryPercent = percent, statusLabel = "On Battery", minutesRemaining = minutes)

    private fun snapshot(
        connectivity: ConnectivitySnapshot = onlineConnectivity(),
        power: PowerSnapshot = chargedPower(),
        context: TravelContextSnapshot = TravelContextSnapshot(),
        weather: WeatherSnapshot = WeatherSnapshot(),
        lastRefresh: Instant? = null,
        isRefreshing: Boolean = false,
    ) = DashboardSnapshot(
        connectivity = connectivity,
        power = power,
        travelContext = context,
        weather = weather,
        lastRefresh = lastRefresh,
        isRefreshing = isRefreshing,
    )

    private fun internetTile(connectivity: ConnectivitySnapshot): GlanceVitalTile {
        val snapshot = snapshot(connectivity = connectivity)
        val health = DashboardHealthEvaluator.evaluate(connectivity, snapshot.power)
        return GlanceVitalTile.internet(snapshot, health, connectivity.isOffline)
    }

    private fun powerTile(power: PowerSnapshot): GlanceVitalTile {
        val snapshot = snapshot(power = power)
        return GlanceVitalTile.power(snapshot, DashboardHealthEvaluator.evaluate(snapshot.connectivity, power))
    }

    // endregion
}
