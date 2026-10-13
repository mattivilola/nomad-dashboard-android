package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.BatteryUnknown
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SignalWifiBad
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VpnKeyOff
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadInlineAction
import com.iloapps.nomaddashboard.core.designsystem.component.NomadMetricValue
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadStatusColor
import com.iloapps.nomaddashboard.core.designsystem.theme.nomadStatusColors
import com.iloapps.nomaddashboard.core.model.ResourceMode
import com.iloapps.nomaddashboard.core.travel.glance.GlanceClocks
import com.iloapps.nomaddashboard.core.travel.glance.GlanceHeadsUpRow
import com.iloapps.nomaddashboard.core.travel.glance.GlanceOverview
import com.iloapps.nomaddashboard.core.travel.glance.GlanceQuickLink
import com.iloapps.nomaddashboard.core.travel.glance.GlanceTileIcon
import com.iloapps.nomaddashboard.core.travel.glance.GlanceVerdict
import com.iloapps.nomaddashboard.core.travel.glance.GlanceVitalTile
import com.iloapps.nomaddashboard.core.travel.glance.displayTitle
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpKind
import com.iloapps.nomaddashboard.core.travel.health.HealthLevel
import com.iloapps.nomaddashboard.core.travel.health.HealthSymbol

internal const val GlanceVerdictTag = "dashboard_glance_verdict"
internal const val GlanceClocksTag = "dashboard_glance_clocks"
internal const val GlanceClocksSettingsTag = "dashboard_glance_clocks_settings"
internal const val GlanceTilesTag = "dashboard_glance_tiles"
internal const val GlanceHeadsUpTag = "dashboard_glance_headsup"
internal const val GlanceLinksTag = "dashboard_glance_links"
internal const val DataUsageMenuTag = "dashboard_data_usage_menu"

internal fun glanceTileTag(kind: GlanceVitalTile.Kind): String = "dashboard_glance_tile_${kind.name}"

internal fun glanceHeadsUpItemTag(kind: HeadsUpKind): String = "dashboard_glance_headsup_${kind.name}"

internal fun glanceLinkTag(link: GlanceQuickLink): String = "dashboard_glance_link_${link.name}"

internal fun dataUsageOptionTag(mode: ResourceMode): String = "dashboard_data_usage_${mode.name}"

/** What the glance does when the user taps it. */
internal class DashboardGlanceActions(
    /** A tile or heads-up item asks for its card to be expanded and scrolled to. */
    val onShowCard: (DashboardScrollTarget) -> Unit = {},
    val onOpenHomeTimeZoneSettings: () -> Unit = {},
    val onSelectResourceMode: (ResourceMode) -> Unit = {},
    /** Workplaces and Saved essentials both live on the Companion screen. */
    val onOpenCompanion: () -> Unit = {},
)

/**
 * Elements 3 to 6 of the glance overview, in order: the verdict strip with the data-usage menu, the
 * one-line clocks, the four vital tiles and the heads-up list with the row of links. (The header and
 * the time-tracking row are above, see `DashboardHeader`.)
 */
@Composable
internal fun DashboardGlance(
    glance: GlanceOverview,
    resourceMode: ResourceMode,
    isLowDataActive: Boolean,
    actions: DashboardGlanceActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlanceVerdictStrip(
            verdict = glance.verdict,
            resourceMode = resourceMode,
            isLowDataActive = isLowDataActive,
            onSelectResourceMode = actions.onSelectResourceMode,
        )
        GlanceClocksLine(glance.clocks, onOpenSettings = actions.onOpenHomeTimeZoneSettings)
        GlanceTiles(glance.tiles, onShowCard = actions.onShowCard)
        if (glance.headsUp.isNotEmpty()) {
            GlanceHeadsUpList(glance.headsUp, onShowCard = actions.onShowCard)
        }
        GlanceLinks(glance.quickLinks, onOpenCompanion = actions.onOpenCompanion)
    }
}

// region Status colours

/** The status colour of a health level; null for "no reading yet", which stays neutral. */
@Composable
internal fun HealthLevel.glanceStatusColor(): NomadStatusColor? {
    val colors = nomadStatusColors()
    return when (this) {
        HealthLevel.READY -> colors.good
        HealthLevel.CAUTION -> colors.caution
        HealthLevel.ATTENTION -> colors.attention
        HealthLevel.UNAVAILABLE -> null
    }
}

// endregion

// region Verdict

@Composable
private fun GlanceVerdictStrip(
    verdict: GlanceVerdict,
    resourceMode: ResourceMode,
    isLowDataActive: Boolean,
    onSelectResourceMode: (ResourceMode) -> Unit,
) {
    val status = verdict.level.glanceStatusColor() ?: nomadStatusColors().neutral
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .testTag(GlanceVerdictTag)
            .fillMaxWidth()
            .clip(shape)
            .background(status.tint(0.12f))
            .border(BorderStroke(1.dp, status.fill.copy(alpha = 0.28f)), shape)
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = verdict.symbol.icon(),
            contentDescription = null,
            tint = status.text,
            modifier = Modifier.size(26.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = verdict.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = verdict.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
        DataUsageMenu(
            mode = resourceMode,
            isLowDataActive = isLowDataActive,
            onSelect = onSelectResourceMode,
        )
    }
}

/** The data-usage choice (Auto, Normal, Low Data), which Settings also offers. */
@Composable
private fun DataUsageMenu(
    mode: ResourceMode,
    isLowDataActive: Boolean,
    onSelect: (ResourceMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // Auto reads "Low Data" while the policy has switched to it (metered or constrained network).
    val label = if (isLowDataActive) ResourceMode.LOW_DATA.dataUsageTitle else mode.dataUsageTitle
    Box {
        NomadInlineAction(
            label = label,
            icon = Icons.Rounded.ArrowDropDown,
            onClick = { expanded = true },
            modifier = Modifier
                .testTag(DataUsageMenuTag)
                .semantics { contentDescription = "Data usage: $label" },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ResourceMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.dataUsageTitle) },
                    trailingIcon = if (option == mode) {
                        { Icon(Icons.Rounded.Check, contentDescription = "Selected") }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    modifier = Modifier.testTag(dataUsageOptionTag(option)),
                )
            }
        }
    }
}

private val ResourceMode.dataUsageTitle: String
    get() = when (this) {
        ResourceMode.AUTO -> "Auto"
        ResourceMode.NORMAL -> "Normal"
        ResourceMode.LOW_DATA -> "Low Data"
    }

private fun HealthSymbol.icon(): ImageVector =
    when (this) {
        HealthSymbol.OFFLINE -> Icons.Rounded.WifiOff
        HealthSymbol.WAITING -> Icons.Rounded.HourglassEmpty
        HealthSymbol.NETWORK_WARNING -> Icons.Rounded.SignalWifiBad
        HealthSymbol.JITTER -> Icons.Rounded.GraphicEq
        HealthSymbol.NETWORK, HealthSymbol.NETWORK_READY -> Icons.Rounded.Wifi
        HealthSymbol.NETWORK_UNVERIFIED -> Icons.Rounded.WifiFind
        HealthSymbol.BATTERY_LOW -> Icons.Rounded.BatteryAlert
        HealthSymbol.BATTERY_HALF -> Icons.Rounded.BatteryStd
        HealthSymbol.BATTERY_FULL -> Icons.Rounded.BatteryFull
        HealthSymbol.BATTERY_UNKNOWN -> Icons.Rounded.BatteryUnknown
        HealthSymbol.POWER_SAVE -> Icons.Rounded.BatterySaver
        HealthSymbol.CHARGING -> Icons.Rounded.BatteryChargingFull
        HealthSymbol.PLUGGED_IN -> Icons.Rounded.Power
        HealthSymbol.OVERALL_ATTENTION -> Icons.Rounded.Error
        HealthSymbol.OVERALL_CAUTION -> Icons.Rounded.WarningAmber
        HealthSymbol.OVERALL_READY -> Icons.Rounded.CheckCircle
    }

// endregion

// region Clocks

/** "Here Valencia 11:30 · Home Helsinki 12:30", the day comparison and a shortcut to the home time zone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GlanceClocksLine(clocks: GlanceClocks, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .testTag(GlanceClocksTag)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ClockText(clocks.here.label, clocks.here.city, clocks.here.time)
            clocks.home?.let { home ->
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
                ClockText(home.label, home.city, home.time)
            }
            clocks.relativeDayText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
            }
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.testTag(GlanceClocksSettingsTag)) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = "Set home time zone",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
        }
    }
}

@Composable
private fun ClockText(label: String, city: String, time: String) {
    val secondary = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    val text: AnnotatedString = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(label) }
        append(' ')
        withStyle(SpanStyle(color = secondary)) { append(city) }
        append(' ')
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(time) }
    }
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}

// endregion

// region Vital tiles

/** Two tiles per row. Each is a button that opens its card, so a tap answers "what is behind this number?". */
@Composable
private fun GlanceTiles(tiles: List<GlanceVitalTile>, onShowCard: (DashboardScrollTarget) -> Unit) {
    Column(
        modifier = Modifier
            .testTag(GlanceTilesTag)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tiles.chunked(2).forEach { rowTiles ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowTiles.forEach { tile ->
                    GlanceTile(
                        tile = tile,
                        onClick = { onShowCard(DashboardScrollTarget(tile.scrollTarget)) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                if (rowTiles.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GlanceTile(tile: GlanceVitalTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val status = tile.level?.glanceStatusColor()
    val shape = RoundedCornerShape(20.dp)
    val borderColor = status?.fill?.copy(alpha = 0.32f) ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val valueColor = status?.text ?: MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .testTag(glanceTileTag(tile.kind))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.dp, borderColor), shape)
            .clickable(onClickLabel = "Shows the ${tile.scrollTarget.displayTitle} card", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = tile.accessibilityLabel }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tile.icon != GlanceTileIcon.WEATHER) {
                Icon(
                    imageVector = tile.icon.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = tile.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                maxLines = 1,
            )
            tile.titleDetail?.let {
                Text(
                    text = "· $it",
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tile.conditionWeatherCode?.let {
                Icon(
                    imageVector = weatherConditionIcon(it),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.size(22.dp),
                )
            }
            NomadMetricValue(
                number = tile.value.number,
                unit = tile.value.unit,
                style = MaterialTheme.typography.titleLarge,
                color = valueColor,
            )
        }
        Text(
            text = tile.secondary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun GlanceTileIcon.icon(): ImageVector =
    when (this) {
        GlanceTileIcon.WIFI -> Icons.Rounded.Wifi
        GlanceTileIcon.WIFI_OFF -> Icons.Rounded.WifiOff
        GlanceTileIcon.BATTERY -> Icons.Rounded.BatteryStd
        GlanceTileIcon.BATTERY_CHARGING -> Icons.Rounded.BatteryChargingFull
        GlanceTileIcon.BATTERY_UNKNOWN -> Icons.Rounded.BatteryUnknown
        GlanceTileIcon.PLUGGED_IN -> Icons.Rounded.Power
        GlanceTileIcon.VPN_ACTIVE -> Icons.Rounded.VpnLock
        GlanceTileIcon.VPN_INACTIVE -> Icons.Rounded.VpnKeyOff
        GlanceTileIcon.WEATHER -> Icons.Rounded.Cloud
    }

// endregion

// region Heads-up

@Composable
private fun GlanceHeadsUpList(rows: List<GlanceHeadsUpRow>, onShowCard: (DashboardScrollTarget) -> Unit) {
    Column(
        modifier = Modifier
            .testTag(GlanceHeadsUpTag)
            .fillMaxWidth(),
    ) {
        Text(
            text = "Heads-up",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            modifier = Modifier.padding(bottom = 2.dp),
        )
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }
            GlanceHeadsUpRowItem(row = row, onClick = { onShowCard(DashboardScrollTarget(row.relatedCard)) })
        }
    }
}

@Composable
private fun GlanceHeadsUpRowItem(row: GlanceHeadsUpRow, onClick: () -> Unit) {
    val status = row.level.glanceStatusColor() ?: nomadStatusColors().neutral
    Row(
        modifier = Modifier
            .testTag(glanceHeadsUpItemTag(row.kind))
            .fillMaxWidth()
            .clickable(onClickLabel = row.accessibilityHint, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = row.kind.icon(),
            contentDescription = null,
            tint = status.text,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            row.detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        )
    }
}

private fun HeadsUpKind.icon(): ImageVector =
    when (this) {
        HeadsUpKind.DATA_CAP -> Icons.Rounded.DataUsage
        HeadsUpKind.STAY_ALLOWANCE -> Icons.Rounded.EventBusy
        HeadsUpKind.SCHENGEN_LIMIT -> Icons.Rounded.EventBusy
        HeadsUpKind.TAX_RESIDENCE_DAYS -> Icons.Rounded.ReceiptLong
        HeadsUpKind.TRAVEL_ADVISORY -> Icons.Rounded.Shield
        HeadsUpKind.WEATHER_ALERT -> Icons.Rounded.Thunderstorm
        HeadsUpKind.REGIONAL_SECURITY -> Icons.Rounded.Security
        HeadsUpKind.STRONG_WIND -> Icons.Rounded.Air
        HeadsUpKind.RAIN_SOON -> Icons.Rounded.WaterDrop
        HeadsUpKind.UV_INDEX -> Icons.Rounded.LightMode
        HeadsUpKind.AIR_QUALITY -> Icons.Rounded.Air
        HeadsUpKind.PUBLIC_HOLIDAY -> Icons.Rounded.Event
        HeadsUpKind.LOCAL_RHYTHM -> Icons.Rounded.EventRepeat
        HeadsUpKind.FUEL_NEARBY -> Icons.Rounded.LocalGasStation
        HeadsUpKind.DAYLIGHT -> Icons.Rounded.WbTwilight
    }

// endregion

// region Links

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GlanceLinks(links: List<GlanceQuickLink>, onOpenCompanion: () -> Unit) {
    if (links.isEmpty()) return
    FlowRow(
        modifier = Modifier
            .testTag(GlanceLinksTag)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        links.forEach { link ->
            NomadInlineAction(
                label = link.title,
                icon = when (link) {
                    GlanceQuickLink.WORKPLACES -> Icons.Rounded.Place
                    GlanceQuickLink.SAVED_ESSENTIALS -> Icons.Rounded.Bookmarks
                },
                onClick = onOpenCompanion,
                modifier = Modifier.testTag(glanceLinkTag(link)),
            )
        }
    }
}

// endregion
