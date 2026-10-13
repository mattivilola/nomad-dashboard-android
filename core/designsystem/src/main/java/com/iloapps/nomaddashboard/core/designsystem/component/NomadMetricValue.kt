package com.iloapps.nomaddashboard.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** The unit is drawn at this share of the number's size. */
private const val UnitScale = 0.62f

/**
 * A metric value with its unit drawn smaller than the number ("96.0 Mbps", "1,429 €/L").
 *
 * The number and the unit are passed apart and never parsed out of a string, so a caller formats the
 * number once and the unit stays a unit. A unit that starts with "/" (a per-unit suffix such as "/L")
 * follows the number without a space; any other unit is separated by one.
 *
 * Screen readers read it as one text ("96.0 Mbps").
 */
@Composable
fun NomadMetricValue(
    number: String,
    unit: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    val text = buildAnnotatedString {
        append(number)
        if (!unit.isNullOrEmpty()) {
            if (!unit.startsWith("/")) append(' ')
            withStyle(
                SpanStyle(
                    fontSize = style.fontSize * UnitScale,
                    fontWeight = FontWeight.Medium,
                ),
            ) {
                append(unit)
            }
        }
    }
    Text(
        text = text,
        modifier = modifier,
        style = style,
        fontWeight = fontWeight,
        color = color,
        maxLines = 1,
    )
}
