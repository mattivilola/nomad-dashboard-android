package com.iloapps.nomaddashboard.feature.widgets

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.iloapps.nomaddashboard.core.travel.answers.NomadDeepLink
import com.iloapps.nomaddashboard.core.travel.widget.WidgetEmptyState
import com.iloapps.nomaddashboard.core.travel.widget.WidgetUpdatedFooter

/** The widget content padding. */
internal val WidgetPadding = 14.dp

/** Opens the app on [link] when the widget element it is applied to is tapped. */
@Composable
internal fun GlanceModifier.opensApp(link: NomadDeepLink): GlanceModifier {
    val context = LocalContext.current
    return clickable(actionStartActivity(WidgetIntents.view(context, link)))
}

/** The shared look of every Nomad widget: the theme background, rounded corners, and a tap that opens [link]. */
@Composable
internal fun WidgetFrame(
    link: NomadDeepLink,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(WidgetPalette.background)
            .cornerRadius(R.dimen.widget_corner_radius)
            .padding(WidgetPadding)
            .opensApp(link),
        content = content,
    )
}

@Composable
internal fun WidgetIcon(
    @DrawableRes icon: Int,
    tint: ColorProvider,
    size: Dp,
    modifier: GlanceModifier = GlanceModifier,
) {
    Image(
        provider = ImageProvider(icon),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/** "Updated 12:40", or "Out of date · 12:40" in the caution colour once the app has been silent for 6 hours. */
@Composable
internal fun UpdatedFooter(footer: WidgetUpdatedFooter) {
    Row(
        modifier = GlanceModifier.semantics { contentDescription = footer.accessibilityLabel },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (footer.isStale) {
            WidgetIcon(R.drawable.ic_widget_schedule, WidgetPalette.caution, size = 11.dp)
            Spacer(GlanceModifier.width(3.dp))
        }
        Text(
            text = footer.text,
            style = TextStyle(
                color = if (footer.isStale) WidgetPalette.caution else WidgetPalette.tertiaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

/** Shown until the app has saved its first snapshot. */
@Composable
internal fun WidgetEmptyContent() {
    WidgetFrame(link = WidgetLinks.dashboard) {
        WidgetIcon(R.drawable.ic_widget_luggage, WidgetPalette.secondaryText, size = 22.dp)
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = WidgetEmptyState.TITLE,
            style = TextStyle(color = WidgetPalette.primaryText, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            maxLines = 2,
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = WidgetEmptyState.DETAIL,
            style = TextStyle(color = WidgetPalette.secondaryText, fontSize = 11.sp),
            maxLines = 4,
        )
    }
}

/** The colours content is drawn in: quieter once it is out of date. */
internal class ContentColors(isStale: Boolean) {
    val primary: ColorProvider = if (isStale) WidgetPalette.dimmedText else WidgetPalette.primaryText
    val secondary: ColorProvider = if (isStale) WidgetPalette.dimmedText else WidgetPalette.secondaryText
    val tertiary: ColorProvider = if (isStale) WidgetPalette.dimmedText else WidgetPalette.tertiaryText
}
