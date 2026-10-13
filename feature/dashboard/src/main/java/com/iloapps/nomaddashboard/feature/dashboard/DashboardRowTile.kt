package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** The soft tile behind a row inside a card, shared by the Your Embassy and Stays & Visas cards. */
@Composable
internal fun Modifier.dashboardRowTile(): Modifier =
    this
        .clip(RoundedCornerShape(22.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
