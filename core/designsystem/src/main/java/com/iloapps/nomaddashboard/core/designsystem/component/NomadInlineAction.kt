package com.iloapps.nomaddashboard.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.theme.nomadStatusColors

private val InlineActionShape = RoundedCornerShape(10.dp)

/**
 * The one style for a button that sits inside a card: teal text on a light teal-tinted rounded
 * rectangle. It is compact, and never a pill or a system-blue text button.
 */
@Composable
fun NomadInlineAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val teal = nomadStatusColors().good
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .alpha(if (enabled) 1f else DisabledAlpha)
            .clip(InlineActionShape)
            .background(teal.tint(InlineActionTintAlpha))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 32.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = teal.text,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = teal.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val InlineActionTintAlpha = 0.14f
private const val DisabledAlpha = 0.45f
