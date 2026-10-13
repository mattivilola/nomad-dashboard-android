package com.iloapps.nomaddashboard.core.travel.widget

import com.iloapps.nomaddashboard.core.travel.daylight.ClockTimeFormatter
import com.iloapps.nomaddashboard.core.travel.daylight.DaylightOutlook
import com.iloapps.nomaddashboard.core.travel.format.DurationText
import com.iloapps.nomaddashboard.core.travel.health.HealthLevel
import com.iloapps.nomaddashboard.core.travel.health.HealthSymbol
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** "Updated 12:40", or "Out of date · 12:40" once the app has been silent for 6 hours. */
data class WidgetUpdatedFooter(
    val text: String,
    val accessibilityLabel: String,
    /** Draw the footer in the caution color and dim the content. */
    val isStale: Boolean,
)

/** The Glance widget: the verdict, the place and the top heads-up. */
data class GlanceWidgetContent(
    val verdictTitle: String,
    val verdictSymbol: HealthSymbol,
    val verdictLevel: HealthLevel,
    val placeName: String?,
    /** At most one for the small widget and two for the medium one, most important first. */
    val headsUp: List<WidgetSnapshot.HeadsUp>,
    /** Shown when [headsUp] is empty. */
    val emptyHeadsUpText: String,
    val footer: WidgetUpdatedFooter,
)

/** The Safety widget: the emergency number where you are, and your foreign ministry's consular line. */
data class SafetyWidgetContent(
    /** "Emergency". */
    val emergencyLabel: String,
    /** The number to call first; null when there is no verified number for where you are. */
    val emergencyNumber: String?,
    val countryName: String?,
    /** "Unknown here", shown instead of the number. */
    val unknownTitle: String?,
    /** "No verified number for where you are". */
    val unknownDetail: String?,
    val consularLabel: String,
    /** The consular hotline, null when none is known. */
    val hotline: String?,
    /** Shown instead of the hotline until a citizenship is chosen; null once one is. */
    val citizenshipHint: String?,
    val accessibilityLabel: String,
    val footer: WidgetUpdatedFooter,
)

/** The Daylight widget: sunset and the daylight left, or when it gets light again. */
data class DaylightWidgetContent(
    val kind: Kind,
    /** "Sunset 19:42", "Dark until 06:31", "Midnight sun". */
    val title: String,
    /** "1 h 10 min of daylight left", "Sunrise tomorrow". */
    val detail: String,
    /** Draw the symbol in the caution color: under an hour of daylight left, or content that is out of date. */
    val isCaution: Boolean,
    val footer: WidgetUpdatedFooter,
) {
    /** Which symbol to draw. */
    enum class Kind {
        /** The sun is up; the sunset is the title. */
        DAYLIGHT,

        /** The sun is down and the next sunrise is known. */
        DARK_UNTIL,

        /** The sun is down and the next sunrise is not known. */
        DARK,
        MIDNIGHT_SUN,
        POLAR_NIGHT,

        /** The snapshot has no location with coordinates. */
        LOCATION_NEEDED,

        /** The saved day has run out before the app refreshed it. */
        OUT_OF_DATE,
    }
}

/** What a widget shows until the app has saved its first snapshot. */
object WidgetEmptyState {
    const val TITLE = "Open Nomad Dashboard"
    const val DETAIL = "Your travel info appears here once the app has run."
}

/** The widget gallery names and descriptions, the same as the macOS widgets. */
enum class WidgetKind(val displayName: String, val description: String) {
    GLANCE("Glance", "Your travel verdict, where you are and the most important heads-up."),
    SAFETY("Safety", "The emergency number where you are, and your embassy's consular hotline."),
    DAYLIGHT("Daylight", "When the sun sets and how much daylight is left, or when it gets light again."),
}

/** Which size of the Glance widget to describe. */
enum class GlanceWidgetSize(val maximumHeadsUps: Int, val emptyHeadsUpText: String) {
    SMALL(maximumHeadsUps = 1, emptyHeadsUpText = "No heads-ups"),
    MEDIUM(maximumHeadsUps = WidgetSnapshot.MAXIMUM_HEADS_UPS, emptyHeadsUpText = "Nothing needs your attention"),
}

/**
 * The words and values each widget shows, decided from a [WidgetSnapshot] and the moment it is drawn at.
 * The widget layer only draws them. Strings are the ones the macOS widgets show.
 *
 * Pure: the zone and the clock format are injected so "Updated 12:40" follows the device's time zone
 * and 12/24-hour preference without this module reading any system setting.
 *
 * @param zone the device's time zone, for the "Updated" time.
 * @param clockTime formats a time already expressed in the zone it is about.
 */
class WidgetDisplay(
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clockTime: ClockTimeFormatter = ClockTimeFormatter.localized(Locale.getDefault()),
) {
    fun updatedFooter(snapshot: WidgetSnapshot, at: Instant): WidgetUpdatedFooter {
        val time = clockTime.format(snapshot.savedAt.atZone(zone))
        return if (snapshot.isStale(at)) {
            WidgetUpdatedFooter(
                text = "Out of date · $time",
                accessibilityLabel = "Out of date. Last updated $time",
                isStale = true,
            )
        } else {
            WidgetUpdatedFooter(text = "Updated $time", accessibilityLabel = "Updated $time", isStale = false)
        }
    }

    fun glance(snapshot: WidgetSnapshot, at: Instant, size: GlanceWidgetSize): GlanceWidgetContent =
        GlanceWidgetContent(
            verdictTitle = snapshot.verdict.title,
            verdictSymbol = snapshot.verdict.symbol,
            verdictLevel = snapshot.verdict.level,
            placeName = snapshot.placeName,
            headsUp = snapshot.headsUp.take(size.maximumHeadsUps),
            emptyHeadsUpText = size.emptyHeadsUpText,
            footer = updatedFooter(snapshot, at),
        )

    fun safety(snapshot: WidgetSnapshot, at: Instant): SafetyWidgetContent {
        val emergency = snapshot.emergency
        return SafetyWidgetContent(
            emergencyLabel = "Emergency",
            emergencyNumber = emergency?.generalNumber,
            countryName = emergency?.countryName,
            unknownTitle = if (emergency == null) "Unknown here" else null,
            unknownDetail = if (emergency == null) "No verified number for where you are" else null,
            consularLabel = "Consular line",
            hotline = snapshot.embassy?.hotline,
            citizenshipHint = if (snapshot.embassy == null) {
                "Add your citizenship in Settings for your consular line"
            } else {
                null
            },
            accessibilityLabel = if (emergency == null) {
                "No verified emergency number for where you are"
            } else {
                "Emergency number in ${emergency.countryName}: ${emergency.generalNumber}"
            },
            footer = updatedFooter(snapshot, at),
        )
    }

    /**
     * What the Daylight widget shows at [at]. The remaining daylight is counted from [at], so draw it
     * again (see [WidgetSnapshot.renderingMoments]) whenever the content has to change.
     */
    fun daylight(snapshot: WidgetSnapshot, at: Instant): DaylightWidgetContent {
        val footer = updatedFooter(snapshot, at)
        val daylight = snapshot.daylight
            ?: return DaylightWidgetContent(
                kind = DaylightWidgetContent.Kind.LOCATION_NEEDED,
                title = "Location needed",
                detail = "Open Nomad Dashboard to find where you are",
                isCaution = false,
                footer = footer,
            )

        val outlook = daylight.outlook(fallbackZone = zone)
        return when (val phase = outlook.phaseAt(at)) {
            is DaylightOutlook.Phase.Daylight -> {
                val remaining = Duration.between(at, phase.sunset)
                DaylightWidgetContent(
                    kind = DaylightWidgetContent.Kind.DAYLIGHT,
                    title = "Sunset ${outlook.clockTime(phase.sunset, clockTime)}",
                    detail = "${DurationText.hoursAndMinutes(remaining)} of daylight left",
                    isCaution = remaining <= CAUTION_INTERVAL,
                    footer = footer,
                )
            }

            is DaylightOutlook.Phase.Dark -> {
                val sunrise = phase.sunrise
                if (sunrise == null) {
                    DaylightWidgetContent(
                        kind = DaylightWidgetContent.Kind.DARK,
                        title = "Dark",
                        detail = "The sun has set",
                        isCaution = false,
                        footer = footer,
                    )
                } else {
                    DaylightWidgetContent(
                        kind = DaylightWidgetContent.Kind.DARK_UNTIL,
                        title = "Dark until ${outlook.clockTime(sunrise, clockTime)}",
                        detail = if (outlook.isSameDay(sunrise, at)) "Sunrise today" else "Sunrise tomorrow",
                        isCaution = false,
                        footer = footer,
                    )
                }
            }

            DaylightOutlook.Phase.MidnightSun -> DaylightWidgetContent(
                kind = DaylightWidgetContent.Kind.MIDNIGHT_SUN,
                title = "Midnight sun",
                detail = "The sun stays up today",
                isCaution = false,
                footer = footer,
            )

            DaylightOutlook.Phase.PolarNight -> DaylightWidgetContent(
                kind = DaylightWidgetContent.Kind.POLAR_NIGHT,
                title = "Polar night",
                detail = "The sun stays down today",
                isCaution = false,
                footer = footer,
            )

            null -> DaylightWidgetContent(
                kind = DaylightWidgetContent.Kind.OUT_OF_DATE,
                title = "Out of date",
                detail = "Open Nomad Dashboard to update",
                isCaution = true,
                footer = footer,
            )
        }
    }

    private companion object {
        /** Daylight left at which the symbol turns to the caution color, like the dashboard's daylight heads-up. */
        val CAUTION_INTERVAL: Duration = Duration.ofMinutes(60)
    }
}
