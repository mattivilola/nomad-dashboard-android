package com.iloapps.nomaddashboard.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A thin progress bar tinted by status (teal below the caution point, sand from it, coral at the cap):
 * pick [tone] from the usage, for example [NomadBadgeTone.Good], [NomadBadgeTone.Caution] or
 * [NomadBadgeTone.Attention].
 *
 * @param fraction Used share, from 0 to 1; values outside are clamped.
 * @param contentDescription What the bar measures, for screen readers ("Data used, 85%").
 */
@Composable
fun NomadUsageBar(
    fraction: Float,
    tone: NomadBadgeTone,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val color = tone.statusColor()
    val clamped = fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = clamped, label = "usageBarFraction")
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(shape)
            .background(color.tint(0.18f))
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
                contentDescription?.let { this.contentDescription = it }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(shape)
                .background(color.fill),
        )
    }
}
