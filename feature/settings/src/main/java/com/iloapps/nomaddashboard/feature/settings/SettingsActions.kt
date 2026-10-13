package com.iloapps.nomaddashboard.feature.settings

import androidx.compose.runtime.Immutable
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.CompanionPreferences
import com.iloapps.nomaddashboard.core.model.ProviderCredentialSettings
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.model.SettingsPage

/** Everything the settings screen can ask its host to do. Every action defaults to doing nothing. */
@Immutable
data class SettingsActions(
    val onOpenPage: (SettingsPage) -> Unit = {},
    val onBack: () -> Unit = {},
    val onOpenDestination: (SettingsDestination) -> Unit = {},
    val onFocusHandled: () -> Unit = {},
    val onUpdate: ((AppSettings) -> AppSettings) -> Unit = {},
    val onUpdateProviderCredentials: ((ProviderCredentialSettings) -> ProviderCredentialSettings) -> Unit = {},
    val onUpdateCompanionPreferences: ((CompanionPreferences) -> CompanionPreferences) -> Unit = {},
    val onFillSurfSpotFromCurrentLocation: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    /** Opens the Companion screen: offline essentials, workplace diary entries and the time zone planner. */
    val onOpenCompanion: () -> Unit = {},
    val onOpenVisitedMap: () -> Unit = {},
    val onOpenAbout: () -> Unit = {},
)

/** What a group's content needs to draw and change itself. */
internal class SettingsGroupScope(
    val state: SettingsUiState,
    val actions: SettingsActions,
    val highlighted: SettingsGroup?,
) {
    val settings: AppSettings get() = state.settings

    fun update(transform: (AppSettings) -> AppSettings) = actions.onUpdate(transform)

    fun updateCompanion(transform: (CompanionPreferences) -> CompanionPreferences) =
        actions.onUpdateCompanionPreferences(transform)
}
