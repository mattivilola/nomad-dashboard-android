package com.iloapps.nomaddashboard.feature.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.iloapps.nomaddashboard.core.travel.widget.GlanceWidgetContent
import com.iloapps.nomaddashboard.core.travel.widget.GlanceWidgetSize
import com.iloapps.nomaddashboard.core.travel.widget.WidgetDisplay
import com.iloapps.nomaddashboard.core.travel.widget.WidgetSnapshot
import java.time.Instant

/** The verdict, the place and the top heads-up, at a glance. */
internal class GlanceOverviewWidget : NomadGlanceWidget() {
    @Composable
    override fun Content(snapshot: WidgetSnapshot?, display: WidgetDisplay, now: Instant) {
        if (snapshot == null) {
            WidgetEmptyContent()
            return
        }
        val wide = isWide()
        val content = display.glance(snapshot, now, if (wide) GlanceWidgetSize.MEDIUM else GlanceWidgetSize.SMALL)
        val colors = ContentColors(content.footer.isStale)

        WidgetFrame(link = WidgetLinks.dashboard) {
            if (wide) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Column(modifier = GlanceModifier.defaultWeight()) { VerdictColumn(content, colors) }
                    Spacer(GlanceModifier.width(16.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) { HeadsUps(content, colors) }
                }
            } else {
                VerdictColumn(content, colors)
                Spacer(GlanceModifier.height(8.dp))
                HeadsUps(content, colors)
            }
            Spacer(GlanceModifier.defaultWeight())
            UpdatedFooter(content.footer)
        }
    }

    @Composable
    private fun VerdictColumn(content: GlanceWidgetContent, colors: ContentColors) {
        WidgetIcon(WidgetIcons.verdict(content.verdictSymbol), WidgetPalette.of(content.verdictLevel), size = 20.dp)
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = content.verdictTitle,
            style = TextStyle(color = colors.primary, fontSize = 26.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        content.placeName?.let { place ->
            Text(
                text = place,
                style = TextStyle(color = colors.secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }

    @Composable
    private fun HeadsUps(content: GlanceWidgetContent, colors: ContentColors) {
        if (content.headsUp.isEmpty()) {
            Text(
                text = content.emptyHeadsUpText,
                style = TextStyle(color = colors.tertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
            return
        }
        content.headsUp.forEachIndexed { index, headsUp ->
            if (index > 0) Spacer(GlanceModifier.height(8.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth().opensApp(WidgetLinks.headsUp(headsUp.headsUpKind)),
                verticalAlignment = Alignment.Top,
            ) {
                WidgetIcon(
                    icon = WidgetIcons.headsUp(headsUp.headsUpKind),
                    tint = WidgetPalette.of(headsUp.severity.level),
                    size = 14.dp,
                    modifier = GlanceModifier.padding(top = 1.dp),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = headsUp.title,
                    style = TextStyle(color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    maxLines = if (isWide()) 3 else 2,
                )
            }
        }
    }
}
