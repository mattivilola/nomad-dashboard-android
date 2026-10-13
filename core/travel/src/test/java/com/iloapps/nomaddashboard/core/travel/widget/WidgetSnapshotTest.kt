package com.iloapps.nomaddashboard.core.travel.widget

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.TravelProfile
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.UTC
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.consecutiveDays
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.engine
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.helsinki
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.helsinkiMidday
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.helsinkiZone
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.morocco
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.now
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.place
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.snapshot
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.utcToday
import com.iloapps.nomaddashboard.core.travel.answers.SnapshotDaylight
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpFormats
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpItem
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpKind
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpSeverity
import com.iloapps.nomaddashboard.core.travel.health.HealthLevel
import com.iloapps.nomaddashboard.core.travel.health.HealthSymbol
import com.iloapps.nomaddashboard.core.travel.safety.ConsularContactsDirectory
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Test

private val builder = WidgetSnapshotBuilder(QuickAnswersFixtures.answers())

private fun make(
    snapshot: DashboardSnapshot,
    settings: AppSettings = AppSettings(),
    headsUp: List<HeadsUpItem> = emptyList(),
    at: Instant = now,
    zone: ZoneId = ZoneId.of("UTC"),
): WidgetSnapshot = builder.build(snapshot, settings, headsUp, at, zone)

private fun item(kind: HeadsUpKind, severity: HeadsUpSeverity, title: String) = HeadsUpItem(
    kind = kind,
    severity = severity,
    title = title,
    statusText = "x",
    detail = null,
    relatedCard = DashboardCardId.WEATHER,
)

private fun ready(savedAt: Instant, title: String = "Ready") =
    WidgetSnapshot.of(WidgetSnapshot.Verdict(title, HealthLevel.READY), savedAt)

class WidgetSnapshotContentTest {
    @Test
    fun `carries the verdict place and emergency number`() {
        val widget = make(morocco())

        assertThat(widget.version).isEqualTo(WidgetSnapshot.SCHEMA_VERSION)
        assertThat(widget.verdict).isEqualTo(WidgetSnapshot.Verdict("Ready", HealthLevel.READY))
        assertThat(widget.placeName).isEqualTo("Rabat, Morocco")
        assertThat(widget.emergency).isEqualTo(WidgetSnapshot.Emergency("Morocco", "19"))
        assertThat(widget.savedAt).isEqualTo(now)
    }

    @Test
    fun `an offline phone is attention`() {
        val widget = make(snapshot(offline = true))

        assertThat(widget.verdict).isEqualTo(WidgetSnapshot.Verdict("Attention", HealthLevel.ATTENTION))
        assertThat(widget.verdict.symbol).isEqualTo(HealthSymbol.OVERALL_ATTENTION)
    }

    @Test
    fun `the verdict symbol follows the dashboard's overall symbols`() {
        fun symbol(level: HealthLevel) = WidgetSnapshot.Verdict("x", level).symbol

        assertThat(symbol(HealthLevel.READY)).isEqualTo(HealthSymbol.OVERALL_READY)
        assertThat(symbol(HealthLevel.CAUTION)).isEqualTo(HealthSymbol.OVERALL_CAUTION)
        assertThat(symbol(HealthLevel.ATTENTION)).isEqualTo(HealthSymbol.OVERALL_ATTENTION)
        assertThat(symbol(HealthLevel.UNAVAILABLE)).isEqualTo(HealthSymbol.WAITING)
    }

    @Test
    fun `an unknown place has no place emergency embassy or daylight`() {
        val widget = make(snapshot())

        assertThat(widget.placeName).isNull()
        assertThat(widget.emergency).isNull()
        assertThat(widget.embassy).isNull()
        assertThat(widget.daylight).isNull()
    }

    @Test
    fun `a country without verified numbers has no emergency number`() {
        val widget = make(snapshot(device = place(countryCode = "LA")))

        assertThat(widget.emergency).isNull()
        assertThat(widget.placeName).isEqualTo("Laos")
    }

    @Test
    fun `heads-ups are capped at two and keep kind and severity`() {
        val wind = item(HeadsUpKind.STRONG_WIND, HeadsUpSeverity.ATTENTION, "Wind up to 65 km/h")
        val widget = make(snapshot(), headsUp = listOf(wind))

        assertThat(widget.headsUp).containsExactly(
            WidgetSnapshot.HeadsUp("Wind up to 65 km/h", "STRONG_WIND", WidgetSnapshot.Severity.ATTENTION),
        )
        assertThat(widget.headsUp.single().headsUpKind).isEqualTo(HeadsUpKind.STRONG_WIND)
        assertThat(WidgetSnapshot.MAXIMUM_HEADS_UPS).isEqualTo(2)

        val many = (0 until 5).map { item(HeadsUpKind.RAIN_SOON, HeadsUpSeverity.INFO, "Item $it") }
        assertThat(make(snapshot(), headsUp = many).headsUp.map { it.title }).containsExactly("Item 0", "Item 1").inOrder()
        assertThat(
            WidgetSnapshot.of(widget.verdict, now, headsUp = widget.headsUp + widget.headsUp + widget.headsUp).headsUp,
        ).hasSize(2)
    }

    @Test
    fun `a snapshot cannot be built with more than two heads-ups`() {
        val one = WidgetSnapshot.HeadsUp("x", "RAIN_SOON", WidgetSnapshot.Severity.INFO)
        val error = runCatching { WidgetSnapshot(verdict = ready(now).verdict, headsUp = listOf(one, one, one), savedAt = now) }

        assertThat(error.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `an unknown heads-up kind reads as no kind`() {
        val future = WidgetSnapshot.HeadsUp("x", "SOMETHING_NEW", WidgetSnapshot.Severity.INFO)

        assertThat(future.headsUpKind).isNull()
    }

    @Test
    fun `the daylight heads-up never takes a widget slot`() {
        val snapshot = helsinki()
        val sunset = SnapshotDaylight.outlook(snapshot, helsinkiMidday, helsinkiZone)!!.sunset!!
        val settings = AppSettings(travelProfile = TravelProfile.ROAD_TRIP)
        val nearSunset = sunset.minus(Duration.ofMinutes(30))
        val items = engine.evaluate(
            snapshot = snapshot,
            settings = settings,
            countryDays = emptyList(),
            now = nearSunset,
            zone = helsinkiZone,
            formats = HeadsUpFormats(locale = Locale.UK),
            limit = WidgetSnapshot.HEADS_UP_FETCH_LIMIT,
        )

        // The engine does raise it for the road trip profile...
        assertThat(items.map { it.kind }).contains(HeadsUpKind.DAYLIGHT)
        // ...but the widget leaves it to the Daylight widget, so the snapshot does not change by the minute.
        assertThat(make(snapshot, settings, items, nearSunset, helsinkiZone).headsUp).isEmpty()
    }

    @Test
    fun `stay and Schengen items reach the widget heads-ups`() {
        val widget = builder.build(
            snapshot = morocco(),
            settings = AppSettings(citizenshipCountryCodes = listOf("US"), stayAllowances = mapOf("MA" to 90)),
            countryDays = consecutiveDays(85, endingOn = utcToday, code = "MA"),
            headsUpEngine = engine,
            now = now,
            zone = UTC,
        )

        assertThat(widget.headsUp.map { it.title }).containsExactly("Morocco: 5 days left")
        assertThat(widget.headsUp.first().severity).isEqualTo(WidgetSnapshot.Severity.CAUTION)
        assertThat(widget.headsUp.first().headsUpKind).isEqualTo(HeadsUpKind.STAY_ALLOWANCE)
    }

    @Test
    fun `without recorded days the widget has no stay heads-ups`() {
        val widget = builder.build(
            snapshot = morocco(),
            settings = AppSettings(stayAllowances = mapOf("MA" to 90)),
            countryDays = emptyList(),
            headsUpEngine = engine,
            now = now,
            zone = UTC,
        )

        assertThat(widget.headsUp).isEmpty()
    }

    @Test
    fun `daylight is computed for the current place`() {
        val widget = make(helsinki(), at = helsinkiMidday, zone = helsinkiZone)

        assertThat(widget.daylight?.timeZoneIdentifier).isEqualTo("Europe/Helsinki")
        assertThat(widget.daylight?.sunset).isNotNull()
        assertThat(widget.daylight?.nextSunrise).isNotNull()
    }

    // region Embassy

    @Test
    fun `no citizenship means no embassy`() {
        assertThat(make(morocco()).embassy).isNull()
    }

    @Test
    fun `the embassy carries the nearest mission and the hotline`() {
        val widget = make(morocco(), AppSettings(citizenshipCountryCodes = listOf("FI")))
        val phone = ConsularContactsDirectory.contactsFor("FI")!!.emergencyPhone

        assertThat(widget.embassy).isEqualTo(WidgetSnapshot.Embassy("Embassy of Finland in Rabat", phone))
    }

    @Test
    fun `at home only the hotline remains`() {
        val widget = make(helsinki(), AppSettings(citizenshipCountryCodes = listOf("FI")))
        val phone = ConsularContactsDirectory.contactsFor("FI")!!.emergencyPhone

        assertThat(widget.embassy).isEqualTo(WidgetSnapshot.Embassy(name = null, hotline = phone))
    }

    @Test
    fun `a citizenship with nothing to show leaves the embassy out`() {
        // Kosovo: no bundled consular line, and the test index lists no mission there.
        val widget = make(morocco(), AppSettings(citizenshipCountryCodes = listOf("XK")))

        assertThat(widget.embassy).isNull()
    }

    // endregion

    // region Privacy

    @Test
    fun `the stored JSON holds no IP address coordinates or citizenship code`() {
        val snapshot = snapshot(
            device = place(city = "Rabat", country = "Morocco", countryCode = "MA", latitude = 34.0123, longitude = -6.8456),
            publicIp = "203.0.113.77",
        )
        val widget = make(snapshot, AppSettings(citizenshipCountryCodes = listOf("FI")))

        val json = WidgetSnapshotCodec.encode(widget)

        assertThat(json).doesNotContain("203.0.113.77")
        assertThat(json).doesNotContain("34.0123")
        assertThat(json).doesNotContain("-6.8456")
        assertThat(json).doesNotContain("latitude")
        assertThat(json).doesNotContain("longitude")
        assertThat(json).doesNotContain("\"FI\"")
        assertThat(json).doesNotContain("citizenship")
        assertThat(json).contains("Embassy of Finland in Rabat")
    }

    @Test
    fun `stay heads-ups add no country codes or citizenship to the stored JSON`() {
        val lisbon = snapshot(
            device = place(city = "Lisbon", country = "Portugal", countryCode = "PT", latitude = 38.7223, longitude = -9.1393),
        )
        val widget = builder.build(
            snapshot = lisbon,
            settings = AppSettings(citizenshipCountryCodes = listOf("US"), stayAllowances = mapOf("PT" to 90)),
            countryDays = consecutiveDays(85, endingOn = utcToday, code = "PT"),
            headsUpEngine = engine,
            now = now,
            zone = UTC,
        )
        assertThat(widget.headsUp).isNotEmpty()

        val json = WidgetSnapshotCodec.encode(widget)

        assertThat(json).doesNotContain("\"US\"")
        assertThat(json).doesNotContain("\"PT\"")
        assertThat(json).doesNotContain("citizenship")
        assertThat(json).doesNotContain("38.7223")
        assertThat(json).doesNotContain("latitude")
    }

    @Test
    fun `daylight keeps only times and the zone`() {
        val json = WidgetSnapshotCodec.encode(make(helsinki(), at = helsinkiMidday, zone = helsinkiZone))

        assertThat(json).doesNotContain("60.1699")
        assertThat(json).doesNotContain("24.9384")
        assertThat(json).contains("Europe/Helsinki")
    }

    // endregion

    // region Freshness

    @Test
    fun `stale only after six hours`() {
        val widget = ready(now)

        assertThat(widget.isStale(now)).isFalse()
        assertThat(widget.isStale(now.plus(Duration.ofHours(6)))).isFalse()
        assertThat(widget.isStale(now.plus(Duration.ofHours(6)).plusSeconds(1))).isTrue()
        assertThat(widget.isStale(widget.staleDate)).isTrue()
        assertThat(widget.isStale(widget.staleDate.minusSeconds(2))).isFalse()
    }

    @Test
    fun `a saved time in the future is not stale`() {
        assertThat(ready(now.plusSeconds(3_600)).isStale(now)).isFalse()
    }

    @Test
    fun `same content ignores the saved time`() {
        val first = make(morocco(), at = now)
        val later = make(morocco(), at = now.plusSeconds(500))
        val elsewhere = make(helsinki(), at = now.plusSeconds(500))

        assertThat(first).isNotEqualTo(later)
        assertThat(first.hasSameContent(later)).isTrue()
        assertThat(first.hasSameContent(elsewhere)).isFalse()
    }

    // endregion
}

class WidgetSnapshotTimelineTest {
    private val daylight = WidgetDaylight(
        sunrise = now.minusSeconds(3_600),
        sunset = now.plusSeconds(7_200),
        nextSunrise = now.plusSeconds(40_000),
        timeZoneIdentifier = "UTC",
    )

    private fun snapshot(savedAgo: Long, daylight: WidgetDaylight?) =
        WidgetSnapshot.of(WidgetSnapshot.Verdict("Ready", HealthLevel.READY), now.minusSeconds(savedAgo), daylight = daylight)

    @Test
    fun `without a snapshot there is only now`() {
        assertThat(WidgetSnapshot.renderingMoments(null, now, includesDaylightTransitions = true)).containsExactly(now)
    }

    @Test
    fun `the stale moment is planned while it is ahead`() {
        val fresh = snapshot(savedAgo = 3_600, daylight = null)

        assertThat(WidgetSnapshot.renderingMoments(fresh, now, includesDaylightTransitions = false))
            .containsExactly(now, fresh.staleDate).inOrder()
    }

    @Test
    fun `an already stale snapshot needs no further moment`() {
        val old = snapshot(savedAgo = 7 * 3_600, daylight = daylight)

        assertThat(WidgetSnapshot.renderingMoments(old, now, includesDaylightTransitions = false)).containsExactly(now)
    }

    /** Every 5 minutes up to the last hour before sunset (now + 2 h), then every minute until sunset. */
    private val countdownMoments: List<Instant> =
        ((300L..3_600L step 300L) + (3_660L..7_140L step 60L)).map { now.plusSeconds(it) }

    @Test
    fun `daylight transitions only count for the daylight widget`() {
        val fresh = snapshot(savedAgo = 3_600, daylight = daylight)

        assertThat(WidgetSnapshot.renderingMoments(fresh, now, includesDaylightTransitions = false))
            .containsExactly(now, fresh.staleDate).inOrder()
        // The sunrise before now is skipped; the rest come in time order.
        assertThat(WidgetSnapshot.renderingMoments(fresh, now, includesDaylightTransitions = true))
            .containsExactlyElementsIn(
                listOf(now) + countdownMoments + listOf(daylight.sunset, fresh.staleDate, daylight.nextSunrise),
            ).inOrder()
    }

    @Test
    fun `the daylight left is refreshed every five minutes and every minute in the last hour`() {
        // Sunset is 2 h ahead: every 5 minutes for the first hour, then every minute until sunset.
        val fresh = snapshot(savedAgo = 3_600, daylight = daylight)
        val sunset = now.plusSeconds(7_200)
        val moments = WidgetSnapshot.renderingMoments(fresh, now, includesDaylightTransitions = true)
            .filter { it > now && it < sunset }

        assertThat(moments).hasSize(12 + 59)
        assertThat(moments.first()).isEqualTo(now.plusSeconds(300))
        assertThat(moments[11]).isEqualTo(now.plusSeconds(3_600))
        assertThat(moments[12]).isEqualTo(now.plusSeconds(3_660))
        assertThat(moments.last()).isEqualTo(now.plusSeconds(7_140))
    }

    @Test
    fun `the countdown needs daylight ahead and only counts for the daylight widget`() {
        val night = WidgetDaylight(
            sunrise = now.minusSeconds(40_000),
            sunset = now.minusSeconds(3_600),
            nextSunrise = now.plusSeconds(20_000),
            timeZoneIdentifier = "UTC",
        )
        val afterSunset = snapshot(savedAgo = 0, daylight = night)

        assertThat(WidgetSnapshot.renderingMoments(afterSunset, now, includesDaylightTransitions = true))
            .containsExactly(now, now.plusSeconds(20_000), afterSunset.staleDate).inOrder()

        val fresh = snapshot(savedAgo = 3_600, daylight = daylight)
        assertThat(WidgetSnapshot.renderingMoments(fresh, now, includesDaylightTransitions = false))
            .containsExactly(now, fresh.staleDate).inOrder()
    }

    @Test
    fun `moments beyond a day are left out`() {
        val far = WidgetDaylight(sunset = now.plus(Duration.ofHours(25)), timeZoneIdentifier = "UTC")
        val fresh = snapshot(savedAgo = 0, daylight = far)

        assertThat(WidgetSnapshot.renderingMoments(fresh, now, includesDaylightTransitions = true))
            .containsExactly(now, fresh.staleDate).inOrder()
    }
}

class WidgetSnapshotSeverityTest {
    @Test
    fun `severity maps to the shared status levels`() {
        assertThat(WidgetSnapshot.Severity.INFO.level).isEqualTo(HealthLevel.READY)
        assertThat(WidgetSnapshot.Severity.CAUTION.level).isEqualTo(HealthLevel.CAUTION)
        assertThat(WidgetSnapshot.Severity.ATTENTION.level).isEqualTo(HealthLevel.ATTENTION)
    }
}

class WidgetSnapshotPublishPolicyTest {
    private fun snapshot(title: String = "Ready", offsetSeconds: Long) = ready(now.plusSeconds(offsetSeconds), title)

    @Test
    fun `the first snapshot is always published`() {
        assertThat(WidgetSnapshotPublishPolicy.shouldPublish(snapshot(offsetSeconds = 0), previous = null)).isTrue()
    }

    @Test
    fun `unchanged content waits for the heartbeat`() {
        val previous = snapshot(offsetSeconds = 0)

        assertThat(WidgetSnapshotPublishPolicy.shouldPublish(snapshot(offsetSeconds = 10), previous)).isFalse()
        assertThat(WidgetSnapshotPublishPolicy.shouldPublish(snapshot(offsetSeconds = 29 * 60), previous)).isFalse()
        assertThat(WidgetSnapshotPublishPolicy.shouldPublish(snapshot(offsetSeconds = 30 * 60), previous)).isTrue()
    }

    @Test
    fun `changed content is throttled to once per minute`() {
        val previous = snapshot(offsetSeconds = 0)

        assertThat(WidgetSnapshotPublishPolicy.shouldPublish(snapshot("Caution", 59), previous)).isFalse()
        assertThat(WidgetSnapshotPublishPolicy.shouldPublish(snapshot("Caution", 60), previous)).isTrue()
    }

    @Test
    fun `a clock that moved backwards publishes`() {
        val previous = snapshot(offsetSeconds = 600)

        assertThat(WidgetSnapshotPublishPolicy.shouldPublish(snapshot(offsetSeconds = 0), previous)).isTrue()
    }

    @Test
    fun `the heartbeat is well inside the stale window`() {
        assertThat(WidgetSnapshotPublishPolicy.HEARTBEAT_INTERVAL.multipliedBy(4)).isLessThan(WidgetSnapshot.STALE_INTERVAL)
    }

    @Test
    fun `rebuilding the same dashboard is unchanged content even after a round trip through the file`() {
        val first = make(helsinki(), at = helsinkiMidday, zone = helsinkiZone)
        val stored = WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(first))!!
        val rebuilt = make(helsinki(), at = helsinkiMidday.plusSeconds(120), zone = helsinkiZone)

        assertThat(rebuilt.hasSameContent(stored)).isTrue()
        assertThat(WidgetSnapshotPublishPolicy.shouldPublish(rebuilt, stored)).isFalse()
    }
}

class WidgetSnapshotCodecTest {
    private val sample = WidgetSnapshot.of(
        verdict = WidgetSnapshot.Verdict("Caution", HealthLevel.CAUTION),
        savedAt = Instant.parse("2027-01-15T08:16:40.125Z"),
        placeName = "Rabat, Morocco",
        headsUp = listOf(WidgetSnapshot.HeadsUp("Wind up to 45 km/h", "STRONG_WIND", WidgetSnapshot.Severity.CAUTION)),
        emergency = WidgetSnapshot.Emergency("Morocco", "19"),
        embassy = WidgetSnapshot.Embassy("Embassy of Finland in Rabat", "+358 9 160 55555"),
        daylight = WidgetDaylight(
            sunrise = Instant.parse("2027-01-15T07:00:00.250Z"),
            sunset = Instant.parse("2027-01-15T17:30:00.500Z"),
            nextSunrise = Instant.parse("2027-01-16T07:00:00.123456789Z"),
            timeZoneIdentifier = "Africa/Casablanca",
        ),
    )

    @Test
    fun `round trips exactly including fractional seconds`() {
        assertThat(WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(sample))).isEqualTo(sample)
    }

    @Test
    fun `a minimal snapshot round trips`() {
        val minimal = ready(now)

        assertThat(WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(minimal))).isEqualTo(minimal)
    }

    @Test
    fun `the file names the schema version`() {
        assertThat(WidgetSnapshotCodec.encode(sample)).contains("\"version\":${WidgetSnapshot.SCHEMA_VERSION}")
    }

    @Test
    fun `unreadable or newer files decode to null`() {
        val json = WidgetSnapshotCodec.encode(sample)
        val newer = json.replace("\"version\":${WidgetSnapshot.SCHEMA_VERSION}", "\"version\":${WidgetSnapshot.SCHEMA_VERSION + 1}")

        assertThat(WidgetSnapshotCodec.decode("not json")).isNull()
        assertThat(WidgetSnapshotCodec.decode("")).isNull()
        assertThat(WidgetSnapshotCodec.decode("[]")).isNull()
        assertThat(WidgetSnapshotCodec.decode("{}")).isNull()
        assertThat(newer).isNotEqualTo(json)
        assertThat(WidgetSnapshotCodec.decode(newer)).isNull()
        assertThat(WidgetSnapshotCodec.decode(json.replace("2027-01-15T08:16:40.125Z", "yesterday"))).isNull()
    }

    @Test
    fun `fields added by a newer build of the same schema are ignored`() {
        val json = WidgetSnapshotCodec.encode(sample).replaceFirst("{", "{\"futureField\":true,")

        assertThat(WidgetSnapshotCodec.decode(json)).isEqualTo(sample)
    }

    @Test
    fun `a file with too many heads-ups is unreadable`() {
        val json = WidgetSnapshotCodec.encode(sample)
        val headsUp = "{\"title\":\"x\",\"kind\":\"RAIN_SOON\",\"severity\":\"info\"}"
        val crowded = json.replace(Regex("\"headsUp\":\\[.*?\\]"), "\"headsUp\":[$headsUp,$headsUp,$headsUp]")

        assertThat(WidgetSnapshotCodec.decode(crowded)).isNull()
    }
}

class WidgetDaylightTest {
    private val sunrise = Instant.ofEpochSecond(1_000)
    private val sunset = Instant.ofEpochSecond(5_000)
    private val nextSunrise = Instant.ofEpochSecond(9_000)

    private val daylight = WidgetDaylight(sunrise, sunset, nextSunrise, timeZoneIdentifier = "Europe/Helsinki")

    @Test
    fun `transitions are sorted and skip missing events`() {
        assertThat(daylight.transitions).containsExactly(sunrise, sunset, nextSunrise).inOrder()
        assertThat(WidgetDaylight(isPolarDay = true, timeZoneIdentifier = "UTC").transitions).isEmpty()
    }

    @Test
    fun `the phase follows the stored outlook`() {
        assertThat(daylight.phaseAt(Instant.ofEpochSecond(0)).toString()).contains("Dark")
        assertThat(daylight.phaseAt(Instant.ofEpochSecond(2_000)).toString()).contains("Daylight")
        assertThat(daylight.phaseAt(Instant.ofEpochSecond(9_000))).isNull()
    }

    @Test
    fun `it converts to and from an outlook`() {
        val outlook = daylight.outlook()

        assertThat(outlook.zone).isEqualTo(ZoneId.of("Europe/Helsinki"))
        assertThat(WidgetDaylight.of(outlook)).isEqualTo(daylight)
    }

    @Test
    fun `an unknown zone falls back to the given one`() {
        val unknown = daylight.copy(timeZoneIdentifier = "Mars/Olympus_Mons")

        assertThat(unknown.outlook(fallbackZone = ZoneId.of("UTC")).zone).isEqualTo(ZoneId.of("UTC"))
    }
}
