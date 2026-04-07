package com.iloapps.nomaddashboard.core.data.repository

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface NomadDashboardRepository {
    val settings: Flow<AppSettings>
    val snapshot: StateFlow<DashboardSnapshot>

    suspend fun refresh()
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings)
}

