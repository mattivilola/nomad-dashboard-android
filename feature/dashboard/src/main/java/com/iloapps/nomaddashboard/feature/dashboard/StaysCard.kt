package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadInlineAction
import com.iloapps.nomaddashboard.core.designsystem.component.NomadUsageBar
import com.iloapps.nomaddashboard.core.designsystem.component.statusColor
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.travel.safety.CurrentCountry
import com.iloapps.nomaddashboard.core.travel.stayscard.StayTone
import com.iloapps.nomaddashboard.core.travel.stayscard.StaysCardPresentation
import java.time.LocalDate

internal const val StaysCardTag = "stays-card"
internal const val StaysCurrentStayTag = "stays_current_stay"
internal const val StaysAllowanceTag = "stays_allowance"
internal const val StaysSchengenTag = "stays_schengen"
internal const val StaysTaxYearTag = "stays_tax_year"
internal const val StaysSetCitizenshipTag = "stays_set_citizenship"
internal const val StaysSettingsLinkTag = "stays_settings_link"
internal const val StaysReviewDaysTag = "stays_review_days"
internal const val StaysOpenSettingsTag = "stays_open_settings"

/**
 * Stays & Visas, computed from the user's own recorded country days and the stay settings. Visited
 * countries and citizenship are sensitive: everything here is computed on the device and never sent
 * anywhere.
 */
@Composable
internal fun StaysCardHost(
    settings: AppSettings,
    countryDays: List<VisitedCountryDay>,
    snapshot: DashboardSnapshot,
    onOpenSettings: (SettingsDestination) -> Unit,
    onOpenVisited: () -> Unit,
    frame: DashboardCardFrame = DashboardCardFrame(),
) {
    val currentCountry = CurrentCountry.code(
        deviceCountryCode = snapshot.travelContext.deviceCountryCode,
        ipCountryCode = snapshot.travelContext.countryCode,
        localInfoCountryCode = snapshot.localInfo.countryCode,
    )
    // A new snapshot is the moment the date is read again, so a card left open overnight catches up.
    val today = remember(snapshot) { LocalDate.now() }
    val presentation = remember(settings, countryDays, currentCountry, today) {
        StaysCardPresentation.of(settings, countryDays, currentCountry, today)
    }
    StaysSectionCard(
        presentation = presentation,
        onOpenSettings = onOpenSettings,
        onOpenVisited = onOpenVisited,
        frame = frame,
    )
}

/**
 * Stays & Visas: how long the user has been in this country, their visa allowance, the Schengen 90/180
 * counter and this year's days per country for tax residence. While visited places are off the card is
 * turned off and only appears while the layout is customized.
 */
@Composable
internal fun StaysSectionCard(
    presentation: StaysCardPresentation,
    onOpenSettings: (SettingsDestination) -> Unit = {},
    onOpenVisited: () -> Unit = {},
    frame: DashboardCardFrame = DashboardCardFrame(),
) {
    val badge = presentation.badge.title to presentation.badge.tone.toNomadTone()
    DashboardCard(
        card = DashboardCardId.STAYS,
        frame = frame,
        modifier = Modifier.testTag(StaysCardTag),
        subtitle = StaysCardPresentation.Subtitle,
        badges = listOf(badge),
        collapsedBadge = badge,
    ) {
        if (presentation.isEnabled) {
            CurrentStay(presentation.currentStay)
            presentation.allowance?.let { AllowanceTile(it) }
            presentation.schengen?.let { SchengenTile(it, onOpenSettings) }
            presentation.taxYear?.let { TaxYearTile(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                NomadInlineAction(
                    label = StaysCardPresentation.ReviewDaysActionTitle,
                    icon = Icons.Rounded.Map,
                    onClick = onOpenVisited,
                    modifier = Modifier.testTag(StaysReviewDaysTag),
                )
                NomadInlineAction(
                    label = StaysCardPresentation.SettingsActionTitle,
                    icon = Icons.Rounded.Settings,
                    onClick = { onOpenSettings(SettingsDestination.STAYS) },
                    modifier = Modifier.testTag(StaysSettingsLinkTag),
                )
            }
        } else {
            Text(
                text = StaysCardPresentation.OffMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
            NomadInlineAction(
                label = "Open Settings",
                icon = Icons.Rounded.Settings,
                onClick = { onOpenSettings(SettingsDestination.VISITED_PLACES) },
                modifier = Modifier.testTag(StaysOpenSettingsTag),
            )
        }
    }
}

/** Colour means status only: sand and coral carry a status, everything else is neutral. */
private fun StayTone.toNomadTone(): NomadBadgeTone = when (this) {
    StayTone.Neutral -> NomadBadgeTone.Neutral
    StayTone.Caution -> NomadBadgeTone.Caution
    StayTone.Attention -> NomadBadgeTone.Attention
}

/** Usage bars read teal until a status is raised. */
private fun StayTone.toBarTone(): NomadBadgeTone = when (this) {
    StayTone.Neutral -> NomadBadgeTone.Good
    StayTone.Caution -> NomadBadgeTone.Caution
    StayTone.Attention -> NomadBadgeTone.Attention
}

@Composable
private fun StayTone.textColor(): Color = when (this) {
    StayTone.Neutral -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    StayTone.Caution, StayTone.Attention -> toNomadTone().statusColor().text
}

@Composable
private fun CurrentStay(stay: StaysCardPresentation.CurrentStay?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(StaysCurrentStayTag)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (stay == null) {
            Text(
                text = StaysCardPresentation.LocationPendingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                text = stay.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stay.since,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
        stay.gapNote?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
        }
    }
}

@Composable
private fun AllowanceTile(allowance: StaysCardPresentation.Allowance) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(StaysAllowanceTag)
            .dashboardRowTile()
            .padding(14.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TileTitle(allowance.title)
        NomadUsageBar(
            fraction = allowance.progress,
            tone = allowance.tone.toBarTone(),
            contentDescription = "${allowance.title}, ${allowance.usage}",
        )
        Text(
            text = allowance.text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = allowance.tone.textColor(),
        )
    }
}

@Composable
private fun SchengenTile(schengen: StaysCardPresentation.Schengen, onOpenSettings: (SettingsDestination) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(StaysSchengenTag)
            .dashboardRowTile()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (schengen.kind == StaysCardPresentation.Schengen.Kind.Exempt) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Public,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
                Text(
                    text = schengen.line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                )
            }
            return@Column
        }

        TileTitle(schengen.title)
        schengen.progress?.let {
            NomadUsageBar(fraction = it, tone = schengen.tone.toBarTone(), contentDescription = "Schengen days used")
        }
        Text(
            text = schengen.line,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = schengen.tone.textColor(),
        )
        schengen.hint?.let { hint ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = hint,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
                NomadInlineAction(
                    label = StaysCardPresentation.CitizenshipActionTitle,
                    onClick = { onOpenSettings(SettingsDestination.CITIZENSHIP) },
                    modifier = Modifier.testTag(StaysSetCitizenshipTag),
                )
            }
        }
    }
}

@Composable
private fun TaxYearTile(taxYear: StaysCardPresentation.TaxYear) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(StaysTaxYearTag)
            .dashboardRowTile()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            TileTitle(taxYear.heading)
            Text(
                text = taxYear.thresholdText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
        }
        taxYear.emptyText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
        taxYear.countries.forEach { country ->
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = country.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Text(
                        text = country.daysText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = country.tone.textColor(),
                    )
                }
                NomadUsageBar(fraction = country.progress, tone = country.tone.toBarTone())
            }
        }
        taxYear.unrecordedText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
        Text(
            text = StaysCardPresentation.TaxFooter,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        )
    }
}

@Composable
private fun TileTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
    )
}
