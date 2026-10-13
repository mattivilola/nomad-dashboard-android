package com.iloapps.nomaddashboard.core.travel.widget

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.travel.daylight.ClockTimeFormatter
import com.iloapps.nomaddashboard.core.travel.health.HealthLevel
import com.iloapps.nomaddashboard.core.travel.health.HealthSymbol
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

class WidgetDisplayTest {
    private val utc = ZoneId.of("UTC")
    private val display = WidgetDisplay(zone = utc, clockTime = ClockTimeFormatter.Hour24)

    // 12:40 UTC.
    private val saved = Instant.parse("2026-10-12T12:40:00Z")

    private val sunrise = Instant.parse("2026-10-12T06:31:00Z")
    private val sunset = Instant.parse("2026-10-12T19:42:00Z")
    private val nextSunrise = Instant.parse("2026-10-13T06:33:00Z")

    private fun snapshot(
        emergency: WidgetSnapshot.Emergency? = null,
        embassy: WidgetSnapshot.Embassy? = null,
        daylight: WidgetDaylight? = null,
        headsUp: List<WidgetSnapshot.HeadsUp> = emptyList(),
    ) = WidgetSnapshot.of(
        verdict = WidgetSnapshot.Verdict("Caution", HealthLevel.CAUTION),
        savedAt = saved,
        placeName = "Lisbon, Portugal",
        headsUp = headsUp,
        emergency = emergency,
        embassy = embassy,
        daylight = daylight,
    )

    private val daylight = WidgetDaylight(sunrise, sunset, nextSunrise, timeZoneIdentifier = "UTC")

    // region Footer

    @Test
    fun `the footer says when the app last updated`() {
        val footer = display.updatedFooter(snapshot(), at = saved.plusSeconds(60))

        assertThat(footer.text).isEqualTo("Updated 12:40")
        assertThat(footer.accessibilityLabel).isEqualTo("Updated 12:40")
        assertThat(footer.isStale).isFalse()
    }

    @Test
    fun `the footer turns out of date after six hours`() {
        val footer = display.updatedFooter(snapshot(), at = saved.plus(Duration.ofHours(6)).plusSeconds(1))

        assertThat(footer.text).isEqualTo("Out of date · 12:40")
        assertThat(footer.accessibilityLabel).isEqualTo("Out of date. Last updated 12:40")
        assertThat(footer.isStale).isTrue()
    }

    @Test
    fun `the footer time follows the device zone`() {
        val helsinki = WidgetDisplay(zone = ZoneId.of("Europe/Helsinki"), clockTime = ClockTimeFormatter.Hour24)

        // Helsinki is UTC+3 in October 2026.
        assertThat(helsinki.updatedFooter(snapshot(), saved).text).isEqualTo("Updated 15:40")
    }

    // endregion

    // region Glance

    @Test
    fun `the small glance shows one heads-up and the medium shows two`() {
        val wind = WidgetSnapshot.HeadsUp("Wind up to 48 km/h", "STRONG_WIND", WidgetSnapshot.Severity.CAUTION)
        val holiday = WidgetSnapshot.HeadsUp("Public holiday tomorrow", "PUBLIC_HOLIDAY", WidgetSnapshot.Severity.INFO)
        val snapshot = snapshot(headsUp = listOf(wind, holiday))

        val small = display.glance(snapshot, saved, GlanceWidgetSize.SMALL)
        val medium = display.glance(snapshot, saved, GlanceWidgetSize.MEDIUM)

        assertThat(small.headsUp).containsExactly(wind)
        assertThat(medium.headsUp).containsExactly(wind, holiday).inOrder()
        assertThat(small.verdictTitle).isEqualTo("Caution")
        assertThat(small.verdictSymbol).isEqualTo(HealthSymbol.OVERALL_CAUTION)
        assertThat(small.verdictLevel).isEqualTo(HealthLevel.CAUTION)
        assertThat(small.placeName).isEqualTo("Lisbon, Portugal")
    }

    @Test
    fun `an empty glance says so in the size's own words`() {
        assertThat(display.glance(snapshot(), saved, GlanceWidgetSize.SMALL).emptyHeadsUpText).isEqualTo("No heads-ups")
        assertThat(display.glance(snapshot(), saved, GlanceWidgetSize.MEDIUM).emptyHeadsUpText)
            .isEqualTo("Nothing needs your attention")
    }

    // endregion

    // region Safety

    @Test
    fun `the safety widget shows the number the country and the hotline`() {
        val content = display.safety(
            snapshot(
                emergency = WidgetSnapshot.Emergency("Portugal", "112"),
                embassy = WidgetSnapshot.Embassy("Embassy of Finland in Lisbon", "+358 9 1605 5555"),
            ),
            saved,
        )

        assertThat(content.emergencyLabel).isEqualTo("Emergency")
        assertThat(content.emergencyNumber).isEqualTo("112")
        assertThat(content.countryName).isEqualTo("Portugal")
        assertThat(content.unknownTitle).isNull()
        assertThat(content.consularLabel).isEqualTo("Consular line")
        assertThat(content.hotline).isEqualTo("+358 9 1605 5555")
        assertThat(content.citizenshipHint).isNull()
        assertThat(content.accessibilityLabel).isEqualTo("Emergency number in Portugal: 112")
    }

    @Test
    fun `without a verified number the safety widget says unknown here`() {
        val content = display.safety(snapshot(), saved)

        assertThat(content.emergencyNumber).isNull()
        assertThat(content.unknownTitle).isEqualTo("Unknown here")
        assertThat(content.unknownDetail).isEqualTo("No verified number for where you are")
        assertThat(content.citizenshipHint).isEqualTo("Add your citizenship in Settings for your consular line")
        assertThat(content.accessibilityLabel).isEqualTo("No verified emergency number for where you are")
    }

    @Test
    fun `a citizenship without a hotline shows no hint`() {
        val content = display.safety(snapshot(embassy = WidgetSnapshot.Embassy("Embassy of Sweden", null)), saved)

        assertThat(content.hotline).isNull()
        assertThat(content.citizenshipHint).isNull()
    }

    // endregion

    // region Daylight

    @Test
    fun `in the day the sunset and the daylight left are shown`() {
        val at = sunset.minus(Duration.ofMinutes(130))
        val content = display.daylight(snapshot(daylight = daylight), at)

        assertThat(content.kind).isEqualTo(DaylightWidgetContent.Kind.DAYLIGHT)
        assertThat(content.title).isEqualTo("Sunset 19:42")
        assertThat(content.detail).isEqualTo("2 h 10 min of daylight left")
        assertThat(content.isCaution).isFalse()
    }

    @Test
    fun `the last hour of daylight is caution`() {
        val content = display.daylight(snapshot(daylight = daylight), sunset.minus(Duration.ofMinutes(60)))

        assertThat(content.detail).isEqualTo("1 h of daylight left")
        assertThat(content.isCaution).isTrue()
        assertThat(display.daylight(snapshot(daylight = daylight), sunset.minusSeconds(10)).detail)
            .isEqualTo("1 min of daylight left")
    }

    @Test
    fun `after sunset it is dark until tomorrow's sunrise`() {
        val content = display.daylight(snapshot(daylight = daylight), sunset.plusSeconds(600))

        assertThat(content.kind).isEqualTo(DaylightWidgetContent.Kind.DARK_UNTIL)
        assertThat(content.title).isEqualTo("Dark until 06:33")
        assertThat(content.detail).isEqualTo("Sunrise tomorrow")
        assertThat(content.isCaution).isFalse()
    }

    @Test
    fun `before sunrise it is dark until today's sunrise`() {
        val content = display.daylight(snapshot(daylight = daylight), sunrise.minusSeconds(3_600))

        assertThat(content.title).isEqualTo("Dark until 06:31")
        assertThat(content.detail).isEqualTo("Sunrise today")
    }

    @Test
    fun `a dark sky with no known sunrise says the sun has set`() {
        val open = WidgetDaylight(sunrise = sunrise, sunset = sunset, timeZoneIdentifier = "UTC")
        val content = display.daylight(snapshot(daylight = open), sunset.plusSeconds(60))

        assertThat(content.kind).isEqualTo(DaylightWidgetContent.Kind.DARK)
        assertThat(content.title).isEqualTo("Dark")
        assertThat(content.detail).isEqualTo("The sun has set")
    }

    @Test
    fun `the polar days and nights have their own words`() {
        val day = display.daylight(snapshot(daylight = WidgetDaylight(isPolarDay = true, timeZoneIdentifier = "UTC")), saved)
        val night = display.daylight(snapshot(daylight = WidgetDaylight(isPolarNight = true, timeZoneIdentifier = "UTC")), saved)

        assertThat(day.kind).isEqualTo(DaylightWidgetContent.Kind.MIDNIGHT_SUN)
        assertThat(day.title).isEqualTo("Midnight sun")
        assertThat(day.detail).isEqualTo("The sun stays up today")
        assertThat(night.kind).isEqualTo(DaylightWidgetContent.Kind.POLAR_NIGHT)
        assertThat(night.title).isEqualTo("Polar night")
        assertThat(night.detail).isEqualTo("The sun stays down today")
    }

    @Test
    fun `without a location the daylight widget asks for one`() {
        val content = display.daylight(snapshot(), saved)

        assertThat(content.kind).isEqualTo(DaylightWidgetContent.Kind.LOCATION_NEEDED)
        assertThat(content.title).isEqualTo("Location needed")
        assertThat(content.detail).isEqualTo("Open Nomad Dashboard to find where you are")
    }

    @Test
    fun `a saved day that has run out is out of date`() {
        val content = display.daylight(snapshot(daylight = daylight), nextSunrise.plusSeconds(1))

        assertThat(content.kind).isEqualTo(DaylightWidgetContent.Kind.OUT_OF_DATE)
        assertThat(content.title).isEqualTo("Out of date")
        assertThat(content.detail).isEqualTo("Open Nomad Dashboard to update")
        assertThat(content.isCaution).isTrue()
    }

    // endregion

    @Test
    fun `the empty state and gallery text are the macOS words`() {
        assertThat(WidgetEmptyState.TITLE).isEqualTo("Open Nomad Dashboard")
        assertThat(WidgetEmptyState.DETAIL).isEqualTo("Your travel info appears here once the app has run.")
        assertThat(WidgetKind.GLANCE.displayName).isEqualTo("Glance")
        assertThat(WidgetKind.SAFETY.description)
            .isEqualTo("The emergency number where you are, and your embassy's consular hotline.")
        assertThat(WidgetKind.DAYLIGHT.description)
            .isEqualTo("When the sun sets and how much daylight is left, or when it gets light again.")
    }

    @Test
    fun `the gallery sample is a complete believable snapshot`() {
        val sample = WidgetSamples.snapshot(saved, utc)

        assertThat(sample.verdict.title).isEqualTo("Ready")
        assertThat(sample.headsUp).hasSize(2)
        assertThat(sample.headsUp.map { it.headsUpKind }).doesNotContain(null)
        assertThat(sample.emergency?.generalNumber).isEqualTo("112")
        assertThat(sample.daylight?.sunset).isEqualTo(Instant.parse("2026-10-12T19:42:00Z"))
        assertThat(WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(sample))).isEqualTo(sample)
    }
}
