package com.iloapps.nomaddashboard.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.Locale

internal const val CountryPickerSearchTag = "settings_country_picker_search"
internal const val TimeZonePickerSearchTag = "settings_time_zone_picker_search"

internal fun countryOptionTag(code: String): String = "settings_country_option_$code"

internal fun timeZoneOptionTag(id: String): String = "settings_time_zone_option_$id"

/** A searchable list of countries. Codes in [excluding] are not offered. */
@Composable
internal fun CountryPickerDialog(
    title: String,
    excluding: Collection<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = Locale.getDefault()
    val countries = remember(locale) { CountryDirectory.all(locale) }
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(countries, query, excluding) { CountryDirectory.matching(countries, query, excluding) }

    PickerDialog(
        title = title,
        searchLabel = "Search countries",
        searchTag = CountryPickerSearchTag,
        query = query,
        onQueryChange = { query = it },
        emptyText = "No country matches your search.",
        isEmpty = matches.isEmpty(),
        onDismiss = onDismiss,
    ) {
        items(matches, key = { it.code }) { country ->
            PickerRow(
                title = country.name,
                subtitle = country.code,
                modifier = Modifier.testTag(countryOptionTag(country.code)),
                onClick = { onSelect(country.code) },
            )
        }
    }
}

/** A searchable list of IANA time zones. */
@Composable
internal fun TimeZonePickerDialog(
    title: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = Locale.getDefault()
    val zones = remember(locale) { TimeZoneDirectory.all(locale) }
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(zones, query) { TimeZoneDirectory.matching(zones, query) }

    PickerDialog(
        title = title,
        searchLabel = "Search cities and regions",
        searchTag = TimeZonePickerSearchTag,
        query = query,
        onQueryChange = { query = it },
        emptyText = "No time zone matches your search.",
        isEmpty = matches.isEmpty(),
        onDismiss = onDismiss,
    ) {
        items(matches, key = { it.id }) { zone ->
            PickerRow(
                title = zone.title,
                subtitle = zone.subtitle,
                modifier = Modifier.testTag(timeZoneOptionTag(zone.id)),
                onClick = { onSelect(zone.id) },
            )
        }
    }
}

@Composable
private fun PickerDialog(
    title: String,
    searchLabel: String,
    searchTag: String,
    query: String,
    onQueryChange: (String) -> Unit,
    emptyText: String,
    isEmpty: Boolean,
    onDismiss: () -> Unit,
    list: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(searchTag),
                    label = { Text(searchLabel) },
                    singleLine = true,
                )
                if (isEmpty) {
                    SettingsBodyText(emptyText)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp), content = list)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PickerRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    }
}
