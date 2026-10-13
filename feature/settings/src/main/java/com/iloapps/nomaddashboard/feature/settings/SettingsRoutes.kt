package com.iloapps.nomaddashboard.feature.settings

import com.iloapps.nomaddashboard.core.model.SettingsDestination

/** The plain Settings route: it opens the page list, or the first page beside the list on wide screens. */
const val SettingsBaseRoute = "settings"

/** Name of the optional navigation argument that carries a [SettingsDestination]. */
const val SettingsDestinationArgument = "destination"

/** The navigation graph pattern for Settings, with the optional destination argument. */
const val SettingsRoutePattern = "$SettingsBaseRoute?$SettingsDestinationArgument={$SettingsDestinationArgument}"

/**
 * The route that opens Settings on [destination]'s page, scrolled to its group. A null destination
 * opens Settings as it is without a deep link.
 */
fun settingsRoute(destination: SettingsDestination? = null): String =
    if (destination == null) SettingsBaseRoute else "$SettingsBaseRoute?$SettingsDestinationArgument=${destination.name}"

/** Reads the navigation argument back; an unknown or missing value is no destination. */
fun settingsDestinationFromArgument(value: String?): SettingsDestination? =
    value?.let { name -> SettingsDestination.entries.firstOrNull { it.name == name } }
