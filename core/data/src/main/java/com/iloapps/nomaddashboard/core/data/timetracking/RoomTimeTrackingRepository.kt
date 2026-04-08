package com.iloapps.nomaddashboard.core.data.timetracking

import com.iloapps.nomaddashboard.core.common.ApplicationScope
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingEntryDao
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingInterruptionDao
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingProjectDao
import com.iloapps.nomaddashboard.core.database.entity.TimeTrackingEntryEntity
import com.iloapps.nomaddashboard.core.database.entity.toEntity
import com.iloapps.nomaddashboard.core.model.TimeTrackingDayReport
import com.iloapps.nomaddashboard.core.model.TimeTrackingFocusLossPerInterruption
import com.iloapps.nomaddashboard.core.model.TimeTrackingInterruption
import com.iloapps.nomaddashboard.core.model.TimeTrackingProjectReport
import com.iloapps.nomaddashboard.core.model.TimeTrackingReportSnapshot
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Singleton
class RoomTimeTrackingRepository @Inject constructor(
    private val projectDao: TimeTrackingProjectDao,
    private val entryDao: TimeTrackingEntryDao,
    private val interruptionDao: TimeTrackingInterruptionDao,
    private val transactionRunner: TimeTrackingTransactionRunner,
    private val settingsDataSource: NomadSettingsDataSource,
    @ApplicationScope
    private val applicationScope: CoroutineScope,
) : TimeTrackingRepository {
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

    override val report: Flow<TimeTrackingReportSnapshot> = combine(
        entryDao.observeAll(),
        interruptionDao.observeAll(),
        projects,
        ::buildReportSnapshot,
    ).stateIn(applicationScope, SharingStarted.Eagerly, TimeTrackingReportSnapshot())

    init {
        applicationScope.launch {
            ensureBuiltInProjects()
            syncTracking()
        }
        applicationScope.launch {
            settingsDataSource.settings.collect {
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
        val settings = currentSettings()
        transactionRunner.run {
            syncTrackingLocked(now, settings)
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
            val settings = currentSettings()
            syncTrackingLocked(now, settings)
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
            val settings = currentSettings()
            syncTrackingLocked(now, settings)
            val active = entryDao.getActive()?.toModel() ?: return@run StopTrackingResult.NotTracking
            entryDao.upsert(active.copy(endAt = now).toEntity())
            StopTrackingResult.Stopped
        }

    override suspend fun reportInterruption(now: Instant): ReportInterruptionResult =
        transactionRunner.run {
            ensureBuiltInProjects()
            val settings = currentSettings()
            syncTrackingLocked(now, settings)
            if (settings.projectTimeTrackingEnabled.not()) {
                return@run ReportInterruptionResult.TrackingDisabled
            }

            val currentEntryId = entryDao.getActive()?.id
                ?: entryDao.getAll()
                    .firstOrNull { entity -> entity.endAtEpochMillis != null && entity.toModel().isUnallocated() }
                    ?.id

            interruptionDao.upsert(
                TimeTrackingInterruption(
                    entryId = currentEntryId?.let(UUID::fromString),
                    occurredAt = now,
                ).toEntity(),
            )
            ReportInterruptionResult.Recorded
        }

    override suspend fun allocateTrackedTime(projectId: UUID): AllocateTrackedTimeResult =
        transactionRunner.run {
            ensureBuiltInProjects()
            val now = Instant.now()
            val settings = currentSettings()
            syncTrackingLocked(now, settings)
            val project = projectDao.getById(projectId.toString())?.toModel()
                ?.takeIf { it.isArchived.not() }
                ?: return@run AllocateTrackedTimeResult.MissingProject

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

    private suspend fun syncTrackingLocked(
        now: Instant,
        settings: AppSettings,
    ) {
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

    private fun buildReportSnapshot(
        entries: List<TimeTrackingEntryEntity>,
        interruptions: List<com.iloapps.nomaddashboard.core.database.entity.TimeTrackingInterruptionEntity>,
        projects: List<TimeTrackingProject>,
    ): TimeTrackingReportSnapshot {
        val zone = ZoneId.systemDefault()
        val entryById = entries.associate { entity -> entity.id to entity.toModel() }
        val projectById = projects.associateBy { it.id }
        val allocatedRecords = entries
            .mapNotNull { entity -> entity.toRecord(projects) }
            .filter { record -> record.entry.endAt != null && record.entry.isUnallocated().not() }
        val dayAccumulator = linkedMapOf<LocalDate, MutableDayReport>()

        allocatedRecords.forEach { record ->
            splitEntryAcrossDays(record.entry, zone).forEach { segment ->
                val day = dayAccumulator.getOrPut(segment.date) { MutableDayReport(segment.date) }
                day.allocatedDuration += segment.duration
                val projectReport = day.projectReports.getOrPut(record.project.id) {
                    MutableProjectReport(project = record.project)
                }
                projectReport.reportedDuration += segment.duration
            }
        }

        interruptions
            .map { it.toModel() }
            .forEach { interruption ->
                val date = interruption.occurredAt.atZone(zone).toLocalDate()
                val day = dayAccumulator.getOrPut(date) { MutableDayReport(date) }
                day.interruptionCount += 1

                val entry = interruption.entryId?.let { entryId -> entryById[entryId.toString()] }
                if (entry?.isUnallocated() == false) {
                    val project = projectById[entry.projectId]
                    if (project != null) {
                        val projectReport = day.projectReports.getOrPut(project.id) {
                            MutableProjectReport(project = project)
                        }
                        projectReport.interruptionCount += 1
                    }
                }
            }

        val dayReports = dayAccumulator.values
            .map { it.toModel() }
            .sortedByDescending { it.date }
        val today = LocalDate.now(zone)
        val todayReport = dayReports.firstOrNull { it.date == today }
        val lastInterruptionAt = interruptions.maxOfOrNull { it.occurredAtEpochMillis }
            ?.let(Instant::ofEpochMilli)

        return TimeTrackingReportSnapshot(
            interruptionsToday = todayReport?.interruptionCount ?: 0,
            lastInterruptionAt = lastInterruptionAt,
            todaysEstimatedFocusLoss = todayReport?.estimatedFocusLoss ?: Duration.ZERO,
            todaysAllocatedDuration = todayReport?.allocatedDuration ?: Duration.ZERO,
            todaysEstimatedFocusTime = todayReport?.estimatedFocusTime ?: Duration.ZERO,
            dayReports = dayReports,
        )
    }

    private fun splitEntryAcrossDays(
        entry: TimeTrackingEntry,
        zone: ZoneId,
    ): List<DayDurationSegment> {
        val endAt = entry.endAt ?: return emptyList()
        if (endAt.isAfter(entry.startAt).not()) {
            return emptyList()
        }
        val segments = mutableListOf<DayDurationSegment>()
        var cursor = entry.startAt
        while (cursor.isBefore(endAt)) {
            val currentDate = cursor.atZone(zone).toLocalDate()
            val nextBoundary = currentDate.plusDays(1).atStartOfDay(zone).toInstant()
            val segmentEnd = if (endAt.isBefore(nextBoundary)) endAt else nextBoundary
            segments += DayDurationSegment(
                date = currentDate,
                duration = Duration.between(cursor, segmentEnd),
            )
            cursor = segmentEnd
        }
        return segments
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

    private suspend fun currentSettings(): AppSettings = settingsDataSource.settings.first()
}

private data class DayDurationSegment(
    val date: LocalDate,
    val duration: Duration,
)

private data class MutableProjectReport(
    val project: TimeTrackingProject,
    var reportedDuration: Duration = Duration.ZERO,
    var interruptionCount: Int = 0,
) {
    fun toModel(): TimeTrackingProjectReport {
        val estimatedFocusLoss = TimeTrackingFocusLossPerInterruption.multipliedBy(interruptionCount.toLong())
        return TimeTrackingProjectReport(
            project = project,
            reportedDuration = reportedDuration,
            interruptionCount = interruptionCount,
            estimatedFocusLoss = estimatedFocusLoss,
            estimatedFocusTime = (reportedDuration - estimatedFocusLoss).coerceAtLeastZero(),
        )
    }
}

private data class MutableDayReport(
    val date: LocalDate,
    var interruptionCount: Int = 0,
    var allocatedDuration: Duration = Duration.ZERO,
    val projectReports: LinkedHashMap<UUID, MutableProjectReport> = linkedMapOf(),
) {
    fun toModel(): TimeTrackingDayReport {
        val estimatedFocusLoss = TimeTrackingFocusLossPerInterruption.multipliedBy(interruptionCount.toLong())
        return TimeTrackingDayReport(
            date = date,
            interruptionCount = interruptionCount,
            estimatedFocusLoss = estimatedFocusLoss,
            allocatedDuration = allocatedDuration,
            estimatedFocusTime = (allocatedDuration - estimatedFocusLoss).coerceAtLeastZero(),
            projectReports = projectReports.values
                .map { it.toModel() }
                .sortedWith(compareByDescending<TimeTrackingProjectReport> { it.reportedDuration }.thenBy { it.project.name.lowercase() }),
        )
    }
}

private fun Duration.coerceAtLeastZero(): Duration = if (isNegative) Duration.ZERO else this
