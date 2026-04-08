package com.iloapps.nomaddashboard.core.data.timetracking

import com.iloapps.nomaddashboard.core.common.ApplicationScope
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingEntryDao
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingProjectDao
import com.iloapps.nomaddashboard.core.database.entity.TimeTrackingEntryEntity
import com.iloapps.nomaddashboard.core.database.entity.toEntity
import com.iloapps.nomaddashboard.core.datastore.NomadSettingsDataSource
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.TimeTrackingBucket
import com.iloapps.nomaddashboard.core.model.TimeTrackingEntry
import com.iloapps.nomaddashboard.core.model.TimeTrackingOtherProjectId
import com.iloapps.nomaddashboard.core.model.TimeTrackingOtherProjectName
import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
import com.iloapps.nomaddashboard.core.model.TimeTrackingRecord
import com.iloapps.nomaddashboard.core.model.isAutomaticallyTracked
import com.iloapps.nomaddashboard.core.model.isUnallocated
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Singleton
class RoomTimeTrackingRepository @Inject constructor(
    private val projectDao: TimeTrackingProjectDao,
    private val entryDao: TimeTrackingEntryDao,
    private val transactionRunner: TimeTrackingTransactionRunner,
    private val settingsDataSource: NomadSettingsDataSource,
    @ApplicationScope
    private val applicationScope: CoroutineScope,
) : TimeTrackingRepository {
    private val settingsState = settingsDataSource.settings
        .stateIn(applicationScope, SharingStarted.Eagerly, AppSettings())

    override val projects: Flow<List<TimeTrackingProject>> = projectDao.observeAll()
        .map { entities -> entities.map { it.toModel() }.sortedProjects() }
        .stateIn(applicationScope, SharingStarted.Eagerly, emptyList())

    override val recentEntries: Flow<List<TimeTrackingRecord>> = combine(
        entryDao.observeAll(),
        projects,
        ::mergeAllocatedRecords,
    ).stateIn(applicationScope, SharingStarted.Eagerly, emptyList())

    override val pendingEntries: Flow<List<TimeTrackingRecord>> = combine(
        entryDao.observeAll(),
        projects,
        ::mergePendingRecords,
    ).stateIn(applicationScope, SharingStarted.Eagerly, emptyList())

    override val activeEntry: Flow<TimeTrackingRecord?> = combine(
        entryDao.observeActive(),
        projects,
    ) { entity, items ->
        entity?.toRecord(items)?.takeIf { it.entry.isUnallocated() }
    }.stateIn(applicationScope, SharingStarted.Eagerly, null)

    init {
        applicationScope.launch {
            ensureBuiltInProjects()
            syncTracking()
        }
        applicationScope.launch {
            settingsState.collect {
                syncTracking()
            }
        }
        applicationScope.launch {
            while (true) {
                delay(60_000)
                syncTracking()
            }
        }
    }

    override suspend fun currentActiveEntry(): TimeTrackingRecord? {
        syncTracking()
        val active = entryDao.getActive()?.toModel() ?: return null
        val project = projectDao.getById(active.projectId.toString())?.toModel() ?: return null
        return TimeTrackingRecord(entry = active, project = project)
    }

    override suspend fun syncTracking(now: Instant) {
        ensureBuiltInProjects()
        transactionRunner.run {
            syncTrackingLocked(now)
        }
    }

    override suspend fun createProject(name: String): CreateProjectResult {
        ensureBuiltInProjects()
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) {
            return CreateProjectResult.InvalidName
        }

        if (normalizedName.equals(TimeTrackingOtherProjectName, ignoreCase = true)) {
            val other = projectDao.getById(TimeTrackingOtherProjectId.toString())?.toModel()
                ?: TimeTrackingProject(id = TimeTrackingOtherProjectId, name = TimeTrackingOtherProjectName)
            projectDao.upsert(other.toEntity())
            return CreateProjectResult.Existing(other)
        }

        projectDao.findByName(normalizedName)?.toModel()?.let { existing ->
            return CreateProjectResult.Existing(existing)
        }

        val project = TimeTrackingProject(name = normalizedName)
        projectDao.upsert(project.toEntity())
        return CreateProjectResult.Created(project)
    }

    override suspend fun startTracking(): StartTrackingResult =
        transactionRunner.run {
            ensureBuiltInProjects()
            val now = Instant.now()
            syncTrackingLocked(now)
            if (entryDao.getActive() != null) {
                return@run StartTrackingResult.AlreadyTracking
            }

            entryDao.upsert(newUnallocatedEntry(bucket = TimeTrackingBucket.UNALLOCATED_MANUAL, startAt = now).toEntity())
            StartTrackingResult.Started
        }

    override suspend fun stopTracking(): StopTrackingResult =
        transactionRunner.run {
            ensureBuiltInProjects()
            val now = Instant.now()
            syncTrackingLocked(now)
            val active = entryDao.getActive()?.toModel() ?: return@run StopTrackingResult.NotTracking
            entryDao.upsert(active.copy(endAt = now).toEntity())
            StopTrackingResult.Stopped
        }

    override suspend fun allocateTrackedTime(projectId: UUID): AllocateTrackedTimeResult =
        transactionRunner.run {
            ensureBuiltInProjects()
            val now = Instant.now()
            syncTrackingLocked(now)
            val project = projectDao.getById(projectId.toString())?.toModel()
                ?.takeIf { it.isArchived.not() }
                ?: return@run AllocateTrackedTimeResult.MissingProject

            val settings = settingsState.value
            val activeBeforeAllocation = entryDao.getActive()?.toModel()
            val pending = entryDao.getAll()
                .map { it.toModel() }
                .filter { it.isUnallocated() }
            if (pending.isEmpty()) {
                return@run AllocateTrackedTimeResult.NothingToAllocate
            }

            val allocatedBucket = if (project.id == TimeTrackingOtherProjectId) {
                TimeTrackingBucket.ALLOCATED_OTHER
            } else {
                TimeTrackingBucket.ALLOCATED_PROJECT
            }
            val updated = pending.map { entry ->
                val closed = if (entry.endAt == null) entry.copy(endAt = now) else entry
                closed.copy(
                    projectId = project.id,
                    bucket = allocatedBucket.name,
                )
            }
            entryDao.upsertAll(updated.map(TimeTrackingEntry::toEntity))

            val restartBucket = when {
                activeBeforeAllocation == null && shouldAutoTrackNow(now, settings) -> TimeTrackingBucket.UNALLOCATED_AUTO
                activeBeforeAllocation?.isAutomaticallyTracked() == true && shouldAutoTrackNow(now, settings) ->
                    TimeTrackingBucket.UNALLOCATED_AUTO
                activeBeforeAllocation != null -> TimeTrackingBucket.UNALLOCATED_MANUAL
                else -> null
            }
            restartBucket?.let { bucket ->
                entryDao.upsert(newUnallocatedEntry(bucket = bucket, startAt = now).toEntity())
            }

            AllocateTrackedTimeResult.Allocated(entryCount = updated.size)
        }

    override suspend fun updateEntry(
        entryId: UUID,
        startAt: Instant,
        endAt: Instant,
    ): UpdateTimeTrackingEntryResult =
        transactionRunner.run {
            if (endAt.isAfter(startAt).not()) {
                return@run UpdateTimeTrackingEntryResult.InvalidRange
            }

            val existing = entryDao.getById(entryId.toString())?.toModel()
                ?: return@run UpdateTimeTrackingEntryResult.MissingEntry
            if (existing.endAt == null) {
                return@run UpdateTimeTrackingEntryResult.InvalidRange
            }

            entryDao.upsert(existing.copy(startAt = startAt, endAt = endAt).toEntity())
            UpdateTimeTrackingEntryResult.Updated
        }

    private suspend fun syncTrackingLocked(now: Instant) {
        val settings = settingsState.value
        val active = entryDao.getActive()?.toModel()

        if (settings.projectTimeTrackingEnabled.not()) {
            if (active != null) {
                entryDao.upsert(active.copy(endAt = now).toEntity())
            }
            return
        }

        if (active?.isAutomaticallyTracked() == true) {
            automaticSessionEnd(startAt = active.startAt, settings = settings)
                ?.takeIf { now.isBefore(it).not() }
                ?.let { sessionEnd ->
                    entryDao.upsert(active.copy(endAt = sessionEnd).toEntity())
                }
        }

        val refreshedActive = entryDao.getActive()?.toModel()
        val hasPendingUnallocated = entryDao.getAll()
            .any { entity -> entity.endAtEpochMillis != null && entity.toModel().isUnallocated() }
        if (refreshedActive == null && hasPendingUnallocated.not() && shouldAutoTrackNow(now, settings)) {
            entryDao.upsert(
                newUnallocatedEntry(
                    bucket = TimeTrackingBucket.UNALLOCATED_AUTO,
                    startAt = now,
                ).toEntity(),
            )
        }
    }

    private suspend fun ensureBuiltInProjects() {
        val existingOther = projectDao.getById(TimeTrackingOtherProjectId.toString())
        if (existingOther == null) {
            projectDao.upsert(
                TimeTrackingProject(
                    id = TimeTrackingOtherProjectId,
                    name = TimeTrackingOtherProjectName,
                ).toEntity(),
            )
        }
    }

    private fun mergeAllocatedRecords(
        entries: List<TimeTrackingEntryEntity>,
        projects: List<TimeTrackingProject>,
    ): List<TimeTrackingRecord> = entries
        .mapNotNull { entity -> entity.toRecord(projects) }
        .filter { record -> record.entry.endAt != null && record.entry.isUnallocated().not() }
        .sortedByDescending { it.entry.startAt }

    private fun mergePendingRecords(
        entries: List<TimeTrackingEntryEntity>,
        projects: List<TimeTrackingProject>,
    ): List<TimeTrackingRecord> = entries
        .mapNotNull { entity -> entity.toRecord(projects) }
        .filter { record -> record.entry.endAt != null && record.entry.isUnallocated() }
        .sortedByDescending { it.entry.startAt }

    private fun TimeTrackingEntryEntity.toRecord(
        projects: List<TimeTrackingProject>,
    ): TimeTrackingRecord? {
        val entry = toModel()
        val project = projects.firstOrNull { it.id == entry.projectId } ?: return null
        return TimeTrackingRecord(entry = entry, project = project)
    }

    private fun newUnallocatedEntry(
        bucket: TimeTrackingBucket,
        startAt: Instant,
    ): TimeTrackingEntry =
        TimeTrackingEntry(
            projectId = TimeTrackingOtherProjectId,
            startAt = startAt,
            bucket = bucket.name,
        )

    private fun automaticSessionEnd(
        startAt: Instant,
        settings: AppSettings,
    ): Instant? {
        val endMinutes = settings.projectTimeTrackingAutoStopMinutes
        val endTime = LocalTime.of(endMinutes / 60, endMinutes % 60)
        val zone = ZoneId.systemDefault()
        val startZoned = startAt.atZone(zone)
        var endAt = startZoned.toLocalDate().atTime(endTime).atZone(zone)
        if (endAt.toInstant().isAfter(startAt).not()) {
            endAt = endAt.plusDays(1)
        }
        return endAt.toInstant()
    }

    private fun shouldAutoTrackNow(
        now: Instant,
        settings: AppSettings,
    ): Boolean {
        if (settings.projectTimeTrackingEnabled.not()) {
            return false
        }
        val startMinutes = settings.projectTimeTrackingAutoStartMinutes
        val endMinutes = settings.projectTimeTrackingAutoStopMinutes
        if (endMinutes <= startMinutes) {
            return false
        }
        val nowLocal = now.atZone(ZoneId.systemDefault()).toLocalTime()
        val startTime = LocalTime.of(startMinutes / 60, startMinutes % 60)
        val endTime = LocalTime.of(endMinutes / 60, endMinutes % 60)
        return nowLocal >= startTime && nowLocal < endTime
    }

    private fun List<TimeTrackingProject>.sortedProjects(): List<TimeTrackingProject> =
        sortedWith(compareBy<TimeTrackingProject>({ it.id == TimeTrackingOtherProjectId }, { it.name.lowercase() }))
}
