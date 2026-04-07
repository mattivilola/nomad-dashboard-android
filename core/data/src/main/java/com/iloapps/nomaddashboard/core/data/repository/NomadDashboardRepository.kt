package com.iloapps.nomaddashboard.core.data.repository

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.ProviderCredentialSettings
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface NomadDashboardRepository {
    val settings: Flow<AppSettings>
    val providerCredentials: Flow<ProviderCredentialSettings>
    val snapshot: StateFlow<DashboardSnapshot>
    val visitedPlaces: Flow<List<VisitedPlace>>
    val visitedCountryDays: Flow<List<VisitedCountryDay>>

    suspend fun refresh()
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings)
    suspend fun updateProviderCredentials(transform: (ProviderCredentialSettings) -> ProviderCredentialSettings)
}
