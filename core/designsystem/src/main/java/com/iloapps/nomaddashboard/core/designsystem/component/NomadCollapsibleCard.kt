package com.iloapps.nomaddashboard.core.designsystem.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val ExpandedPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
private val CollapsedPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
private val CollapsedRowHeight = 40.dp

/**
 * A dashboard card that folds into one line.
 *
 * Expanded, it is the usual card: the section header (title, optional [subtitle] and [badges], then the
 * [actions] slot and a chevron) followed by [content]. Collapsed, it is one row of about 40dp: title,
 * [summary], [collapsedBadge] and the chevron. The chevron toggles; so does tapping the collapsed row.
 * The card animates its size as it folds.
 *
 * The [actions] slot is where a card keeps its refresh indicator and its own buttons; it shows only
 * while the card is expanded, so a collapsed row stays quiet.
 *
 * @param summary One line that leads with what matters and never repeats [collapsedBadge].
 * @param collapsedBadge The status badge on the collapsed row; null shows none.
 * @param toggleModifier Applied to whichever control toggles, the chevron when expanded and the row when
 * collapsed, for example to give it a test tag.
 */
@Composable
fun NomadCollapsibleCard(
    title: String,
    summary: String,
    collapsed: Boolean,
    onCollapsedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badges: List<Pair<String, NomadBadgeTone>> = emptyList(),
    collapsedBadge: Pair<String, NomadBadgeTone>? = null,
    toggleModifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    NomadCard(
        modifier = modifier.animateContentSize(),
        contentPadding = if (collapsed) CollapsedPadding else ExpandedPadding,
    ) {
        if (collapsed) {
            CollapsedCardRow(
                title = title,
                summary = summary,
                badge = collapsedBadge,
                onExpand = { onCollapsedChange(false) },
                modifier = toggleModifier,
            )
        } else {
            NomadSectionClusterHeader(
                title = title,
                subtitle = subtitle,
                badges = badges,
                actions = {
                    actions()
                    IconButton(
                        onClick = { onCollapsedChange(true) },
                        modifier = toggleModifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = "Collapse $title",
                        )
                    }
                },
            )
            content()
        }
    }
}

@Composable
private fun CollapsedCardRow(
    title: String,
    summary: String,
    badge: Pair<String, NomadBadgeTone>?,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CollapsedRowHeight)
            .clickable(onClickLabel = "Expand $title", role = Role.Button, onClick = onExpand)
            .semantics(mergeDescendants = true) { stateDescription = "Collapsed" },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = summary,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        badge?.let { (text, tone) -> NomadStatusBadge(text = text, tone = tone) }
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    }
}
