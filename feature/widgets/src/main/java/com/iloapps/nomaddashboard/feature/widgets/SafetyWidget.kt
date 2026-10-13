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
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.iloapps.nomaddashboard.core.travel.widget.SafetyWidgetContent
import com.iloapps.nomaddashboard.core.travel.widget.WidgetDisplay
import com.iloapps.nomaddashboard.core.travel.widget.WidgetSnapshot
import java.time.Instant

/** The emergency number where you are, and your foreign ministry's consular line. */
internal class SafetyWidget : NomadGlanceWidget() {
    @Composable
    override fun Content(snapshot: WidgetSnapshot?, display: WidgetDisplay, now: Instant) {
        if (snapshot == null) {
            WidgetEmptyContent()
            return
        }
        val content = display.safety(snapshot, now)
        val colors = ContentColors(content.footer.isStale)

        WidgetFrame(link = WidgetLinks.emergency) {
            if (isWide()) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Column(modifier = GlanceModifier.defaultWeight()) { Emergency(content, colors) }
                    Spacer(GlanceModifier.width(16.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) { Consular(content, colors) }
                }
            } else {
                Emergency(content, colors)
                Spacer(GlanceModifier.height(8.dp))
                Consular(content, colors)
            }
            Spacer(GlanceModifier.defaultWeight())
            UpdatedFooter(content.footer)
        }
    }

    @Composable
    private fun Emergency(content: SafetyWidgetContent, colors: ContentColors) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .semantics { contentDescription = content.accessibilityLabel }
                .opensApp(WidgetLinks.emergency),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WidgetIcon(R.drawable.ic_widget_call, colors.secondary, size = 12.dp)
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    text = content.emergencyLabel,
                    style = TextStyle(color = colors.secondary, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
            if (content.emergencyNumber != null) {
                Text(
                    text = content.emergencyNumber.orEmpty(),
                    style = TextStyle(color = colors.primary, fontSize = 40.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                content.countryName?.let { country ->
                    Text(
                        text = country,
                        style = TextStyle(color = colors.secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                }
            } else {
                Text(
                    text = content.unknownTitle.orEmpty(),
                    modifier = GlanceModifier.padding(top = 4.dp),
                    style = TextStyle(color = colors.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    text = content.unknownDetail.orEmpty(),
                    style = TextStyle(color = colors.secondary, fontSize = 11.sp),
                    maxLines = 3,
                )
            }
        }
    }

    @Composable
    private fun Consular(content: SafetyWidgetContent, colors: ContentColors) {
        val link = WidgetLinks.consular(content)
        when {
            content.hotline != null -> Column(modifier = GlanceModifier.fillMaxWidth().opensApp(link)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WidgetIcon(R.drawable.ic_widget_account_balance, colors.tertiary, size = 11.dp)
                    Spacer(GlanceModifier.width(4.dp))
                    Text(
                        text = content.consularLabel,
                        style = TextStyle(color = colors.tertiary, fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                }
                Text(
                    text = content.hotline.orEmpty(),
                    style = TextStyle(color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }

            content.citizenshipHint != null -> Text(
                text = content.citizenshipHint.orEmpty(),
                modifier = GlanceModifier.fillMaxWidth().opensApp(link),
                style = TextStyle(color = colors.tertiary, fontSize = 10.sp),
                maxLines = 3,
            )
        }
    }
}
