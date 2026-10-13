package com.iloapps.nomaddashboard.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.model.SurfSpotConfiguration
import java.util.Locale

internal const val ExpandWeatherForecastSwitchTag = "settings_expand_weather_forecast_switch"
internal const val HudUserApiTokenFieldTag = "settings_hud_user_api_token_field"
internal const val HudUserApiTokenSaveButtonTag = "settings_hud_user_api_token_save_button"
internal const val TankerkoenigApiKeyFieldTag = "settings_tankerkoenig_api_key_field"
internal const val TankerkoenigApiKeySaveButtonTag = "settings_tankerkoenig_api_key_save_button"

@Composable
internal fun WeatherLocalGroup(group: SettingsGroup, scope: SettingsGroupScope) {
    when (group) {
        SettingsGroup.WEATHER -> WeatherGroup(scope)
        SettingsGroup.AIR_QUALITY -> AirQualityGroup(scope)
        SettingsGroup.SURF_SPOT -> SurfSpotGroup(scope)
        SettingsGroup.LOCAL_INFO -> LocalInfoGroup(scope)
        SettingsGroup.FUEL_PRICES -> FuelPricesGroup(scope)
        else -> error("$group is not a Weather & Local group")
    }
}

@Composable
private fun WeatherGroup(scope: SettingsGroupScope) {
    val settings = scope.settings
    SettingsGroupCard(
        group = SettingsGroup.WEATHER,
        subtitle = "Choose where the weather card gets its location.",
        highlighted = scope.highlighted == SettingsGroup.WEATHER,
        footer = "Device location is used only when you turn this on. Without it, weather follows your approximate IP location.",
    ) {
        SettingsToggleRow(
            title = "Use current location for weather",
            description = "Prefer on-device location for the weather card when available.",
            checked = settings.useCurrentLocationForWeather,
            switchModifier = Modifier.testTag("settings_weather_location_switch"),
        ) { enabled -> scope.update { it.copy(useCurrentLocationForWeather = enabled) } }
        SettingsToggleRow(
            title = "Expand weather forecast",
            description = "Show the longer forecast block inside the main weather card.",
            checked = settings.weatherForecastExpanded,
            switchModifier = Modifier.testTag(ExpandWeatherForecastSwitchTag),
        ) { enabled -> scope.update { it.copy(weatherForecastExpanded = enabled) } }
    }
}

@Composable
private fun AirQualityGroup(scope: SettingsGroupScope) {
    val settings = scope.settings
    SettingsGroupCard(
        group = SettingsGroup.AIR_QUALITY,
        subtitle = "Air quality alongside the weather.",
        highlighted = scope.highlighted == SettingsGroup.AIR_QUALITY,
        badges = if (settings.airQualityEnabled && !settings.useCurrentLocationForWeather) {
            listOf("Waiting for device location" to NomadBadgeTone.Warning)
        } else {
            emptyList()
        },
        footer = "Fetched only while weather may use your location. Coordinates are rounded to about 11 km before they leave the device.",
    ) {
        SettingsToggleRow(
            title = "Show air quality",
            description = "Add the local air quality reading to the weather card.",
            checked = settings.airQualityEnabled,
            switchModifier = Modifier.testTag("settings_air_quality_switch"),
        ) { enabled -> scope.update { it.copy(airQualityEnabled = enabled) } }
    }
}

@Composable
private fun SurfSpotGroup(scope: SettingsGroupScope) {
    val surfSpot = scope.settings.surfSpot
    val state = scope.state
    var surfSpotName by rememberSaveable(surfSpot.name) { mutableStateOf(surfSpot.name) }
    var surfSpotLatitude by rememberSaveable(surfSpot.latitude) {
        mutableStateOf(surfSpot.latitude?.let { formatCoordinate(it) }.orEmpty())
    }
    var surfSpotLongitude by rememberSaveable(surfSpot.longitude) {
        mutableStateOf(surfSpot.longitude?.let { formatCoordinate(it) }.orEmpty())
    }

    val parsedLatitude = surfSpotLatitude.trim().takeIf(String::isNotBlank)?.toDoubleOrNull()
    val parsedLongitude = surfSpotLongitude.trim().takeIf(String::isNotBlank)?.toDoubleOrNull()
    val latitudeInvalid = surfSpotLatitude.isNotBlank() && parsedLatitude == null
    val longitudeInvalid = surfSpotLongitude.isNotBlank() && parsedLongitude == null
    val coordinatesOutOfRange = listOfNotNull(
        parsedLatitude?.takeUnless { it in -90.0..90.0 },
        parsedLongitude?.takeUnless { it in -180.0..180.0 },
    ).isNotEmpty()
    val draftSurfSpot = SurfSpotConfiguration(
        name = surfSpotName.trim(),
        latitude = parsedLatitude,
        longitude = parsedLongitude,
    )
    val isDirty = draftSurfSpot != surfSpot

    SettingsGroupCard(
        group = SettingsGroup.SURF_SPOT,
        subtitle = "Save one coastal break for marine wave, swell, and wind conditions on the dashboard.",
        highlighted = scope.highlighted == SettingsGroup.SURF_SPOT,
        badges = listOf(
            if (surfSpot.latitude != null && surfSpot.longitude != null) {
                "Configured" to NomadBadgeTone.Good
            } else {
                "Not set" to NomadBadgeTone.Info
            },
        ),
    ) {
        OutlinedTextField(
            value = surfSpotName,
            onValueChange = { surfSpotName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Spot name") },
            supportingText = { Text("Use a practical label like Tarifa, El Saler, or Ericeira.") },
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = surfSpotLatitude,
                onValueChange = { surfSpotLatitude = it },
                modifier = Modifier.weight(1f),
                label = { Text("Latitude") },
                supportingText = { Text(if (latitudeInvalid) "Enter a decimal latitude." else "Decimal coordinates.") },
                isError = latitudeInvalid || coordinatesOutOfRange && parsedLatitude != null && parsedLatitude !in -90.0..90.0,
                singleLine = true,
            )
            OutlinedTextField(
                value = surfSpotLongitude,
                onValueChange = { surfSpotLongitude = it },
                modifier = Modifier.weight(1f),
                label = { Text("Longitude") },
                supportingText = { Text(if (longitudeInvalid) "Enter a decimal longitude." else "Decimal coordinates.") },
                isError = longitudeInvalid || coordinatesOutOfRange && parsedLongitude != null && parsedLongitude !in -180.0..180.0,
                singleLine = true,
            )
        }
        SettingsBodyText(
            when {
                latitudeInvalid || longitudeInvalid -> "Save uses decimal coordinates only."
                coordinatesOutOfRange -> "Latitude must be between -90 and 90, longitude between -180 and 180."
                else -> "Use current location to autofill the spot from Android location services, or enter coordinates manually."
            },
        )
        state.surfSpotLocationStatus?.let { status ->
            SettingsBodyText(status, isError = state.surfSpotLocationStatusIsError)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        ) {
            Button(
                enabled = !state.isResolvingSurfSpotLocation,
                onClick = scope.actions.onFillSurfSpotFromCurrentLocation,
            ) {
                Icon(Icons.Rounded.MyLocation, contentDescription = null)
                Text(
                    text = if (state.isResolvingSurfSpotLocation) "Matching..." else "Use current location",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Button(
                enabled = isDirty && !latitudeInvalid && !longitudeInvalid && !coordinatesOutOfRange,
                onClick = { scope.update { it.copy(surfSpot = draftSurfSpot) } },
            ) {
                Text("Save surf spot")
            }
        }
    }
}

@Composable
private fun LocalInfoGroup(scope: SettingsGroupScope) {
    SettingsGroupCard(
        group = SettingsGroup.LOCAL_INFO,
        subtitle = "Local place context, public holidays, best-effort school holidays, and local price signals.",
        highlighted = scope.highlighted == SettingsGroup.LOCAL_INFO,
        footer = "Local Info uses Nager.Date for public holidays, OpenHolidays for best-effort school holidays, " +
            "Eurostat in Europe, and HUD USER plus the US Census Geocoder in the US. The HUD token stays encrypted on this device only.",
    ) {
        SettingsToggleRow(
            title = "Show local info",
            description = "Add the Local Info card to the dashboard.",
            checked = scope.settings.localInfoEnabled,
            switchModifier = Modifier.testTag("settings_local_info_switch"),
        ) { enabled -> scope.update { SettingsEditing.setLocalInfoEnabled(it, enabled) } }
        SettingsCredentialField(
            savedValue = scope.state.providerCredentials.hudUserApiToken,
            label = "HUD USER API token (US 1BR rent)",
            supportingText = "Optional for Local Info. Only needed for the US 1-bedroom rent benchmark.",
            clearLabel = "Clear token",
            saveLabel = "Save token",
            fieldTag = HudUserApiTokenFieldTag,
            saveTag = HudUserApiTokenSaveButtonTag,
            onSave = { token -> scope.actions.onUpdateProviderCredentials { it.copy(hudUserApiToken = token) } },
        )
    }
}

@Composable
private fun FuelPricesGroup(scope: SettingsGroupScope) {
    SettingsGroupCard(
        group = SettingsGroup.FUEL_PRICES,
        subtitle = "Nearby fuel rows when country coverage is available.",
        highlighted = scope.highlighted == SettingsGroup.FUEL_PRICES,
        footer = "Coverage depends on the country. Germany needs your own Tankerkönig API key, which stays encrypted on this device only.",
    ) {
        SettingsToggleRow(
            title = "Enable fuel prices",
            description = "Keep nearby fuel rows visible when country coverage is available.",
            checked = scope.settings.fuelPricesEnabled,
            switchModifier = Modifier.testTag("settings_fuel_prices_switch"),
        ) { enabled -> scope.update { it.copy(fuelPricesEnabled = enabled) } }
        SettingsCredentialField(
            savedValue = scope.state.providerCredentials.tankerkoenigApiKey,
            label = "Tankerkönig API key",
            supportingText = "Required only for Germany fuel prices.",
            clearLabel = "Clear key",
            saveLabel = "Save key",
            fieldTag = TankerkoenigApiKeyFieldTag,
            saveTag = TankerkoenigApiKeySaveButtonTag,
            onSave = { key -> scope.actions.onUpdateProviderCredentials { it.copy(tankerkoenigApiKey = key) } },
        )
    }
}

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.5f", value)
