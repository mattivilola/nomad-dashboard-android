package com.iloapps.nomaddashboard.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iloapps.nomaddashboard.core.data.companion.CompanionRepository
import com.iloapps.nomaddashboard.core.data.repository.NomadDashboardRepository
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.CompanionPreferences
import com.iloapps.nomaddashboard.core.model.ProviderCredentialSettings
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.model.SettingsPage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: NomadDashboardRepository,
    private val companionRepository: CompanionRepository,
) : ViewModel() {
    private val surfSpotLocationState = MutableStateFlow(SurfSpotLocationState())
    private val navigationState = MutableStateFlow(SettingsNavigationState())
    private val actionError = MutableStateFlow<String?>(null)
    private var appliedDeepLink: SettingsDestination? = null

    private val storedState = combine(
        repository.settings,
        repository.providerCredentials,
        companionRepository.data.map { it.preferences }.distinctUntilChanged(),
        companionRepository.storageError,
    ) { settings, providerCredentials, companionPreferences, companionError ->
        SettingsUiState(
            settings = settings,
            providerCredentials = providerCredentials,
            companionPreferences = companionPreferences,
            companionError = companionError,
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        storedState,
        surfSpotLocationState,
        navigationState,
        actionError,
    ) { stored, surfSpotLocation, navigation, error ->
        stored.copy(
            isResolvingSurfSpotLocation = surfSpotLocation.isResolving,
            surfSpotLocationStatus = surfSpotLocation.message,
            surfSpotLocationStatusIsError = surfSpotLocation.isError,
            navigation = navigation,
            companionError = stored.companionError ?: error,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(),
    )

    fun openPage(page: SettingsPage) = navigationState.update { it.openPage(page) }

    fun openDestination(destination: SettingsDestination) = navigationState.update { it.openDestination(destination) }

    fun back() = navigationState.update { it.back() }

    fun focusHandled() = navigationState.update { it.focusHandled() }

    /**
     * Applies the destination a navigation route carried. The same destination is applied once per
     * screen instance, so coming back to Settings from another tab does not scroll again.
     */
    fun applyDeepLink(destination: SettingsDestination?) {
        if (destination == null || destination == appliedDeepLink) return
        appliedDeepLink = destination
        openDestination(destination)
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            repository.updateSettings(transform)
            repository.refresh()
        }
    }

    fun updateProviderCredentials(transform: (ProviderCredentialSettings) -> ProviderCredentialSettings) {
        viewModelScope.launch {
            repository.updateProviderCredentials(transform)
        }
    }

    fun updateCompanionPreferences(transform: (CompanionPreferences) -> CompanionPreferences) {
        viewModelScope.launch {
            try {
                companionRepository.update { current -> current.copy(preferences = transform(current.preferences)) }
                actionError.value = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                actionError.value = error.message ?: "The setting could not be saved."
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun fillSurfSpotFromCurrentLocation() {
        viewModelScope.launch {
            surfSpotLocationState.value = SurfSpotLocationState(isResolving = true)
            val surfSpot = repository.resolveSurfSpotFromCurrentLocation()
            if (surfSpot != null) {
                repository.updateSettings { current -> current.copy(surfSpot = surfSpot) }
                repository.refresh()
                surfSpotLocationState.value = SurfSpotLocationState(
                    message = "Matched current location for the surf spot.",
                    isError = false,
                )
            } else {
                surfSpotLocationState.value = SurfSpotLocationState(
                    message = "Current location unavailable. Check Android location permission and try again.",
                    isError = true,
                )
            }
        }
    }
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val providerCredentials: ProviderCredentialSettings = ProviderCredentialSettings(),
    val companionPreferences: CompanionPreferences = CompanionPreferences(),
    /** Set when the Companion file could not be read or written, so its settings cannot be changed. */
    val companionError: String? = null,
    val isResolvingSurfSpotLocation: Boolean = false,
    val surfSpotLocationStatus: String? = null,
    val surfSpotLocationStatusIsError: Boolean = false,
    val navigation: SettingsNavigationState = SettingsNavigationState(),
)

private data class SurfSpotLocationState(
    val isResolving: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)
