package com.iloapps.nomaddashboard.core.data.timetracking

import com.iloapps.nomaddashboard.core.common.ApplicationScope
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingEntryDao
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingProjectDao
import com.iloapps.nomaddashboard.core.database.entity.TimeTrackingEntryEntity
import com.iloapps.nomaddashboard.core.database.entity.toEntity
import com.iloapps.nomaddashboard.core.model.TimeTrackingEntry
import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
import com.iloapps.nomaddashboard.core.model.TimeTrackingRecord
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Singleton
class RoomTimeTrackingRepository @Inject constructor(
    private val projectDao: TimeTrackingProjectDao,
    private val entryDao: TimeTrackingEntryDao,
    private val transactionRunner: TimeTrackingTransactionRunner,
    @ApplicationScope
    applicationScope: CoroutineScope,
) : TimeTrackingRepository {
    override val projects: Flow<List<TimeTrackingProject>> = projectDao.observeAll()
        .map { entities -> entities.map { it.toModel() } }
        .stateIn(applicationScope, SharingStarted.Eagerly, emptyList())

    override val recentEntries: Flow<List<TimeTrackingRecord>> = combine(
        entryDao.observeCompleted(),
        projects,
        ::mergeRecords,
    ).stateIn(applicationScope, SharingStarted.Eagerly, emptyList())

    override val activeEntry: Flow<TimeTrackingRecord?> = combine(
        entryDao.observeActive(),
        projects,
    ) { entity, items ->
        entity?.toRecord(items)
    }.stateIn(applicationScope, SharingStarted.Eagerly, null)

    override suspend fun currentActiveEntry(): TimeTrackingRecord? {
        val active = entryDao.getActive()?.toModel() ?: return null
        val project = projectDao.getById(active.projectId.toString())?.toModel() ?: return null
        return TimeTrackingRecord(entry = active, project = project)
    }

    override suspend fun createProject(name: String): CreateProjectResult {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) {
            return CreateProjectResult.InvalidName
        }

        projectDao.findByName(normalizedName)?.toModel()?.let { existing ->
            return CreateProjectResult.Existing(existing)
        }

        val project = TimeTrackingProject(name = normalizedName)
        projectDao.upsert(project.toEntity())
        return CreateProjectResult.Created(project)
    }

    override suspend fun startTracking(projectId: UUID): StartTrackingResult =
        transactionRunner.run {
            if (entryDao.getActive() != null) {
                return@run StartTrackingResult.AlreadyTracking
            }

            val project = projectDao.getById(projectId.toString()) ?: return@run StartTrackingResult.MissingProject
            if (project.isArchived) {
                return@run StartTrackingResult.MissingProject
            }

            entryDao.upsert(
                TimeTrackingEntry(
                    projectId = projectId,
                    startAt = Instant.now(),
                ).toEntity(),
            )
            StartTrackingResult.Started
        }

    override suspend fun stopTracking(): StopTrackingResult =
        transactionRunner.run {
            val active = entryDao.getActive()?.toModel() ?: return@run StopTrackingResult.NotTracking
            entryDao.upsert(active.copy(endAt = Instant.now()).toEntity())
            StopTrackingResult.Stopped
        }

    private fun mergeRecords(
        entries: List<TimeTrackingEntryEntity>,
        projects: List<TimeTrackingProject>,
    ): List<TimeTrackingRecord> = entries.mapNotNull { entity -> entity.toRecord(projects) }

    private fun TimeTrackingEntryEntity.toRecord(
        projects: List<TimeTrackingProject>,
    ): TimeTrackingRecord? {
        val entry = toModel()
        val project = projects.firstOrNull { it.id == entry.projectId } ?: return null
        return TimeTrackingRecord(entry = entry, project = project)
    }
}
