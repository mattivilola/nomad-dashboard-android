package com.iloapps.nomaddashboard.feature.dashboard

import com.iloapps.nomaddashboard.core.model.withCardMoved
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadInlineAction
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardCardWidthMode
import com.iloapps.nomaddashboard.core.model.TravelProfile
import com.iloapps.nomaddashboard.core.travel.glance.displayTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal const val DashboardOverflowTag = "dashboard_overflow_menu"
internal const val DashboardCustomizeToggleTag = "dashboard_customize_toggle"
internal const val DashboardResetLayoutTag = "dashboard_reset_layout"
internal const val DashboardShowArrivalTag = "dashboard_show_arrival_briefing"

internal fun dashboardProfileTag(profile: TravelProfile): String = "dashboard_profile_${profile.name}"

internal fun customizeHandleTag(card: DashboardCardId): String = "dashboard_customize_handle_${card.name}"

internal fun customizeMoveUpTag(card: DashboardCardId): String = "dashboard_customize_move_up_${card.name}"

internal fun customizeMoveDownTag(card: DashboardCardId): String = "dashboard_customize_move_down_${card.name}"

internal fun customizeWidthTag(card: DashboardCardId): String = "dashboard_customize_width_${card.name}"

/**
 * The dashboard header's overflow menu: Customize Layout, Reset dashboard layout, and the travel
 * profile switch. The profile picks the recommended card order; switching it keeps a customized order.
 */
@Composable
internal fun DashboardOverflowMenu(
    isCustomizingLayout: Boolean,
    travelProfile: TravelProfile,
    onToggleCustomizing: () -> Unit,
    onResetLayout: () -> Unit,
    onSelectTravelProfile: (TravelProfile) -> Unit,
    /** Re-opens the arrival briefing; null hides the item (no current country or past the arrival window). */
    onShowArrivalBriefing: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    fun close(action: () -> Unit) = {
        expanded = false
        action()
    }

    IconButton(onClick = { expanded = true }, modifier = Modifier.testTag(DashboardOverflowTag)) {
        Icon(Icons.Rounded.MoreVert, contentDescription = "More dashboard options")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(if (isCustomizingLayout) "Done customizing" else "Customize layout") },
            onClick = close(onToggleCustomizing),
            modifier = Modifier.testTag(DashboardCustomizeToggleTag),
        )
        onShowArrivalBriefing?.let { show ->
            DropdownMenuItem(
                text = { Text("Show arrival briefing") },
                onClick = close(show),
                modifier = Modifier.testTag(DashboardShowArrivalTag),
            )
        }
        DropdownMenuItem(
            text = { Text("Reset dashboard layout") },
            onClick = close(onResetLayout),
            modifier = Modifier.testTag(DashboardResetLayoutTag),
        )
        HorizontalDivider()
        Text(
            text = "Travel profile",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        TravelProfile.entries.forEach { profile ->
            DropdownMenuItem(
                text = { Text(profile.title) },
                trailingIcon = if (profile == travelProfile) {
                    { Icon(Icons.Rounded.Check, contentDescription = "Selected") }
                } else {
                    null
                },
                onClick = close { onSelectTravelProfile(profile) },
                modifier = Modifier.testTag(dashboardProfileTag(profile)),
            )
        }
    }
}

/**
 * A card in Customize Layout: a strip with the drag handle, the move up/down actions and the width
 * toggle above the card itself. Only here do cards show these controls.
 *
 * The handle starts a drag after a long press; the move actions and the handle's accessibility actions
 * do the same without dragging.
 */
@Composable
internal fun DashboardCustomizeFrame(
    card: DashboardCardId,
    widthMode: DashboardCardWidthMode,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (delta: Int) -> Unit,
    onToggleWidth: () -> Unit,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = "Reorder ${card.displayTitle}. Long press and drag.",
                modifier = dragHandleModifier
                    .testTag(customizeHandleTag(card))
                    .semantics {
                        customActions = buildList {
                            if (canMoveUp) add(CustomAccessibilityAction("Move up") { onMove(-1); true })
                            if (canMoveDown) add(CustomAccessibilityAction("Move down") { onMove(1); true })
                        }
                    }
                    .padding(12.dp)
                    .size(24.dp),
            )
            Text(
                text = card.displayTitle,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            IconButton(
                onClick = { onMove(-1) },
                enabled = canMoveUp,
                modifier = Modifier.testTag(customizeMoveUpTag(card)),
            ) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move ${card.displayTitle} up")
            }
            IconButton(
                onClick = { onMove(1) },
                enabled = canMoveDown,
                modifier = Modifier.testTag(customizeMoveDownTag(card)),
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move ${card.displayTitle} down")
            }
            NomadInlineAction(
                label = if (widthMode == DashboardCardWidthMode.NARROW) "Half width" else "Full width",
                onClick = onToggleWidth,
                modifier = Modifier.testTag(customizeWidthTag(card)),
            )
        }
        content()
    }
}

/**
 * Drag-to-reorder for the cards in Customize Layout. The order being dragged lives in [draftOrder] and is
 * saved once, when the finger lifts, so a drag is one settings write.
 */
@Stable
internal class DashboardReorderState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val itemSpacingPx: Float,
    private val onCommit: (List<DashboardCardId>) -> Unit,
) {
    var draggedCard: DashboardCardId? by mutableStateOf(null)
        private set

    /** How far the finger has moved the dragged card from where the list currently lays it out. */
    var dragOffset: Float by mutableFloatStateOf(0f)
        private set

    /** The order being dragged, or null while no drag is under way. */
    var draftOrder: List<DashboardCardId>? by mutableStateOf(null)
        private set

    fun start(card: DashboardCardId, order: List<DashboardCardId>) {
        draggedCard = card
        draftOrder = order
        dragOffset = 0f
    }

    fun drag(deltaY: Float) {
        val card = draggedCard ?: return
        dragOffset += deltaY
        swapWithNeighbour(card)
        autoScroll(card)
    }

    fun end() {
        draftOrder?.let(onCommit)
        reset()
    }

    fun cancel() = reset()

    private fun reset() {
        draggedCard = null
        draftOrder = null
        dragOffset = 0f
    }

    private fun swapWithNeighbour(card: DashboardCardId) {
        val order = draftOrder ?: return
        val items = listState.layoutInfo.visibleItemsInfo
        val dragged = items.firstOrNull { it.key == card.name } ?: return
        val top = dragged.offset + dragOffset
        val bottom = top + dragged.size

        val below = items.firstOrNull { it.index == dragged.index + 1 && it.key in DashboardListItem.customizeCardKeys }
        val above = items.firstOrNull { it.index == dragged.index - 1 && it.key in DashboardListItem.customizeCardKeys }
        when {
            below != null && bottom > below.offset + below.size / 2f -> {
                draftOrder = order.withCardMoved(card, 1)
                dragOffset -= below.size + itemSpacingPx
            }

            above != null && top < above.offset + above.size / 2f -> {
                draftOrder = order.withCardMoved(card, -1)
                dragOffset += above.size + itemSpacingPx
            }
        }
    }

    /** Keeps the list moving while a card is held near the top or bottom edge. */
    private fun autoScroll(card: DashboardCardId) {
        val info = listState.layoutInfo
        val dragged = info.visibleItemsInfo.firstOrNull { it.key == card.name } ?: return
        val top = dragged.offset + dragOffset
        val bottom = top + dragged.size
        val edge = AutoScrollEdgePx
        val step = when {
            top < info.viewportStartOffset + edge -> -AutoScrollStepPx
            bottom > info.viewportEndOffset - edge -> AutoScrollStepPx
            else -> return
        }
        scope.launch {
            val consumed = listState.scrollBy(step)
            dragOffset += consumed
        }
    }

    private companion object {
        const val AutoScrollEdgePx = 96f
        const val AutoScrollStepPx = 28f
    }
}

/** The long-press drag gesture for a card's handle. */
@Composable
internal fun Modifier.dashboardDragHandle(
    card: DashboardCardId,
    state: DashboardReorderState,
    currentOrder: () -> List<DashboardCardId>,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val latestOrder by rememberUpdatedState(currentOrder)
    return pointerInput(card, state) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                state.start(card, latestOrder())
            },
            onDrag = { change, amount ->
                change.consume()
                state.drag(amount.y)
            },
            onDragEnd = state::end,
            onDragCancel = state::cancel,
        )
    }
}
