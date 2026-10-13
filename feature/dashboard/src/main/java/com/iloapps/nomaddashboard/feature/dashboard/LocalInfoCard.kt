package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadInlineAction
import com.iloapps.nomaddashboard.core.designsystem.component.NomadStatusBadge
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot
import com.iloapps.nomaddashboard.core.model.LocalInfoStatus
import com.iloapps.nomaddashboard.core.model.LocalPriceLevelStatus
import com.iloapps.nomaddashboard.core.model.StartupLocationBootstrapState
import com.iloapps.nomaddashboard.core.travel.cardcontent.CardLocation
import com.iloapps.nomaddashboard.core.travel.cardcontent.LocalInfoRow
import com.iloapps.nomaddashboard.core.travel.cardcontent.LocalInfoRows
import com.iloapps.nomaddashboard.core.travel.cardcontent.LocalRhythmRow
import com.iloapps.nomaddashboard.core.travel.rhythm.AndroidIcuLunisolarCalendars
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmCalendar
import java.time.Instant
import java.time.ZoneId

/**
 * The Local Info card: the emergency number first, then the public holiday, the school break, one
 * "Prices vs EU" row and the local rhythm of the current country (Ramadan, Lunar New Year, Holy Week).
 * The place the data applies to is the subtitle.
 *
 * @param snapshot the whole dashboard snapshot: the emergency number and the local rhythm follow the
 * current country (device location, then IP location, then Local Info).
 * @param now the moment the dates are relative to; a test pins it.
 */
@Composable
internal fun LocalInfoSectionCard(
    enabled: Boolean,
    snapshot: DashboardSnapshot,
    onOpenSettings: () -> Unit,
    frame: DashboardCardFrame = DashboardCardFrame(),
    now: Instant = remember(snapshot) { Instant.now() },
) {
    val localInfo = snapshot.localInfo
    val startupLocation = snapshot.startupLocation
    val waitingForStartupLocation = enabled &&
        startupLocation.isChecking &&
        localInfo.status !in setOf(LocalInfoStatus.READY, LocalInfoStatus.PARTIAL)
    val badge = localInfoBadge(localInfo, startupLocation)
    val sourceLine = "Sources: " + (
        if (enabled) localInfo.sources.map { it.name }.ifEmpty { LocalInfoCapabilitySources }
        else LocalInfoCapabilitySources
    ).joinToString(" · ")

    val time = rememberCardTime(ZoneId.systemDefault())
    val rhythmCalendar = remember { LocalRhythmCalendar(AndroidIcuLunisolarCalendars()) }
    val showsContent = enabled && !waitingForStartupLocation
    val rows = remember(showsContent, snapshot, time, now) {
        if (showsContent) LocalInfoRows.of(localInfo, CardLocation.countryCode(snapshot), time, now) else emptyList()
    }
    val rhythm = remember(showsContent, snapshot, time, now) {
        if (showsContent && localInfo.status != LocalInfoStatus.LOCATION_REQUIRED) {
            LocalRhythmRow.rows(rhythmCalendar, snapshot, time, now)
        } else {
            emptyList()
        }
    }

    DashboardCard(
        card = DashboardCardId.LOCAL_INFO,
        frame = frame,
        modifier = Modifier.testTag(LocalInfoCardTag),
        subtitle = LocalInfoRows.subtitle(enabled, waitingForStartupLocation, localInfo),
        collapsedBadge = badge,
        actions = {
            if (localInfo.isRefreshing) {
                SectionRefreshIndicator(tag = "dashboard_local_info_refresh_indicator")
            }
            badge?.let {
                NomadStatusBadge(text = it.first, tone = it.second)
            }
            if (localInfoNeedsSettingsAction(enabled = enabled, snapshot = localInfo)) {
                NomadInlineAction(
                    label = "Open Settings",
                    icon = Icons.Rounded.Settings,
                    onClick = onOpenSettings,
                )
            }
        },
    ) {
        if (enabled.not()) {
            Text(
                text = "Local Info is disabled. Enable it in Settings.",
                style = MaterialTheme.typography.bodyLarge,
            )
            SourceLine(sourceLine)
            return@DashboardCard
        }

        if (waitingForStartupLocation) {
            Text(
                text = "Checking device location before loading local context, holidays, and price signals.",
                style = MaterialTheme.typography.bodyLarge,
            )
            SourceLine(sourceLine)
            return@DashboardCard
        }

        if (rows.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { row -> LocalInfoRowView(row) }
            }
        }

        if (rhythm.isNotEmpty()) {
            LocalRhythmSection(rhythm)
        }

        val hasContent = rows.isNotEmpty() || rhythm.isNotEmpty()
        if (hasContent.not()) {
            Text(
                text = localInfo.detail ?: "Local Info is unavailable right now.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        // Without any row the detail above already explains why.
        val notes = if (hasContent) localInfoNotes(localInfo) else listOfNotNull(localInfo.note)
        notes.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
        SourceLine(sourceLine)
    }
}

@Composable
private fun SourceLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
    )
}

/**
 * One answer with what it refers to. A status chip (a busy day) takes the place of the value; a value
 * that is a phone number can be selected and copied.
 */
@Composable
private fun LocalInfoRowView(row: LocalInfoRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(localInfoRowTag(row.id))
            .dashboardRowTile()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (row.detail.isNotBlank()) {
                Text(
                    text = row.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        val chip = row.badge
        if (chip != null) {
            NomadStatusBadge(
                text = chip.text,
                tone = chip.tone.badgeTone(),
                modifier = Modifier.testTag(warningChipTag(row.id)),
            )
        } else {
            val value: @Composable () -> Unit = {
                Text(
                    text = row.value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                )
            }
            if (row.isValueSelectable) SelectionContainer { value() } else value()
        }
    }
}

/** The periods of the current country that are on or start soon, each with what a visitor should expect. */
@Composable
private fun LocalRhythmSection(rows: List<LocalRhythmRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = LocalRhythmRow.SECTION_TITLE,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
        rows.forEach { row ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(LocalInfoRhythmRowTagPrefix + row.id)
                    .dashboardRowTile()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = row.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = row.timing,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = row.dates,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
                Text(
                    text = row.guidance,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * A healthy snapshot has nothing to explain, so only its note shows; a degraded one also carries its
 * detail.
 */
private fun localInfoNotes(snapshot: LocalInfoSnapshot): List<String> =
    when (snapshot.status) {
        LocalInfoStatus.READY -> listOfNotNull(snapshot.note)
        else -> listOfNotNull(snapshot.detail, snapshot.note)
    }.filter(String::isNotBlank)

private fun localInfoNeedsSettingsAction(
    enabled: Boolean,
    snapshot: LocalInfoSnapshot,
): Boolean =
    enabled.not() ||
        snapshot.status == LocalInfoStatus.LOCATION_REQUIRED ||
        snapshot.localPriceLevel.status == LocalPriceLevelStatus.CONFIGURATION_REQUIRED

private fun localInfoBadge(
    snapshot: LocalInfoSnapshot,
    startupLocation: StartupLocationBootstrapState,
): Pair<String, NomadBadgeTone>? = when {
    startupLocation.isChecking &&
        snapshot.status !in setOf(LocalInfoStatus.READY, LocalInfoStatus.PARTIAL) ->
        "Checking" to NomadBadgeTone.Info
    else -> when (snapshot.status) {
        LocalInfoStatus.OFF -> "Off" to NomadBadgeTone.Info
        LocalInfoStatus.CHECKING -> "Checking" to NomadBadgeTone.Info
        LocalInfoStatus.READY -> "Ready" to NomadBadgeTone.Good
        LocalInfoStatus.PARTIAL -> "Partial" to NomadBadgeTone.Info
        LocalInfoStatus.LOCATION_REQUIRED -> "Location Needed" to NomadBadgeTone.Caution
        LocalInfoStatus.UNSUPPORTED -> "Unsupported" to NomadBadgeTone.Info
        LocalInfoStatus.UNAVAILABLE -> "Unavailable" to NomadBadgeTone.Caution
    }
}

private val LocalInfoCapabilitySources = listOf("Nager.Date", "OpenHolidays", "Eurostat", "HUD USER", "US Census Geocoder")

internal const val LocalInfoCardTag = "local-info-card"

internal const val LocalInfoPublicHolidayWarningChipTag = "local-info-public-holiday-warning-chip"

internal const val LocalInfoSchoolBreakWarningChipTag = "local-info-school-break-warning-chip"

internal const val LocalInfoRhythmRowTagPrefix = "local-info-rhythm-"

/** The tag of a Local Info row: [LocalInfoRows.EMERGENCY_ID] and the other row ids. */
internal fun localInfoRowTag(rowId: String): String = "local-info-row-$rowId"

private fun warningChipTag(rowId: String): String = when (rowId) {
    LocalInfoRows.PUBLIC_HOLIDAY_ID -> LocalInfoPublicHolidayWarningChipTag
    LocalInfoRows.SCHOOL_HOLIDAY_ID -> LocalInfoSchoolBreakWarningChipTag
    else -> "local-info-warning-chip-$rowId"
}
