package com.iloapps.nomaddashboard.feature.visited

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSectionHeader

@Composable
fun VisitedScreen() {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            NomadCard {
                NomadSectionHeader(
                    title = "Visited Map",
                    subtitle = "The screen is present now so navigation and parity structure are in place.",
                )
                Text(
                    text = "Room storage and Google Maps rendering are scaffolded for the next implementation slice.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

