package com.iloapps.nomaddashboard.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** A sparkline sits under a value and stays this tall. */
val NomadSparklineHeight = 24.dp

/**
 * A compact trend line, about 24dp tall, for the space under a value. A long press shows the span it
 * covers with the lowest and highest value (Android has no hover); screen readers get the same facts
 * as the content description.
 *
 * Fewer than two finite values leave an empty space of the same height so the layout does not jump.
 *
 * @param values Samples, oldest first.
 * @param spanLabel The time span the samples cover, for example "Last 30 min".
 * @param formatValue Formats the minimum and maximum, for example with their unit.
 * @param tone Status colour of the line; neutral unless the value beside it carries a status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NomadSparkline(
    values: List<Double>,
    spanLabel: String,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier,
    tone: NomadBadgeTone = NomadBadgeTone.Neutral,
) {
    val samples = remember(values) { values.filter(Double::isFinite) }
    if (samples.size < 2) {
        Spacer(modifier = modifier.fillMaxWidth().height(NomadSparklineHeight))
        return
    }

    val minimum = samples.min()
    val maximum = samples.max()
    val detail = "$spanLabel · min ${formatValue(minimum)} · max ${formatValue(maximum)}"
    val description = "$spanLabel, minimum ${formatValue(minimum)}, maximum ${formatValue(maximum)}"
    val color = tone.statusColor().fill

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text = detail) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(NomadSparklineHeight)
                .semantics { contentDescription = description },
        ) {
            val verticalPadding = 3.dp.toPx()
            val usableHeight = size.height - verticalPadding * 2
            val range = maximum - minimum
            val stepX = size.width / (samples.size - 1)
            fun point(index: Int): Offset {
                val fraction = if (range == 0.0) 0.5f else ((samples[index] - minimum) / range).toFloat()
                return Offset(index * stepX, verticalPadding + usableHeight * (1f - fraction))
            }

            val path = Path().apply {
                moveTo(point(0).x, point(0).y)
                for (index in 1 until samples.size) lineTo(point(index).x, point(index).y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawCircle(color = color, radius = 2.dp.toPx(), center = point(samples.lastIndex))
        }
    }
}
