package com.iloapps.nomaddashboard.core.travel.widget

import com.iloapps.nomaddashboard.core.travel.daylight.DaylightOutlook
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpKind
import com.iloapps.nomaddashboard.core.travel.health.HealthLevel
import com.iloapps.nomaddashboard.core.travel.health.HealthSymbol
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The compact, display-ready state the home screen widgets read, ported from the macOS `WidgetSnapshot`.
 *
 * The app writes it to a file in its own storage after each dashboard refresh and the widgets read it
 * from there; nothing is sent anywhere. It deliberately holds only what the widgets show: no IP
 * address, no coordinates, no citizenship codes. The embassy and hotline are shown on the widget, so
 * they are the only trace of the citizenship setting.
 *
 * Serialize it with [WidgetSnapshotCodec], which also rejects a file written by another schema version.
 * Use [of] to build one by hand: it caps the heads-up list.
 */
@Serializable
data class WidgetSnapshot(
    val version: Int = SCHEMA_VERSION,
    val verdict: Verdict,
    /** "Rabat, Morocco". */
    val placeName: String? = null,
    /** At most [MAXIMUM_HEADS_UPS], most important first. */
    val headsUp: List<HeadsUp> = emptyList(),
    val emergency: Emergency? = null,
    val embassy: Embassy? = null,
    val daylight: WidgetDaylight? = null,
    @Serializable(with = InstantIsoSerializer::class) val savedAt: Instant,
) {
    init {
        require(headsUp.size <= MAXIMUM_HEADS_UPS) { "A widget snapshot holds at most $MAXIMUM_HEADS_UPS heads-ups" }
    }

    @Serializable
    data class Verdict(
        /** "Ready", "Caution", "Attention" or "Waiting". */
        val title: String,
        val level: HealthLevel,
    ) {
        /** The symbol the dashboard uses for the same overall verdict. */
        val symbol: HealthSymbol
            get() = when (level) {
                HealthLevel.READY -> HealthSymbol.OVERALL_READY
                HealthLevel.CAUTION -> HealthSymbol.OVERALL_CAUTION
                HealthLevel.ATTENTION -> HealthSymbol.OVERALL_ATTENTION
                HealthLevel.UNAVAILABLE -> HealthSymbol.WAITING
            }
    }

    @Serializable
    enum class Severity {
        @SerialName("info")
        INFO,

        @SerialName("caution")
        CAUTION,

        @SerialName("attention")
        ATTENTION,
        ;

        /**
         * The health level whose status color the severity shares: attention is coral, caution is sand
         * and info is teal, like the glance heads-up rows.
         */
        val level: HealthLevel
            get() = when (this) {
                INFO -> HealthLevel.READY
                CAUTION -> HealthLevel.CAUTION
                ATTENTION -> HealthLevel.ATTENTION
            }
    }

    @Serializable
    data class HeadsUp(
        val title: String,
        /**
         * The [HeadsUpKind] constant's name, which picks the icon. Kept as text so a file written by a
         * build that knew a kind this one does not still reads; see [headsUpKind].
         */
        val kind: String,
        val severity: Severity,
    ) {
        /** The kind this build knows by [kind], null for a name it does not know. */
        val headsUpKind: HeadsUpKind?
            get() = HeadsUpKind.entries.firstOrNull { it.name == kind }
    }

    @Serializable
    data class Emergency(
        val countryName: String,
        /** The number to call first. */
        val generalNumber: String,
    )

    /** The nearest embassy and the consular hotline; only present once a citizenship is chosen. */
    @Serializable
    data class Embassy(
        val name: String? = null,
        val hotline: String? = null,
    )

    fun isStale(at: Instant): Boolean = Duration.between(savedAt, at) > STALE_INTERVAL

    /** The moment the widgets start showing the out-of-date state. */
    val staleDate: Instant
        get() = savedAt.plus(STALE_INTERVAL).plusSeconds(1)

    /** Whether two snapshots show the same thing, ignoring when they were saved. */
    fun hasSameContent(other: WidgetSnapshot): Boolean = copy(savedAt = other.savedAt) == other

    companion object {
        const val SCHEMA_VERSION = 1

        /** The widgets mark their content as out of date after this long without an update from the app. */
        val STALE_INTERVAL: Duration = Duration.ofHours(6)

        const val MAXIMUM_HEADS_UPS = 2

        /**
         * How many heads-up items to ask the engine for. The daylight heads-up is left out of the widgets
         * (see [WidgetSnapshotBuilder]), so one more than [MAXIMUM_HEADS_UPS] keeps both slots filled.
         */
        const val HEADS_UP_FETCH_LIMIT = MAXIMUM_HEADS_UPS + 1

        /** How far ahead a widget plans its renders. */
        val TIMELINE_LOOK_AHEAD: Duration = Duration.ofHours(24)

        /**
         * The Daylight widget writes the daylight left as text ("3 h 11 min"), which a widget cannot keep
         * counting by itself, so it renders again every 5 minutes while the sun is up and every minute in
         * its last hour.
         */
        val DAYLIGHT_COUNTDOWN_STEP: Duration = Duration.ofMinutes(5)
        val DAYLIGHT_COUNTDOWN_FINE_STEP: Duration = Duration.ofMinutes(1)
        val DAYLIGHT_COUNTDOWN_FINE_INTERVAL: Duration = Duration.ofHours(1)

        private const val MAXIMUM_COUNTDOWN_MOMENTS = 1_000

        private fun daylightCountdownMoments(daylight: WidgetDaylight?, now: Instant): List<Instant> {
            val sunset = (daylight?.phaseAt(now) as? DaylightOutlook.Phase.Daylight)?.sunset ?: return emptyList()
            val horizon = now.plus(TIMELINE_LOOK_AHEAD)
            val moments = mutableListOf<Instant>()
            var moment = now
            while (moments.size < MAXIMUM_COUNTDOWN_MOMENTS) {
                val step = if (Duration.between(moment, sunset) <= DAYLIGHT_COUNTDOWN_FINE_INTERVAL) {
                    DAYLIGHT_COUNTDOWN_FINE_STEP
                } else {
                    DAYLIGHT_COUNTDOWN_STEP
                }
                moment = moment.plus(step)
                if (moment >= sunset || moment > horizon) break
                moments += moment
            }
            return moments
        }

        /** A snapshot of the current schema, with [headsUp] capped at [MAXIMUM_HEADS_UPS]. */
        fun of(
            verdict: Verdict,
            savedAt: Instant,
            placeName: String? = null,
            headsUp: List<HeadsUp> = emptyList(),
            emergency: Emergency? = null,
            embassy: Embassy? = null,
            daylight: WidgetDaylight? = null,
        ): WidgetSnapshot = WidgetSnapshot(
            verdict = verdict,
            placeName = placeName,
            headsUp = headsUp.take(MAXIMUM_HEADS_UPS),
            emergency = emergency,
            embassy = embassy,
            daylight = daylight,
            savedAt = savedAt,
        )

        /**
         * [now] first, then every later moment within a day at which a widget has to render again without
         * the app's help: when the content turns out of date and, for the Daylight widget, sunrise and sunset
         * plus the daylight countdown while the sun is up.
         */
        fun renderingMoments(snapshot: WidgetSnapshot?, now: Instant, includesDaylightTransitions: Boolean): List<Instant> {
            if (snapshot == null) return listOf(now)

            val later = buildList {
                add(snapshot.staleDate)
                if (includesDaylightTransitions) {
                    addAll(snapshot.daylight?.transitions.orEmpty())
                    addAll(daylightCountdownMoments(snapshot.daylight, now))
                }
            }
            val horizon = now.plus(TIMELINE_LOOK_AHEAD)
            val upcoming = later.filter { it > now && it <= horizon }.distinct().sorted()
            return listOf(now) + upcoming
        }
    }
}

/**
 * Today's light at a place, kept as plain instants so it can be stored, shown in a widget and replayed
 * later without the coordinates it was computed from. The storable form of [DaylightOutlook].
 */
@Serializable
data class WidgetDaylight(
    @Serializable(with = InstantIsoSerializer::class) val sunrise: Instant? = null,
    @Serializable(with = InstantIsoSerializer::class) val sunset: Instant? = null,
    /** The sunrise of the day after the one this was made for. */
    @Serializable(with = InstantIsoSerializer::class) val nextSunrise: Instant? = null,
    val isPolarDay: Boolean = false,
    val isPolarNight: Boolean = false,
    /** IANA id of the place's time zone, so clock times read the way they do there. */
    val timeZoneIdentifier: String,
) {
    /** The moments at which the phase of the light changes, in order. */
    val transitions: List<Instant>
        get() = listOfNotNull(sunrise, sunset, nextSunrise).sorted()

    /**
     * The outlook this was stored from.
     *
     * @param fallbackZone used when [timeZoneIdentifier] is not a zone this device knows.
     */
    fun outlook(fallbackZone: ZoneId = ZoneId.systemDefault()): DaylightOutlook =
        DaylightOutlook(
            sunrise = sunrise,
            sunset = sunset,
            nextSunrise = nextSunrise,
            isPolarDay = isPolarDay,
            isPolarNight = isPolarNight,
            zone = try {
                ZoneId.of(timeZoneIdentifier)
            } catch (_: DateTimeException) {
                fallbackZone
            },
        )

    /** What the light is doing at [instant], null once [instant] is past the sunrise this knows about. */
    fun phaseAt(instant: Instant, fallbackZone: ZoneId = ZoneId.systemDefault()): DaylightOutlook.Phase? =
        outlook(fallbackZone).phaseAt(instant)

    companion object {
        fun of(outlook: DaylightOutlook): WidgetDaylight =
            WidgetDaylight(
                sunrise = outlook.sunrise,
                sunset = outlook.sunset,
                nextSunrise = outlook.nextSunrise,
                isPolarDay = outlook.isPolarDay,
                isPolarNight = outlook.isPolarNight,
                timeZoneIdentifier = outlook.zone.id,
            )
    }
}

/** An [Instant] as ISO-8601 text such as `2026-10-12T09:30:00Z`: exact to the nanosecond and readable. */
object InstantIsoSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant =
        try {
            Instant.parse(decoder.decodeString())
        } catch (error: DateTimeException) {
            throw SerializationException("Not an ISO-8601 instant", error)
        }
}
