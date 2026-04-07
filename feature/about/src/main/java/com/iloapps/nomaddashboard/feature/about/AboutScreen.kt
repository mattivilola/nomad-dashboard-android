package com.iloapps.nomaddashboard.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
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
fun AboutScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            NomadCard {
                NomadSectionHeader(
                    title = "About",
                    subtitle = "Nomad Dashboard for Android",
                )
                Text("Native Android port of the macOS app by ILO APPLICATIONS SL.", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "This bootstrap focuses on architecture, release tooling, and a runnable dashboard shell that matches the macOS visual direction.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
