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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: NomadDashboardRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        repository.settings,
        repository.providerCredentials,
    ) { settings, providerCredentials ->
        SettingsUiState(
            settings = settings,
            providerCredentials = providerCredentials,
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
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val providerCredentials: ProviderCredentialSettings = ProviderCredentialSettings(),
)
