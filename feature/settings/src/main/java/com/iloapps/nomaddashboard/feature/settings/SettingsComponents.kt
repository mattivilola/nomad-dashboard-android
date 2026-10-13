package com.iloapps.nomaddashboard.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSectionClusterHeader

internal fun settingsGroupTag(group: SettingsGroup): String = "settings_group_${group.name}"

/**
 * One group of related settings: a header, its controls, and a short footer for the privacy or
 * data-source facts that concern only this group. [highlighted] draws a brief accent border when a
 * deep link lands on the group.
 */
@Composable
internal fun SettingsGroupCard(
    group: SettingsGroup,
    subtitle: String,
    highlighted: Boolean,
    footer: String? = null,
    badges: List<Pair<String, NomadBadgeTone>> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (highlighted) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "settingsGroupHighlight",
    )
    NomadCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(28.dp))
            .testTag(settingsGroupTag(group)),
    ) {
        NomadSectionClusterHeader(title = group.title, subtitle = subtitle, badges = badges)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        footer?.let { SettingsFooter(it) }
    }
}

@Composable
internal fun SettingsFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
    )
}

@Composable
internal fun SettingsBodyText(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
    )
}

@Composable
internal fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    switchModifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = switchModifier,
        )
    }
}

/** A single-choice row of chips; the selected option is filled. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> SettingsChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    tagFor: (T) -> String? = { null },
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                modifier = tagFor(option)?.let { Modifier.testTag(it) } ?: Modifier,
            )
        }
    }
}

/** A labelled value with minus and plus buttons. */
@Composable
internal fun SettingsStepperRow(
    title: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    canDecrement: Boolean = true,
    canIncrement: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
        }
        IconButton(onClick = onDecrement, enabled = canDecrement) {
            Icon(Icons.Rounded.Remove, contentDescription = "Decrease $title")
        }
        IconButton(onClick = onIncrement, enabled = canIncrement) {
            Icon(Icons.Rounded.Add, contentDescription = "Increase $title")
        }
    }
}

/**
 * A secret the user pastes in: masked, saved explicitly and clearable. The saved value is the source
 * of truth, so the draft resets whenever it changes underneath.
 */
@Composable
internal fun SettingsCredentialField(
    savedValue: String,
    label: String,
    supportingText: String,
    clearLabel: String,
    saveLabel: String,
    fieldTag: String,
    saveTag: String,
    onSave: (String) -> Unit,
) {
    var draft by rememberSaveable(savedValue) { mutableStateOf(savedValue) }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(fieldTag),
        label = { Text(label) },
        supportingText = { Text(supportingText) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
    ) {
        TextButton(
            enabled = savedValue.isNotBlank() || draft.isNotBlank(),
            onClick = {
                draft = ""
                onSave("")
            },
        ) {
            Text(clearLabel)
        }
        Button(
            enabled = draft != savedValue,
            modifier = Modifier.testTag(saveTag),
            onClick = { onSave(draft) },
        ) {
            Text(saveLabel)
        }
    }
}
