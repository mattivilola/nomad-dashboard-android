package com.iloapps.nomaddashboard.feature.visited

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iloapps.nomaddashboard.core.data.repository.NomadDashboardRepository
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedPlaceEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VisitedUiState(
    val settings: AppSettings = AppSettings(),
    val places: List<VisitedPlace> = emptyList(),
    val countryDays: List<VisitedCountryDay> = emptyList(),
    val placeEvents: List<VisitedPlaceEvent> = emptyList(),
)

@HiltViewModel
class VisitedViewModel @Inject constructor(
    private val repository: NomadDashboardRepository,
) : ViewModel() {
    val uiState: StateFlow<VisitedUiState> = combine(
        repository.settings,
        repository.visitedPlaces,
        repository.visitedCountryDays,
        repository.visitedPlaceEvents,
        ::VisitedUiState,
    ).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        VisitedUiState(),
    )

    fun refresh() {
        viewModelScope.launch {
            repository.refresh()
        }
    }

    fun clearVisitedHistory() {
        viewModelScope.launch {
            repository.clearVisitedHistory()
        }
    }
}
