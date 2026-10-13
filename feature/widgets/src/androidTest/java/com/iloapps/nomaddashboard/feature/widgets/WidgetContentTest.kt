package com.iloapps.nomaddashboard.feature.widgets

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import com.iloapps.nomaddashboard.core.travel.daylight.ClockTimeFormatter
import com.iloapps.nomaddashboard.core.travel.widget.WidgetDisplay
import com.iloapps.nomaddashboard.core.travel.widget.WidgetSamples
import com.iloapps.nomaddashboard.core.travel.widget.WidgetSnapshot
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Test

/** Draws each widget from sample snapshots and checks the words on it, with no launcher involved. */
class WidgetContentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val display = WidgetDisplay(zone = ZoneOffset.UTC, clockTime = ClockTimeFormatter.Hour24)
    private val savedAt = Instant.parse("2026-10-12T12:40:00Z")
    private val snapshot = WidgetSamples.snapshot(savedAt, ZoneOffset.UTC)

    private val small = DpSize(150.dp, 150.dp)
    private val wide = DpSize(300.dp, 150.dp)

    private fun draw(
        widget: NomadGlanceWidget,
        size: DpSize,
        snapshot: WidgetSnapshot?,
        at: Instant = savedAt,
        assertions: androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest.() -> Unit,
    ) = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(size)
        provideComposable { widget.Content(snapshot, display, at) }
        assertions()
    }

    @Test
    fun glanceSmallShowsTheVerdictThePlaceAndOneHeadsUp() = draw(GlanceOverviewWidget(), small, snapshot) {
        onNode(hasText("Ready")).assertExists()
        onNode(hasText("Lisbon, Portugal")).assertExists()
        onNode(hasText("Wind up to 48 km/h")).assertExists()
        onNode(hasText("Public holiday tomorrow")).assertDoesNotExist()
        onNode(hasText("Updated 12:40")).assertExists()
    }

    @Test
    fun glanceWideShowsBothHeadsUps() = draw(GlanceOverviewWidget(), wide, snapshot) {
        onNode(hasText("Wind up to 48 km/h")).assertExists()
        onNode(hasText("Public holiday tomorrow")).assertExists()
    }

    @Test
    fun glanceWithoutHeadsUpsSaysSo() = draw(GlanceOverviewWidget(), small, snapshot.copy(headsUp = emptyList())) {
        onNode(hasText("No heads-ups")).assertExists()
    }

    @Test
    fun aWidgetThatIsSixHoursOldIsMarkedOutOfDate() =
        draw(GlanceOverviewWidget(), small, snapshot, at = savedAt.plus(Duration.ofHours(6)).plusSeconds(2)) {
            onNode(hasText("Out of date · 12:40")).assertExists()
            onNode(hasText("Updated 12:40")).assertDoesNotExist()
        }

    @Test
    fun beforeTheAppHasRunTheWidgetsAskToOpenIt() {
        for (widget in listOf(GlanceOverviewWidget(), SafetyWidget(), DaylightWidget())) {
            draw(widget, small, snapshot = null) {
                onNode(hasText("Open Nomad Dashboard")).assertExists()
            }
        }
    }

    @Test
    fun safetyShowsTheEmergencyNumberAndTheConsularLine() = draw(SafetyWidget(), small, snapshot) {
        onNode(hasText("112")).assertExists()
        onNode(hasText("Portugal")).assertExists()
        onNode(hasText("+00 123 456 789")).assertExists()
        onNode(hasText("Updated 12:40")).assertExists()
    }

    @Test
    fun safetyWithoutACitizenshipExplainsHowToGetTheConsularLine() =
        draw(SafetyWidget(), small, snapshot.copy(embassy = null)) {
            onNode(hasText("Add your citizenship in Settings for your consular line")).assertExists()
            onNode(hasText("+00 123 456 789")).assertDoesNotExist()
        }

    @Test
    fun safetyWithoutAVerifiedNumberSaysUnknown() = draw(SafetyWidget(), wide, snapshot.copy(emergency = null)) {
        onNode(hasText("Unknown here")).assertExists()
        onNode(hasText("No verified number for where you are")).assertExists()
    }

    @Test
    fun daylightShowsTheSunsetAndTheDaylightLeft() = draw(DaylightWidget(), small, snapshot) {
        onNode(hasText("Sunset 19:42")).assertExists()
        onNode(hasText("7 h 2 min of daylight left")).assertExists()
    }

    @Test
    fun daylightAfterDarkShowsWhenItGetsLightAgain() =
        draw(DaylightWidget(), small, snapshot, at = Instant.parse("2026-10-12T21:00:00Z")) {
            onNode(hasText("Dark until 06:31")).assertExists()
            onNode(hasText("Sunrise tomorrow")).assertExists()
        }

    @Test
    fun daylightWithoutALocationAsksForOne() = draw(DaylightWidget(), small, snapshot.copy(daylight = null)) {
        onNode(hasText("Location needed")).assertExists()
    }
}
