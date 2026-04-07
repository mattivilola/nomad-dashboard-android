package com.iloapps.nomaddashboard.feature.timetracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSectionHeader

@Composable
fun TimeTrackingScreen() {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            NomadCard {
                NomadSectionHeader(
                    title = "Time Tracking",
                    subtitle = "Foreground-service time tracking is planned in the next parity slice.",
                )
                Text(
                    text = "This screen is wired so Android navigation, module boundaries, and release packaging are ready before the deeper local-tracking implementation lands.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

