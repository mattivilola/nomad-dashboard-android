package com.iloapps.nomaddashboard.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iloapps.nomaddashboard.core.data.repository.NomadDashboardRepository
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val snapshot: DashboardSnapshot = DashboardSnapshot(),
    val settings: AppSettings = AppSettings(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: NomadDashboardRepository,
) : ViewModel() {
    val uiState: StateFlow<DashboardUiState> = combine(repository.snapshot, repository.settings, ::DashboardUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.refresh()
        }
    }
}

