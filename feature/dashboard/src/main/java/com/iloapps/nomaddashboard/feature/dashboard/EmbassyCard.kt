@file:OptIn(ExperimentalLayoutApi::class)

package com.iloapps.nomaddashboard.feature.dashboard

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadInlineAction
import com.iloapps.nomaddashboard.core.designsystem.component.NomadMetricValue
import com.iloapps.nomaddashboard.core.designsystem.component.NomadStatusBadge
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.travel.safety.EmbassyOverview
import com.iloapps.nomaddashboard.core.travel.safety.EmbassyOverviewBuilder
import com.iloapps.nomaddashboard.core.travel.safety.SafetyLocation
import com.iloapps.nomaddashboard.core.travel.safety.build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val EmbassyCardTag = "embassy-card"
internal const val EmbassyOpenSettingsTag = "embassy_open_settings"
internal const val EmbassyFooterTag = "embassy_footer"

internal fun embassyHotlineTag(id: String) = "embassy_hotline_$id"
internal fun embassyCallTag(id: String) = "embassy_call_$id"
internal fun embassyCopyTag(id: String) = "embassy_copy_$id"
internal fun embassyMissionTag(id: String) = "embassy_mission_$id"
internal fun embassyMissionMenuTag(id: String) = "embassy_mission_menu_$id"
internal fun embassyRegistrationTag(id: String) = "embassy_registration_$id"
internal fun embassyDirectoryTag(id: String) = "embassy_directory_$id"

/**
 * Your Embassy, computed from the settings and the snapshot. The citizenship and the position are only
 * compared with the bundled offline dataset, which is decoded off the main thread, and never leave the
 * device.
 */
@Composable
internal fun EmbassyCardHost(
    settings: AppSettings,
    snapshot: DashboardSnapshot,
    onOpenSettings: (SettingsDestination?) -> Unit,
    onOpenUri: (String) -> Unit,
    onDial: (String) -> Unit,
    onCopy: (String) -> Unit,
    frame: DashboardCardFrame = DashboardCardFrame(),
) {
    val builder = remember { EmbassyOverviewBuilder() }
    val location = remember(snapshot.travelContext, snapshot.localInfo.countryCode) {
        SafetyLocation.of(snapshot.travelContext, snapshot.localInfo.countryCode)
    }
    val citizenships = settings.citizenshipCountryCodes
    var overview by remember { mutableStateOf<EmbassyOverview?>(null) }
    LaunchedEffect(citizenships, location) {
        overview = withContext(Dispatchers.Default) { builder.build(citizenships, location) }
    }
    EmbassySectionCard(
        overview = overview,
        onOpenSettings = onOpenSettings,
        onOpenUri = onOpenUri,
        onDial = onDial,
        onCopy = onCopy,
        frame = frame,
    )
}

/**
 * Your Embassy: the foreign ministry's consular line, the nearest listed missions (or the EU and Nordic
 * fallback) and the official links. The wording says "listed" and "nearest known" because the dataset is
 * incomplete. [overview] is null while the offline dataset loads.
 *
 * Without a citizenship the card is turned off and only appears while the layout is customized, with a
 * prompt that opens the citizenship setting.
 */
@Composable
internal fun EmbassySectionCard(
    overview: EmbassyOverview?,
    onOpenSettings: (SettingsDestination?) -> Unit = {},
    onOpenUri: (String) -> Unit = {},
    onDial: (String) -> Unit = {},
    onCopy: (String) -> Unit = {},
    frame: DashboardCardFrame = DashboardCardFrame(),
) {
    val badge = overview?.badge?.let { it.title to it.tone.toNomadTone() }
    DashboardCard(
        card = DashboardCardId.EMBASSY,
        frame = frame,
        modifier = Modifier.testTag(EmbassyCardTag),
        subtitle = overview?.subtitle,
        badges = listOfNotNull(badge),
        collapsedBadge = badge,
    ) {
        if (overview == null) {
            Text(
                text = "Checking listed missions…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        } else {
            EmbassyContent(overview, onOpenSettings, onOpenUri, onDial, onCopy)
        }
    }
}

@Composable
private fun EmbassyContent(
    overview: EmbassyOverview,
    onOpenSettings: (SettingsDestination?) -> Unit,
    onOpenUri: (String) -> Unit,
    onDial: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    when (overview.state) {
        EmbassyOverview.State.Off -> {
            BodyText(EmbassyOverviewBuilder.OffMessage)
            OpenSettingsAction { onOpenSettings(SettingsDestination.CITIZENSHIP) }
        }

        EmbassyOverview.State.AtHome -> {
            Hotlines(overview, onDial, onCopy)
            overview.atHomeMessage?.let { Notice(Icons.Rounded.Home, it) }
        }

        EmbassyOverview.State.LocationNeeded -> {
            Hotlines(overview, onDial, onCopy)
            Notice(Icons.Rounded.LocationOff, EmbassyOverviewBuilder.LocationNeededTitle)
            BodyText(EmbassyOverviewBuilder.LocationNeededMessage)
            OpenSettingsAction { onOpenSettings(null) }
            Links(overview, onOpenUri)
        }

        EmbassyOverview.State.Abroad -> {
            Hotlines(overview, onDial, onCopy)
            Missions(overview, onOpenUri, onCopy)
            Links(overview, onOpenUri)
            overview.footer?.let {
                Text(
                    text = it,
                    modifier = Modifier.testTag(EmbassyFooterTag),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
            }
        }
    }
}

/** Colour means status only: the card raises caution (location needed) and nothing else. */
private fun EmbassyOverview.BadgeTone.toNomadTone(): NomadBadgeTone = when (this) {
    EmbassyOverview.BadgeTone.Neutral -> NomadBadgeTone.Neutral
    EmbassyOverview.BadgeTone.Caution -> NomadBadgeTone.Caution
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
    )
}

@Composable
private fun OpenSettingsAction(onClick: () -> Unit) {
    NomadInlineAction(
        label = "Open Settings",
        icon = Icons.Rounded.Settings,
        onClick = onClick,
        modifier = Modifier.testTag(EmbassyOpenSettingsTag),
    )
}

@Composable
private fun Notice(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
        )
    }
}

@Composable
private fun Hotlines(overview: EmbassyOverview, onDial: (String) -> Unit, onCopy: (String) -> Unit) {
    overview.hotlines.forEach { hotline ->
        HotlineRow(hotline, onDial = { onDial(hotline.phone) }, onCopy = { onCopy(hotline.phone) })
    }
}

/** The foreign ministry's consular line. The number is selectable, can be copied and opens the dialer. */
@Composable
private fun HotlineRow(hotline: EmbassyOverview.Hotline, onDial: () -> Unit, onCopy: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(embassyHotlineTag(hotline.id))
            .dashboardRowTile()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = hotline.title,
            modifier = Modifier.semantics { contentDescription = hotline.help },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        SelectionContainer {
            Text(
                text = hotline.phone,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Text(
            text = hotline.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NomadInlineAction(
                label = "Call",
                icon = Icons.Rounded.Call,
                onClick = onDial,
                modifier = Modifier.testTag(embassyCallTag(hotline.id)),
            )
            NomadInlineAction(
                label = "Copy",
                icon = Icons.Rounded.ContentCopy,
                onClick = onCopy,
                modifier = Modifier.testTag(embassyCopyTag(hotline.id)),
            )
        }
    }
}

@Composable
private fun Missions(overview: EmbassyOverview, onOpenUri: (String) -> Unit, onCopy: (String) -> Unit) {
    if (overview.missions.isNotEmpty()) {
        overview.missions.forEach { MissionRow(it, onOpenUri, onCopy) }
        return
    }
    overview.fallbackNotice?.let { Notice(Icons.Rounded.Info, it) }
    overview.fallbackMissions.forEach { MissionRow(it, onOpenUri, onCopy) }
    overview.noMissionMessage?.let { Notice(Icons.Rounded.Info, it) }
}

/** One mission. The whole row opens the maps app; the menu holds the secondary actions. */
@Composable
private fun MissionRow(mission: EmbassyOverview.MissionRow, onOpenUri: (String) -> Unit, onCopy: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(embassyMissionTag(mission.id))
            .dashboardRowTile(),
        verticalAlignment = Alignment.Top,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClickLabel = "Show ${mission.name} in your maps app") { onOpenUri(mission.mapsUri) }
                .semantics(mergeDescendants = true) { contentDescription = mission.accessibilityLabel }
                .padding(start = 14.dp, top = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = mission.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    NomadStatusBadge(text = mission.kindTitle, modifier = Modifier.align(Alignment.CenterVertically))
                    if (mission.locationText.isNotEmpty()) {
                        Text(
                            text = mission.locationText,
                            modifier = Modifier.align(Alignment.CenterVertically),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        )
                    }
                }
                mission.reasonCaption?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    )
                }
            }
            mission.distanceText?.let { Distance(it) }
        }
        MissionMenu(mission, onOpenUri, onCopy)
    }
}

/** "4.2 km" or "~4 km", with the unit drawn smaller than the number. */
@Composable
private fun Distance(text: String) {
    if (text.endsWith(" km")) {
        NomadMetricValue(number = text.removeSuffix(" km"), unit = "km")
    } else {
        Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MissionMenu(mission: EmbassyOverview.MissionRow, onOpenUri: (String) -> Unit, onCopy: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag(embassyMissionMenuTag(mission.id)),
        ) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More actions for ${mission.name}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Show in Maps") },
                leadingIcon = { Icon(Icons.Rounded.Map, contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenUri(mission.mapsUri)
                },
            )
            mission.websiteUrl?.let { website ->
                DropdownMenuItem(
                    text = { Text("Open website") },
                    leadingIcon = { Icon(Icons.Rounded.OpenInNew, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onOpenUri(website)
                    },
                )
            }
            mission.address?.let { address ->
                DropdownMenuItem(
                    text = { Text("Copy address") },
                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onCopy(address)
                    },
                )
            }
        }
    }
}

/** The ministry's trip-registration service and its list of missions, as inline actions. */
@Composable
private fun Links(overview: EmbassyOverview, onOpenUri: (String) -> Unit) {
    overview.links.forEach { group ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (overview.links.size > 1) {
                Text(
                    text = group.countryName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                group.registration?.let { link ->
                    NomadInlineAction(
                        label = link.title,
                        icon = Icons.Rounded.OpenInNew,
                        onClick = { onOpenUri(link.url) },
                        modifier = Modifier.testTag(embassyRegistrationTag(group.id)),
                    )
                }
                group.missionsDirectory?.let { link ->
                    NomadInlineAction(
                        label = link.title,
                        icon = Icons.Rounded.OpenInNew,
                        onClick = { onOpenUri(link.url) },
                        modifier = Modifier.testTag(embassyDirectoryTag(group.id)),
                    )
                }
            }
        }
    }
}

/** The digits of a phone number with a leading plus, which is what the dialer needs; null without digits. */
internal fun dialableNumber(phone: String): String? {
    val trimmed = phone.trim()
    val digits = trimmed.filter(Char::isDigit)
    if (digits.isEmpty()) return null
    return if (trimmed.startsWith("+")) "+$digits" else digits
}

/** Opens [uri] (a `geo:` or web link) in whichever app handles it; does nothing when none does. */
internal fun Context.openExternalUri(uri: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No app handles the link; leave the user on the dashboard.
    }
}

/** Opens the dialer with [phone] filled in. The user still has to press call. */
internal fun Context.dialPhoneNumber(phone: String) {
    val number = dialableNumber(phone) ?: return
    val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // A device without a dialer: the number can still be selected or copied.
    }
}
