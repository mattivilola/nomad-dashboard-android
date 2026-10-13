package com.iloapps.nomaddashboard.feature.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal const val SafetyEssentialsTag = "companion_safety_essentials"
internal const val SafetyEmergencyNumbersTag = "companion_safety_emergency_numbers"
internal const val SafetyEmbassyTag = "companion_safety_embassy"

/**
 * The emergency numbers and the user's embassy at the top of the Offline essentials. The text is
 * selectable so a number can be copied, and it comes from datasets bundled with the app, so it is there
 * without a connection. Shows nothing when neither is known.
 */
@Composable
internal fun SafetyEssentialsBlock(essentials: SafetyEssentials, modifier: Modifier = Modifier) {
    if (essentials.isEmpty) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SafetyEssentialsTag)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        essentials.emergencyNumbers?.let { numbers ->
            Column(
                modifier = Modifier.testTag(SafetyEmergencyNumbersTag),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BlockTitle(listOfNotNull("Emergency numbers", essentials.countryName).joinToString(" · "))
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = numbers.headline,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        numbers.othersText?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                            )
                        }
                    }
                }
            }
        }
        essentials.embassy?.let { embassy ->
            Column(
                modifier = Modifier.testTag(SafetyEmbassyTag),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BlockTitle("Your embassy")
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        embassy.nearestMission?.let {
                            Text(text = it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                        embassy.hotlines.forEach { hotline ->
                            Text(
                                text = "${hotline.title} · ${hotline.phone}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = "Bundled with the app, so it works offline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        )
    }
}

@Composable
private fun BlockTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
    )
}
