package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadInlineAction
import com.iloapps.nomaddashboard.core.designsystem.component.NomadStatusBadge
import com.iloapps.nomaddashboard.core.designsystem.component.statusColor
import com.iloapps.nomaddashboard.core.model.AirQualitySnapshot
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.MarineSnapshot
import com.iloapps.nomaddashboard.core.model.StartupLocationBootstrapState
import com.iloapps.nomaddashboard.core.model.SurfSpotConfiguration
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import com.iloapps.nomaddashboard.core.travel.cardcontent.CardTime
import com.iloapps.nomaddashboard.core.travel.cardcontent.DayOutlook
import com.iloapps.nomaddashboard.core.travel.cardcontent.HourlyChip
import com.iloapps.nomaddashboard.core.travel.cardcontent.SurfForecastColumn
import com.iloapps.nomaddashboard.core.travel.cardcontent.SurfPresentation
import com.iloapps.nomaddashboard.core.travel.cardcontent.SurfSpotState
import com.iloapps.nomaddashboard.core.travel.cardcontent.WeatherAirQualityPresentation
import com.iloapps.nomaddashboard.core.travel.cardcontent.WeatherForecastPresentation
import com.iloapps.nomaddashboard.core.travel.cardcontent.WeatherUvPresentation
import com.iloapps.nomaddashboard.core.travel.cardcontent.WindMetric
import com.iloapps.nomaddashboard.core.travel.cards.CardTone
import com.iloapps.nomaddashboard.core.travel.daylight.DaylightLinePresentation
import com.iloapps.nomaddashboard.core.travel.daylight.DaylightReference
import com.iloapps.nomaddashboard.core.travel.format.TravelFormatters
import java.time.Instant
import java.time.ZoneId

internal const val WeatherDaylightLineTag = "weather_daylight_line"
internal const val WeatherHourlyStripTag = "weather_hourly_strip"
internal const val WeatherTomorrowRowTag = "weather_tomorrow_row"
internal const val WeatherOutlookToggleTag = "weather_outlook_toggle"
internal const val WeatherAirQualityTag = "weather_air_quality"
internal const val WeatherSurfBandTag = "weather_surf_band"
internal const val WeatherSurfSettingsTag = "weather_surf_settings"

private const val OutlookTitle = "7-day outlook"

/**
 * The Weather card: the temperature and condition once, then Feels like, Wind (with m/s) and Rain chance,
 * the UV index and air quality, the hourly strip, a Tomorrow row, the folding 7-day outlook, the
 * sunrise/sunset line (which needs no weather data) and the surf band.
 *
 * @param daylight where, and in which time zone, to read the day's light; null without a location.
 * @param settings decides whether air quality shows and whether the 7-day outlook starts open.
 * @param onForecastExpandedChange tells the owner that the outlook was opened or folded, to save the choice.
 * @param now the moment the forecast is relative to; a test pins it.
 */
@Composable
internal fun WeatherSectionCard(
    snapshot: WeatherSnapshot,
    startupLocation: StartupLocationBootstrapState,
    surfSpot: SurfSpotConfiguration,
    marine: MarineSnapshot?,
    modifier: Modifier = Modifier,
    frame: DashboardCardFrame = DashboardCardFrame(),
    airQuality: AirQualitySnapshot? = null,
    settings: AppSettings = AppSettings(),
    daylight: DaylightReference? = null,
    onOpenSurfSettings: () -> Unit = {},
    onForecastExpandedChange: (Boolean) -> Unit = {},
    now: Instant = remember(snapshot, airQuality) { Instant.now() },
) {
    val waitingForStartupLocation = startupLocation.isChecking && snapshot.currentTemperatureCelsius == null
    val hasConditions = snapshot.currentTemperatureCelsius != null
    val statusBadge = statusBadgeForWeather(snapshot, startupLocation)
    val time = rememberCardTime(daylight?.zone ?: ZoneId.systemDefault())
    var outlookExpanded by rememberSaveable(settings.weatherForecastExpanded) {
        mutableStateOf(settings.weatherForecastExpanded)
    }

    DashboardCard(
        card = DashboardCardId.WEATHER,
        frame = frame,
        modifier = modifier,
        subtitle = if (waitingForStartupLocation) "Checking device location" else snapshot.sourceName,
        collapsedBadge = statusBadge,
        actions = {
            if (snapshot.isRefreshing) {
                SectionRefreshIndicator(tag = "dashboard_weather_refresh_indicator")
            }
            NomadStatusBadge(
                text = statusBadge.first,
                tone = statusBadge.second,
            )
        },
    ) {
        when {
            waitingForStartupLocation -> Text(
                text = "Checking device location before loading location-based weather.",
                style = MaterialTheme.typography.bodyLarge,
            )

            hasConditions -> {
                WeatherConditions(snapshot = snapshot, airQuality = airQuality, settings = settings, time = time, now = now)
                val forecast = remember(snapshot, time, now, outlookExpanded) {
                    WeatherForecastPresentation.of(snapshot, time, now, outlookExpanded)
                }
                WeatherForecast(
                    forecast = forecast,
                    onToggleOutlook = {
                        outlookExpanded = !outlookExpanded
                        onForecastExpandedChange(outlookExpanded)
                    },
                )
            }

            else -> Text(
                text = snapshot.summary,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        // Computed on the device from the location, so it does not depend on weather data.
        val daylightLine = remember(daylight, now, time) {
            daylight?.let { DaylightLinePresentation.of(it, now, time.formats.clockTime) }
        }
        daylightLine?.let { DaylightLine(it) }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.36f))

        SurfSpotBand(
            surfSpot = surfSpot,
            marine = marine,
            now = now,
            onOpenSettings = onOpenSurfSettings,
        )
    }
}

@Composable
private fun WeatherConditions(
    snapshot: WeatherSnapshot,
    airQuality: AirQualitySnapshot?,
    settings: AppSettings,
    time: CardTime,
    now: Instant,
) {
    val wind = remember(snapshot) { WindMetric.of(snapshot) }
    val uv = remember(snapshot, time, now) { WeatherUvPresentation.of(snapshot, time, now) }
    val air = remember(airQuality, settings.isAirQualityActive, time, now) {
        WeatherAirQualityPresentation.of(airQuality, settings, time, now)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = TravelFormatters.temperature(snapshot.currentTemperatureCelsius),
                modifier = Modifier.testTag("weather_temperature"),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = weatherConditionIcon(snapshot.weatherCode),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = snapshot.conditionDescription,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            )
        }

        AdaptiveMetricRow(
            items = listOfNotNull<@Composable () -> Unit>(
                { WeatherStat("Feels like", TravelFormatters.temperature(snapshot.apparentTemperatureCelsius)) },
                { WeatherStat("Wind", wind.primary, secondary = wind.secondary) },
                {
                    WeatherStat(
                        "Rain chance",
                        snapshot.rainChancePercent?.let { TravelFormatters.percentage(it.toDouble()) }
                            ?: TravelFormatters.Unavailable,
                    )
                },
                uv?.let { { WeatherStat("UV", it.value, secondary = it.peak, tone = it.tone) } },
            ),
        )

        air?.let { AirQualityRow(it) }
    }
}

@Composable
private fun WeatherStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    tone: CardTone = CardTone.NEUTRAL,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = tone.valueColor(),
        )
        secondary?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }
    }
}

/** "AQI 38 · Fair · mostly PM10" with a bar per hour of the 12 hour outlook. */
@Composable
private fun AirQualityRow(presentation: WeatherAirQualityPresentation) {
    val description = listOfNotNull(presentation.headline, presentation.outlookCaption).joinToString(". ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WeatherAirQualityTag)
            .dashboardRowTile()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Air quality. $description" },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "AIR QUALITY",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Text(
                text = presentation.headline,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = presentation.tone.valueColor(),
            )
        }
        if (presentation.bars.isNotEmpty()) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.height(AirQualityBarsHeight),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    presentation.bars.forEach { bar ->
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(AirQualityBarsHeight * bar.height.toFloat())
                                .clip(RoundedCornerShape(50))
                                .background(bar.tone.barColor()),
                        )
                    }
                }
                presentation.outlookCaption?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

private val AirQualityBarsHeight = 22.dp

@Composable
private fun WeatherForecast(
    forecast: WeatherForecastPresentation,
    onToggleOutlook: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (forecast.hourly.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WeatherHourlyStripTag)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                forecast.hourly.forEach { chip -> HourlyChipView(chip) }
            }
        }

        if (forecast.showsTomorrow) {
            forecast.tomorrow?.let { TomorrowRow(it) }
        }

        if (forecast.showsOutlook) {
            OutlookToggle(expanded = forecast.isOutlookExpanded, onToggle = onToggleOutlook)
            if (forecast.isOutlookExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    forecast.days.forEach { day -> DayOutlookRow(day) }
                }
            }
        }
    }
}

@Composable
private fun HourlyChipView(chip: HourlyChip) {
    Column(
        modifier = Modifier
            .width(HourlyChipWidth)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = chip.accessibilityLabel },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = chip.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            maxLines = 1,
        )
        Icon(
            imageVector = weatherConditionIcon(chip.weatherCode),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = chip.temperature,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = chip.rainChance ?: " ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            maxLines = 1,
        )
    }
}

private val HourlyChipWidth = 62.dp

@Composable
private fun TomorrowRow(day: DayOutlook) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WeatherTomorrowRowTag),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = weatherConditionIcon(day.weatherCode),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = day.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = day.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
        Text(
            text = day.range,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OutlookToggle(expanded: Boolean, onToggle: () -> Unit) {
    CardDetailsToggle(
        label = OutlookTitle,
        expanded = expanded,
        onToggle = onToggle,
        modifier = Modifier.testTag(WeatherOutlookToggleTag),
    )
}

@Composable
private fun DayOutlookRow(day: DayOutlook) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = weatherConditionIcon(day.weatherCode),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = day.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = day.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
            listOfNotNull(day.rainChance, day.wind).takeIf { it.isNotEmpty() }?.let { details ->
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                )
            }
        }
        Text(
            text = day.range,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** "Sunrise 07:45 · Sunset 18:12", or "Midnight sun" / "Polar night". */
@Composable
private fun DaylightLine(presentation: DaylightLinePresentation) {
    Row(
        modifier = Modifier
            .testTag(WeatherDaylightLineTag)
            .semantics(mergeDescendants = true) { contentDescription = presentation.summary },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.WbTwilight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = presentation.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
        )
    }
}

/** Wave, swell, wind and sea in one row of equal columns, then the forecast hours as equal columns. */
@Composable
private fun SurfSpotBand(
    surfSpot: SurfSpotConfiguration,
    marine: MarineSnapshot?,
    now: Instant,
    onOpenSettings: () -> Unit,
) {
    val state = SurfSpotState.of(surfSpot, marine)

    Column(
        modifier = Modifier.testTag(WeatherSurfBandTag),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Surf Spot",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = surfSpot.name.ifBlank {
                        if (state == SurfSpotState.NOT_SET) "Add one break in Settings" else "Configured surf spot"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
            if (state.isActionable) {
                NomadInlineAction(
                    label = if (state == SurfSpotState.NOT_SET) "Set Surf Spot" else "Fix spot",
                    icon = Icons.Rounded.Settings,
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag(WeatherSurfSettingsTag),
                )
            } else {
                surfStatusBadge(state, marine)?.let { (text, tone) -> NomadStatusBadge(text = text, tone = tone) }
            }
        }

        if (marine != null) {
            val surf = SurfPresentation.of(marine, now)
            EqualColumns(count = surf.current.size) { index ->
                val stat = surf.current[index]
                WeatherStat(label = stat.label, value = stat.primary, secondary = stat.secondary)
            }
            if (surf.forecast.isNotEmpty()) {
                EqualColumns(count = SurfPresentation.FORECAST_COLUMNS) { index ->
                    surf.forecast.getOrNull(index)?.let { SurfForecastColumnView(it) }
                }
            }
        } else {
            Text(
                text = when (state) {
                    SurfSpotState.NOT_SET ->
                        "Add a surf spot in Settings to bring marine wave, swell, and coastal wind into this card."
                    SurfSpotState.INVALID ->
                        "Surf spot coordinates are out of range. Fix the saved latitude and longitude in Settings."
                    else -> "Marine conditions are unavailable right now for the configured spot."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            )
        }
    }
}

private fun surfStatusBadge(state: SurfSpotState, marine: MarineSnapshot?): Pair<String, NomadBadgeTone>? =
    when {
        marine != null -> {
            val updated = marine.fetchedAt?.formatDashboardTimestamp()
            listOfNotNull(marine.sourceName, updated).joinToString(" · ") to NomadBadgeTone.Good
        }
        state == SurfSpotState.UNAVAILABLE -> "Unavailable" to NomadBadgeTone.Caution
        else -> null
    }

/** [count] cells of the same width side by side. */
@Composable
private fun EqualColumns(count: Int, cell: @Composable (index: Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) { index ->
            Box(modifier = Modifier.weight(1f)) { cell(index) }
        }
    }
}

@Composable
private fun SurfForecastColumnView(column: SurfForecastColumn) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = column.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        )
        Text(
            text = column.wave,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = column.windSpeed,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
        Text(
            text = column.windDirection ?: " ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
    }
}

internal fun statusBadgeForWeather(
    snapshot: WeatherSnapshot,
    startupLocation: StartupLocationBootstrapState,
): Pair<String, NomadBadgeTone> =
    if (startupLocation.isChecking && snapshot.currentTemperatureCelsius == null) {
        "Checking" to NomadBadgeTone.Info
    } else if (snapshot.currentTemperatureCelsius != null) {
        "Live" to NomadBadgeTone.Good
    } else {
        "Limited" to NomadBadgeTone.Caution
    }

/** The colour of a value that carries a status; a neutral value keeps the normal text colour. */
@Composable
private fun CardTone.valueColor(): Color =
    if (this == CardTone.NEUTRAL) Color.Unspecified else badgeTone().statusColor().text

/** The colour of a chart mark: the status colour, or the neutral mark colour. */
@Composable
private fun CardTone.barColor(): Color = badgeTone().statusColor().fill

