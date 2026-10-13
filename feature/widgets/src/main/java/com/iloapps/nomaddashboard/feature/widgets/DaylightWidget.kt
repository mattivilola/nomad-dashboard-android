package com.iloapps.nomaddashboard.feature.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.iloapps.nomaddashboard.core.travel.widget.WidgetDisplay
import com.iloapps.nomaddashboard.core.travel.widget.WidgetSnapshot
import java.time.Instant

/**
 * Sunset and the daylight left, or when it gets light again. The times come from the daylight the app
 * saved with the snapshot and are replayed for the moment of drawing, so no location is read and nothing
 * is fetched.
 */
internal class DaylightWidget : NomadGlanceWidget() {
    @Composable
    override fun Content(snapshot: WidgetSnapshot?, display: WidgetDisplay, now: Instant) {
        if (snapshot == null) {
            WidgetEmptyContent()
            return
        }
        val content = display.daylight(snapshot, now)
        val colors = ContentColors(content.footer.isStale)

        WidgetFrame(link = WidgetLinks.daylight(content)) {
            WidgetIcon(
                icon = WidgetIcons.daylight(content.kind),
                tint = if (content.isCaution) WidgetPalette.caution else colors.secondary,
                size = 26.dp,
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = content.title,
                style = TextStyle(color = colors.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = content.detail,
                style = TextStyle(color = colors.secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 3,
            )
            Spacer(GlanceModifier.defaultWeight())
            UpdatedFooter(content.footer)
        }
    }
}
