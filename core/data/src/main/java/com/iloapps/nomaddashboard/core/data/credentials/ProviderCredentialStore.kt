package com.iloapps.nomaddashboard.core.data.credentials

import com.iloapps.nomaddashboard.core.model.ProviderCredentialSettings
import kotlinx.coroutines.flow.StateFlow

interface ProviderCredentialStore {
    val credentials: StateFlow<ProviderCredentialSettings>

    suspend fun update(transform: (ProviderCredentialSettings) -> ProviderCredentialSettings)
}
