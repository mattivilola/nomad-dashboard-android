package com.iloapps.nomaddashboard.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadMetricBlock
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSectionClusterHeader
import com.iloapps.nomaddashboard.core.designsystem.component.NomadTopBar

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            NomadCard {
                NomadTopBar(
                    title = "About",
                    subtitle = "Nomad Dashboard for Android",
                    supportingText = "A compact travel instrument panel for connection, weather, movement, safety, and local-first utility on the road.",
                    badgeText = "Local-first",
                    badgeTone = NomadBadgeTone.Good,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    NomadMetricBlock("Platform", "Android", "phone-first adaptive UI")
                    NomadMetricBlock("Data", "Local", "settings and user secrets stay on-device")
                }
            }
        }

        item {
            NomadCard {
                NomadSectionClusterHeader(
                    title = "What The App Does",
                    subtitle = "Keep the main travel questions visible without opening multiple tools.",
                )
                Text(
                    text = "Nomad Dashboard combines travel context, weather, alerts, visited places, fuel, emergency readiness, and time tracking into one operational view.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        item {
            NomadCard {
                NomadSectionClusterHeader(
                    title = "Privacy Model",
                    subtitle = "Provider credentials and personal settings remain device-local.",
                )
                Text(
                    text = "User-managed API keys are entered after install and stored only in encrypted device storage. The app does not ship your provider credentials in resources, BuildConfig, or the manifest.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        item {
            NomadCard {
                NomadSectionClusterHeader(
                    title = "Current Focus",
                    subtitle = "Bringing Android UX quality closer to the original macOS product while respecting phone ergonomics.",
                )
                Text(
                    text = "The ongoing parity work concentrates on faster scan hierarchy, denser but clearer cards, and stronger in-place actions on the screens used most often.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
