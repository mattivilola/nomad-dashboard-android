package com.iloapps.nomaddashboard.core.data.timetracking

import android.content.Context
import android.net.Uri
import com.iloapps.nomaddashboard.core.common.IoDispatcher
import com.iloapps.nomaddashboard.core.data.visited.DatabaseTransactionRunner
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingEntryDao
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingInterruptionDao
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingProjectDao
import com.iloapps.nomaddashboard.core.database.entity.TimeTrackingEntryEntity
import com.iloapps.nomaddashboard.core.database.entity.TimeTrackingInterruptionEntity
import com.iloapps.nomaddashboard.core.database.entity.TimeTrackingProjectEntity
import com.iloapps.nomaddashboard.core.model.TimeTrackingBucket
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private const val TimeBackupSchemaVersion = 1
private const val MaxTimeBackupBytes = 5L * 1024L * 1024L
private const val MaxTimeBackupRecords = 20_000
private const val MaxProjectNameLength = 200

data class TimeTrackingImportPreview(
    val schemaVersion: Int,
    val projectsToAdd: Int,
    val entriesToAdd: Int,
    val interruptionsToAdd: Int,
    val duplicateCount: Int,
    val conflictCount: Int,
    val warnings: List<String>,
)

/** Portable local ledger backup. Active capture and credentials are intentionally excluded. */
@Singleton
class TimeTrackingBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val projectDao: TimeTrackingProjectDao,
    private val entryDao: TimeTrackingEntryDao,
    private val interruptionDao: TimeTrackingInterruptionDao,
    private val transactionRunner: DatabaseTransactionRunner,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun exportCsv(): String = withContext(ioDispatcher) {
        val snapshot = snapshot()
        val projects = snapshot.projects.associateBy { it.id }
        snapshot.completedEntries.joinToString(prefix = "entry_id,project_id,project_name,start_at,end_at,bucket\n", separator = "\n") { entry ->
            listOf(entry.id, entry.projectId, projects[entry.projectId]?.name.orEmpty(), entry.startAtEpochMillis.toString(), entry.endAtEpochMillis.toString(), entry.bucket)
                .joinToString(",") { csv(it) }
        }
    }

    suspend fun exportBackup(): String = withContext(ioDispatcher) {
        val snapshot = snapshot()
        val entryIds = snapshot.completedEntries.map { it.id }.toSet()
        json.encodeToString(TimeTrackingBackup.serializer(), TimeTrackingBackup(
            exportedAtEpochMillis = Instant.now().toEpochMilli(),
            projects = snapshot.projects.map(TimeTrackingProjectEntity::toBackup),
            completedEntries = snapshot.completedEntries.map(TimeTrackingEntryEntity::toBackup),
            interruptions = snapshot.interruptions.filter { it.entryId == null || it.entryId in entryIds }.map(TimeTrackingInterruptionEntity::toBackup),
        ))
    }

    suspend fun saveBackup(uri: Uri) = withContext(ioDispatcher) {
        context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { it.write(exportBackup()) }
            ?: error("The selected backup destination could not be opened.")
    }

    suspend fun previewImport(uri: Uri): TimeTrackingImportPreview = withContext(ioDispatcher) {
        preview(decode(readUri(uri)), snapshot())
    }

    suspend fun importFrom(uri: Uri): TimeTrackingImportPreview = withContext(ioDispatcher) {
        val backup = decode(readUri(uri))
        transactionRunner.run {
            val snapshot = snapshotLocked()
            val preview = preview(backup, snapshot)
            if (preview.conflictCount > 0) return@run preview
            backup.projects.filter { it.id !in snapshot.projectsById }.forEach { projectDao.upsert(it.toEntity()) }
            backup.completedEntries.filter { it.id !in snapshot.entriesById }.forEach { entryDao.upsert(it.toEntity()) }
            backup.interruptions.filter { it.id !in snapshot.interruptionsById }.forEach { interruptionDao.upsert(it.toEntity()) }
            preview
        }
    }

    private fun preview(backup: TimeTrackingBackup, snapshot: TimeTrackingSnapshot): TimeTrackingImportPreview {
        val projects = snapshot.projectsById
        val entries = snapshot.entriesById
        val interruptions = snapshot.interruptionsById
        validateTimeTrackingBackupReferences(
            backup = backup,
            availableProjectIds = snapshot.projectsById.keys + backup.projects.map(ProjectBackup::id),
            completedEntryIds = snapshot.completedEntries.mapTo(mutableSetOf()) { it.id }.apply {
                addAll(backup.completedEntries.map(EntryBackup::id))
            },
            activeEntryId = snapshot.activeEntryId,
        )
        val counts = calculateTimeTrackingImportCounts(
            backup = backup,
            existingProjects = projects.mapValues { it.value.toBackup() },
            existingEntries = entries.mapValues { it.value.toBackup() },
            existingInterruptions = interruptions.mapValues { it.value.toBackup() },
        )
        return TimeTrackingImportPreview(TimeBackupSchemaVersion, counts.projectsToAdd, counts.entriesToAdd, counts.interruptionsToAdd, counts.duplicateCount, counts.conflictCount,
            listOfNotNull("Active capture is never exported or imported.", "Credentials are never included.", if (counts.conflictCount > 0) "Conflicts were found. Import will not overwrite local records." else null))
    }

    private suspend fun snapshot(): TimeTrackingSnapshot = transactionRunner.run { snapshotLocked() }

    private suspend fun snapshotLocked(): TimeTrackingSnapshot {
        val projects = projectDao.getAll()
        val entries = entryDao.getAll()
        val interruptions = interruptionDao.getAll()
        return TimeTrackingSnapshot(
            projects = projects,
            completedEntries = entries.filter { it.endAtEpochMillis != null },
            interruptions = interruptions,
            projectsById = projects.associateBy { it.id },
            entriesById = entries.associateBy { it.id },
            interruptionsById = interruptions.associateBy { it.id },
            activeEntryId = entries.firstOrNull { it.endAtEpochMillis == null }?.id,
        )
    }

    private fun readUri(uri: Uri): String = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        var totalBytes = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            totalBytes += read
            require(totalBytes <= MaxTimeBackupBytes) { "Backup exceeds the 5 MB import limit." }
            output.write(buffer, 0, read)
        }
        output.toString(Charsets.UTF_8.name())
    } ?: error("The selected backup could not be opened.")

    private fun decode(raw: String): TimeTrackingBackup = decodeTimeTrackingBackup(json, raw)
}

internal fun validateTimeTrackingBackupReferences(
    backup: TimeTrackingBackup,
    availableProjectIds: Set<String>,
    completedEntryIds: Set<String>,
    activeEntryId: String?,
) {
    require(backup.completedEntries.all { it.projectId in availableProjectIds }) {
        "Backup entries reference a project that is unavailable locally."
    }
    require(backup.interruptions.all { it.entryId == null || it.entryId in completedEntryIds }) {
        "Backup interruptions must reference completed entries."
    }
    require(backup.completedEntries.none { it.id == activeEntryId }) {
        "Backup cannot replace the active capture."
    }
}

internal data class TimeTrackingImportCounts(
    val projectsToAdd: Int,
    val entriesToAdd: Int,
    val interruptionsToAdd: Int,
    val duplicateCount: Int,
    val conflictCount: Int,
)

internal fun calculateTimeTrackingImportCounts(
    backup: TimeTrackingBackup,
    existingProjects: Map<String, ProjectBackup>,
    existingEntries: Map<String, EntryBackup>,
    existingInterruptions: Map<String, InterruptionBackup>,
): TimeTrackingImportCounts {
    var duplicates = 0
    var conflicts = 0
    fun check(existing: Any?, incoming: Any?) {
        if (existing != null) {
            if (existing == incoming) duplicates++ else conflicts++
        }
    }
    backup.projects.forEach { check(existingProjects[it.id], it) }
    backup.completedEntries.forEach { check(existingEntries[it.id], it) }
    backup.interruptions.forEach { check(existingInterruptions[it.id], it) }
    return TimeTrackingImportCounts(
        projectsToAdd = backup.projects.count { it.id !in existingProjects },
        entriesToAdd = backup.completedEntries.count { it.id !in existingEntries },
        interruptionsToAdd = backup.interruptions.count { it.id !in existingInterruptions },
        duplicateCount = duplicates,
        conflictCount = conflicts,
    )
}

private data class TimeTrackingSnapshot(
    val projects: List<TimeTrackingProjectEntity>,
    val completedEntries: List<TimeTrackingEntryEntity>,
    val interruptions: List<TimeTrackingInterruptionEntity>,
    val projectsById: Map<String, TimeTrackingProjectEntity>,
    val entriesById: Map<String, TimeTrackingEntryEntity>,
    val interruptionsById: Map<String, TimeTrackingInterruptionEntity>,
    val activeEntryId: String?,
)

@Serializable internal data class TimeTrackingBackup(val schemaVersion: Int = TimeBackupSchemaVersion, val exportedAtEpochMillis: Long, val projects: List<ProjectBackup>, val completedEntries: List<EntryBackup>, val interruptions: List<InterruptionBackup>)
@Serializable internal data class ProjectBackup(val id: String, val name: String, val archived: Boolean)
@Serializable internal data class EntryBackup(val id: String, val projectId: String, val startAtEpochMillis: Long, val endAtEpochMillis: Long?, val bucket: String)
@Serializable internal data class InterruptionBackup(val id: String, val entryId: String?, val occurredAtEpochMillis: Long)
private fun TimeTrackingProjectEntity.toBackup() = ProjectBackup(id, name, isArchived)
private fun TimeTrackingEntryEntity.toBackup() = EntryBackup(id, projectId, startAtEpochMillis, endAtEpochMillis, bucket)
private fun TimeTrackingInterruptionEntity.toBackup() = InterruptionBackup(id, entryId, occurredAtEpochMillis)
private fun ProjectBackup.toEntity() = TimeTrackingProjectEntity(id, name, archived)
private fun EntryBackup.toEntity() = TimeTrackingEntryEntity(id, projectId, startAtEpochMillis, endAtEpochMillis, bucket)
private fun InterruptionBackup.toEntity() = TimeTrackingInterruptionEntity(id, entryId, occurredAtEpochMillis)
private fun csv(value: String) = "\"${value.replace("\"", "\"\"")}\""
private val validBuckets = TimeTrackingBucket.entries.mapTo(mutableSetOf()) { it.name }
private fun requireUuid(ids: List<String>) = require(ids.all { id ->
    runCatching { UUID.fromString(id).toString().equals(id, ignoreCase = true) }.getOrDefault(false)
}) { "Backup contains an invalid ID." }

internal fun decodeTimeTrackingBackup(json: Json, raw: String): TimeTrackingBackup =
    json.decodeFromString(TimeTrackingBackup.serializer(), raw).also { backup ->
        require(backup.schemaVersion == TimeBackupSchemaVersion) { "This backup schema is not supported." }
        require(backup.completedEntries.none { it.endAtEpochMillis == null }) { "Active entries cannot be imported." }
        require(backup.projects.size <= MaxTimeBackupRecords && backup.completedEntries.size <= MaxTimeBackupRecords && backup.interruptions.size <= MaxTimeBackupRecords) { "Backup exceeds the import record limit." }
        require(backup.projects.map { it.id }.distinct().size == backup.projects.size && backup.completedEntries.map { it.id }.distinct().size == backup.completedEntries.size && backup.interruptions.map { it.id }.distinct().size == backup.interruptions.size) { "Backup contains duplicate IDs." }
        requireUuid(backup.projects.map(ProjectBackup::id) + backup.completedEntries.map(EntryBackup::id) + backup.interruptions.map(InterruptionBackup::id))
        requireUuid(backup.completedEntries.map(EntryBackup::projectId))
        requireUuid(backup.interruptions.mapNotNull(InterruptionBackup::entryId))
        require(backup.projects.all { it.name.isNotBlank() && it.name.length <= MaxProjectNameLength }) { "Backup contains an invalid project name." }
        require(backup.completedEntries.all { entry -> entry.endAtEpochMillis != null && entry.endAtEpochMillis >= entry.startAtEpochMillis }) { "Backup contains an invalid entry time range." }
        require(backup.completedEntries.all { it.bucket in validBuckets }) { "Backup contains an invalid entry bucket." }
    }
