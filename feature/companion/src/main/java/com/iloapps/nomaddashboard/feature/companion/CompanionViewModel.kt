package com.iloapps.nomaddashboard.feature.companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iloapps.nomaddashboard.core.common.DefaultDispatcher
import com.iloapps.nomaddashboard.core.data.companion.CompanionRepository
import com.iloapps.nomaddashboard.core.data.repository.NomadDashboardRepository
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.NomadCompanionData
import com.iloapps.nomaddashboard.core.model.OfflineEssential
import com.iloapps.nomaddashboard.core.model.OfflineEssentialKind
import com.iloapps.nomaddashboard.core.model.TimezonePlanner
import com.iloapps.nomaddashboard.core.model.WorkReadiness
import com.iloapps.nomaddashboard.core.model.WorkplaceVisit
import com.iloapps.nomaddashboard.core.travel.safety.SafetyLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

@HiltViewModel
class CompanionViewModel @Inject constructor(
    private val repository: CompanionRepository,
    private val clock: Clock,
    dashboardRepository: NomadDashboardRepository,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val safetyEssentialsBuilder = SafetyEssentialsBuilder()
    val data: StateFlow<NomadCompanionData> = repository.data.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NomadCompanionData())
    val storageError = repository.storageError

    /**
     * The emergency numbers for the current country and the user's embassy, for the top of the Offline
     * essentials. Decided from the saved settings and the last dashboard snapshot, so it needs no network;
     * the bundled mission dataset is read off the main thread.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val safetyEssentials: StateFlow<SafetyEssentials> = combine(
        dashboardRepository.settings.map { it.citizenshipCountryCodes }.distinctUntilChanged(),
        dashboardRepository.snapshot.map { SafetyLocation.of(it.travelContext, it.localInfo.countryCode) }.distinctUntilChanged(),
    ) { citizenships, location -> citizenships to location }
        .mapLatest { (citizenships, location) -> safetyEssentialsBuilder.build(citizenships, location) }
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SafetyEssentials.Empty)

    private val mutableActionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = mutableActionError
    fun update(transform: (NomadCompanionData) -> NomadCompanionData) = launchSafely { repository.update(transform) }
    fun addEssential(kind: OfflineEssentialKind, title: String, content: String, mapUrl: String?) = launchSafely {
        if (title.isBlank() || content.isBlank()) return@launchSafely
        val now = clock.millis()
        repository.saveEssential(OfflineEssential(UUID.randomUUID().toString(), kind, title.trim(), content.trim(), mapUrl?.trim()?.takeIf { it.isNotBlank() }, now, now))
    }
    fun addPlanner(label: String, homeZone: String, clientZone: String) = launchSafely {
        if (label.isBlank() || homeZone.isBlank() || clientZone.isBlank()) return@launchSafely
        require(runCatching { java.time.ZoneId.of(homeZone.trim()); java.time.ZoneId.of(clientZone.trim()) }.isSuccess) { "Use valid IANA timezone IDs, for example Europe/Helsinki." }
        repository.savePlanner(TimezonePlanner(UUID.randomUUID().toString(), label.trim(), homeZone.trim(), clientZone.trim(), updatedAtEpochMillis = clock.millis()))
        repository.update { current -> current.copy(preferences = current.preferences.copy(homeTimeZoneId = homeZone.trim())) }
    }
    fun updateEssential(item: OfflineEssential) = launchSafely { repository.saveEssential(item.copy(updatedAtEpochMillis = clock.millis())) }
    fun updatePlanner(item: TimezonePlanner) = launchSafely {
        require(runCatching { java.time.ZoneId.of(item.homeZoneId); java.time.ZoneId.of(item.clientZoneId) }.isSuccess) { "Use valid IANA timezone IDs." }
        repository.savePlanner(item.copy(updatedAtEpochMillis = clock.millis()))
    }
    fun recordWorkplace(snapshot: DashboardSnapshot, placeLabel: String) = launchSafely {
        saveWorkplaceObservation(snapshot, placeLabel, confirmed = true)
    }
    private suspend fun saveWorkplaceObservation(snapshot: DashboardSnapshot, placeLabel: String, confirmed: Boolean) {
        val current = data.value
        if (!current.preferences.workplaceDiaryEnabled || placeLabel.isBlank()) return
        val now = clock.millis()
        val location = snapshot.travelContext
        val latitude = location.deviceLatitude ?: location.latitude
        val longitude = location.deviceLongitude ?: location.longitude
        val mapUrl = if (current.preferences.retainWorkplaceMapLink) {
            latitude?.let { lat -> longitude?.let { lon -> "https://maps.google.com/?q=$lat,$lon" } }
        } else null
        repository.saveVisit(WorkplaceVisit(
            id = UUID.randomUUID().toString(), placeLabel = placeLabel.trim(), placeLabelConfirmed = confirmed, mapsUrl = mapUrl,
            observedAtEpochMillis = now, internetState = snapshot.connectivity.internetState.label,
            vpnActive = snapshot.connectivity.vpnActive, latencyMs = snapshot.connectivity.latencyMs,
            powerPercent = snapshot.power.batteryPercent, charging = snapshot.power.charging,
            connectionName = snapshot.connectivity.wifiName?.takeIf { current.preferences.retainConnectionName },
            reliabilityNote = if (location.deviceLatitude == null && latitude != null) "Map link uses approximate IP location; confirm before relying on it." else null,
            updatedAtEpochMillis = now,
        ))
    }
    fun updateVisit(visit: WorkplaceVisit) = launchSafely { repository.saveVisit(visit.copy(updatedAtEpochMillis = clock.millis())) }
    suspend fun exportBackup(): String = repository.exportBackup()
    suspend fun previewImport(value: String) = repository.previewImport(value)
    suspend fun mergeImport(value: String) = repository.mergeImport(value)
    private fun launchSafely(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (error: CancellationException) { throw error } catch (error: Throwable) { mutableActionError.value = error.message ?: "Companion action could not be completed." }
    }
}

fun workReadiness(snapshot: DashboardSnapshot): WorkReadiness = when {
    snapshot.connectivity.isChecking || (snapshot.localSignalsAt ?: snapshot.lastRefresh) == null -> WorkReadiness("Work readiness unknown", "Waiting for the device's passive connection reading.", null)
    !snapshot.connectivity.isOnline -> WorkReadiness("Connection unavailable", "Keep local work available; this is an observed state, not a diagnosis.", false)
    snapshot.power.batteryPercent == null -> WorkReadiness("Connection available", "Power status is unavailable, so readiness remains partial.", null)
    (snapshot.power.batteryPercent ?: 100) < 15 && !snapshot.power.charging -> WorkReadiness("Connection available", "Battery is low and not charging.", false)
    else -> WorkReadiness("Ready for focused work", listOfNotNull("Connection observed", if (snapshot.connectivity.vpnActive) "VPN active" else null, if (snapshot.power.charging) "Charging" else null).joinToString(" · "), true)
}
