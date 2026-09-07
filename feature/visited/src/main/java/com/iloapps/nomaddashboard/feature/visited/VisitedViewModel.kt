package com.iloapps.nomaddashboard.feature.visited

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iloapps.nomaddashboard.core.data.repository.NomadDashboardRepository
import com.iloapps.nomaddashboard.core.data.visited.TravelHistoryManager
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedPlaceEvent
import com.iloapps.nomaddashboard.core.model.TravelHistoryImportPreview
import com.iloapps.nomaddashboard.core.model.TravelStayReminder
import android.net.Uri
import java.time.LocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VisitedUiState(
    val settings: AppSettings = AppSettings(),
    val places: List<VisitedPlace> = emptyList(),
    val countryDays: List<VisitedCountryDay> = emptyList(),
    val placeEvents: List<VisitedPlaceEvent> = emptyList(),
    val isRefreshing: Boolean = false,
    val reminders: List<TravelStayReminder> = emptyList(),
    val importPreview: TravelHistoryImportPreview? = null,
    val travelActionError: String? = null,
)

data class TravelShareContent(val title: String, val text: String)
private data class TravelImportUi(
    val preview: TravelHistoryImportPreview? = null,
    val error: String? = null,
)

@HiltViewModel
class VisitedViewModel @Inject constructor(
    private val repository: NomadDashboardRepository,
    private val travelHistoryManager: TravelHistoryManager,
) : ViewModel() {
    private val _shareContent = MutableSharedFlow<TravelShareContent>(extraBufferCapacity = 1)
    val shareContent: SharedFlow<TravelShareContent> = _shareContent.asSharedFlow()

    private val baseState = combine(
        repository.settings,
        repository.visitedPlaces,
        repository.visitedCountryDays,
        repository.visitedPlaceEvents,
        repository.snapshot,
    ) { settings, places, countryDays, placeEvents, snapshot ->
        VisitedUiState(
            settings = settings,
            places = places,
            countryDays = countryDays,
            placeEvents = placeEvents,
            isRefreshing = snapshot.isRefreshing,
        )
    }

    private val importUi = MutableStateFlow(TravelImportUi())

    val uiState: StateFlow<VisitedUiState> = combine(baseState, travelHistoryManager.reminders, importUi) { state, reminders, actions ->
        state.copy(reminders = reminders, importPreview = actions.preview, travelActionError = actions.error)
    }.stateIn(
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

    fun correctCountryDay(date: LocalDate, country: String, countryCode: String?) {
        viewModelScope.launch {
            runCatching { travelHistoryManager.correctCountryDay(date, country, countryCode) }
        }
    }

    fun restoreOriginalCountryDay(date: LocalDate) {
        viewModelScope.launch { runCatching { travelHistoryManager.restoreOriginalCountryDay(date) } }
    }

    fun addStayReminder(label: String, country: String?, targetDate: LocalDate, note: String?) {
        viewModelScope.launch { runCatching { travelHistoryManager.saveReminder(label, country, targetDate, note) } }
    }

    fun deleteStayReminder(id: String) {
        viewModelScope.launch { travelHistoryManager.deleteReminder(id) }
    }

    fun shareCountryDays(year: Int?, csv: Boolean) {
        viewModelScope.launch {
            val content = if (csv) travelHistoryManager.exportCsv(year) else travelHistoryManager.exportText(year)
            _shareContent.tryEmit(TravelShareContent(if (csv) "Nomad travel CSV" else "Nomad travel summary", content))
        }
    }

    fun previewImport(uri: Uri) {
        viewModelScope.launch {
            runCatching { travelHistoryManager.previewImport(uri) }
                .onSuccess { preview -> importUi.value = TravelImportUi(preview = preview) }
                .onFailure { importUi.value = TravelImportUi(error = it.message ?: "The backup could not be read.") }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching { travelHistoryManager.importFrom(uri) }
                .onSuccess { preview ->
                    importUi.value = if (preview.conflictCount == 0) TravelImportUi() else TravelImportUi(preview = preview)
                }
                .onFailure { importUi.value = TravelImportUi(error = it.message ?: "The backup could not be imported.") }
        }
    }

    fun shareBackup() {
        viewModelScope.launch {
            _shareContent.tryEmit(TravelShareContent("Nomad travel backup", travelHistoryManager.exportBackup()))
        }
    }

    fun saveBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching { travelHistoryManager.saveBackup(uri) }
                .onFailure { importUi.value = TravelImportUi(error = it.message ?: "The backup could not be saved.") }
        }
    }
}
