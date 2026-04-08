package com.iloapps.nomaddashboard.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iloapps.nomaddashboard.core.data.repository.NomadDashboardRepository
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.ProviderCredentialSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: NomadDashboardRepository,
) : ViewModel() {
    private val surfSpotLocationState = MutableStateFlow(SurfSpotLocationState())

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.settings,
        repository.providerCredentials,
        surfSpotLocationState,
    ) { settings, providerCredentials, surfSpotLocation ->
        SettingsUiState(
            settings = settings,
            providerCredentials = providerCredentials,
            isResolvingSurfSpotLocation = surfSpotLocation.isResolving,
            surfSpotLocationStatus = surfSpotLocation.message,
            surfSpotLocationStatusIsError = surfSpotLocation.isError,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(),
    )

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
    val isResolvingSurfSpotLocation: Boolean = false,
    val surfSpotLocationStatus: String? = null,
    val surfSpotLocationStatusIsError: Boolean = false,
)

private data class SurfSpotLocationState(
    val isResolving: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)
