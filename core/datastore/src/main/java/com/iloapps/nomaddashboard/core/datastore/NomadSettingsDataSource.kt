package com.iloapps.nomaddashboard.core.datastore

import androidx.datastore.core.DataStore
import com.iloapps.nomaddashboard.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NomadSettingsDataSource(
    private val dataStore: DataStore<AppSettingsProto>,
) {
    val settings: Flow<AppSettings> = dataStore.data.map(AppSettingsProto::toExternalModel)

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.updateData { current ->
            transform(current.toExternalModel()).toProto()
        }
    }
}

