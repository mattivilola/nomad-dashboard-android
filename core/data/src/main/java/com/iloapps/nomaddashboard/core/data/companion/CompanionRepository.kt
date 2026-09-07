package com.iloapps.nomaddashboard.core.data.companion

import com.iloapps.nomaddashboard.core.model.CompanionImportPreview
import com.iloapps.nomaddashboard.core.model.NomadCompanionData
import com.iloapps.nomaddashboard.core.model.OfflineEssential
import com.iloapps.nomaddashboard.core.model.TimezonePlanner
import com.iloapps.nomaddashboard.core.model.WorkplaceVisit
import kotlinx.coroutines.flow.StateFlow

interface CompanionRepository {
    val data: StateFlow<NomadCompanionData>
    val storageError: StateFlow<String?>
    suspend fun update(transform: (NomadCompanionData) -> NomadCompanionData)
    suspend fun saveEssential(item: OfflineEssential)
    suspend fun savePlanner(planner: TimezonePlanner)
    suspend fun saveVisit(visit: WorkplaceVisit)
    suspend fun exportBackup(): String
    suspend fun previewImport(serialized: String): CompanionImportPreview
    suspend fun mergeImport(serialized: String): CompanionImportPreview
}
