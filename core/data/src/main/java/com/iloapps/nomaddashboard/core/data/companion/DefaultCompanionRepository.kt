package com.iloapps.nomaddashboard.core.data.companion

import com.iloapps.nomaddashboard.core.model.CompanionImportPreview
import com.iloapps.nomaddashboard.core.model.NomadCompanionData
import com.iloapps.nomaddashboard.core.model.OfflineEssential
import com.iloapps.nomaddashboard.core.model.TimezonePlanner
import com.iloapps.nomaddashboard.core.model.WorkplaceVisit
import com.iloapps.nomaddashboard.core.common.IoDispatcher
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class DefaultCompanionRepository @Inject constructor(
    private val storage: CompanionStorage,
    private val json: kotlinx.serialization.json.Json,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CompanionRepository {
    override val data: StateFlow<NomadCompanionData> = storage.data
    override val storageError: StateFlow<String?> = storage.error
    private val backupCodec = CompanionBackupCodec(json)

    override suspend fun update(transform: (NomadCompanionData) -> NomadCompanionData) = storage.update(transform)
    override suspend fun saveEssential(item: OfflineEssential) = update { current ->
        current.copy(essentials = current.essentials.replaceById(item) { it.id })
    }
    override suspend fun savePlanner(planner: TimezonePlanner) = update { current ->
        current.copy(planners = current.planners.replaceById(planner) { it.id })
    }
    override suspend fun saveVisit(visit: WorkplaceVisit) = update { current ->
        current.copy(workplaceVisits = current.workplaceVisits.replaceById(visit) { it.id })
    }
    override suspend fun exportBackup(): String = withContext(ioDispatcher) { backupCodec.export(data.value, clock.millis()) }
    override suspend fun previewImport(serialized: String): CompanionImportPreview = withContext(ioDispatcher) { backupCodec.preview(data.value, serialized) }
    override suspend fun mergeImport(serialized: String): CompanionImportPreview {
        return storage.mergeBackup(backupCodec, serialized, clock.millis())
    }
}

private fun <T> List<T>.replaceById(item: T, id: (T) -> String): List<T> =
    filterNot { id(it) == id(item) } + item
