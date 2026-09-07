package com.iloapps.nomaddashboard.core.data.companion

import android.content.Context
import com.iloapps.nomaddashboard.core.common.ApplicationScope
import com.iloapps.nomaddashboard.core.common.IoDispatcher
import com.iloapps.nomaddashboard.core.model.COMPANION_SCHEMA_VERSION
import com.iloapps.nomaddashboard.core.model.CompanionConflictNote
import com.iloapps.nomaddashboard.core.model.CompanionImportPreview
import com.iloapps.nomaddashboard.core.model.NomadCompanionBackup
import com.iloapps.nomaddashboard.core.model.NomadCompanionData
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@Singleton
class CompanionStorage @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope applicationScope: CoroutineScope,
) {
    private val file = File(context.noBackupFilesDir, "nomad-companion-v1.json")
    private val writeMutex = Mutex()
    private val initialLoadFinished = CompletableDeferred<Unit>()
    private var loadFailure: Throwable? = null
    private val mutableData = MutableStateFlow(NomadCompanionData())
    private val mutableError = MutableStateFlow<String?>(null)
    val data: StateFlow<NomadCompanionData> = mutableData
    val error: StateFlow<String?> = mutableError

    init {
        applicationScope.launch {
            try {
                load()
            } finally {
                initialLoadFinished.complete(Unit)
            }
        }
    }

    suspend fun load(): NomadCompanionData = writeMutex.withLock {
        val loaded = withContext(ioDispatcher) {
            if (!file.exists()) {
                NomadCompanionData()
            } else {
                runCatching {
                    json.decodeFromString(NomadCompanionData.serializer(), file.readText())
                }.getOrElse { error ->
                    loadFailure = IllegalStateException("Companion data could not be read; the existing file was kept.", error)
                    mutableError.value = loadFailure?.message
                    NomadCompanionData()
                }
            }
        }
        if (loaded.schemaVersion != COMPANION_SCHEMA_VERSION) {
            loadFailure = IllegalStateException("Companion data uses an unsupported schema; the existing file was kept.")
            mutableError.value = loadFailure?.message
            mutableData.value = NomadCompanionData()
        } else {
            mutableData.value = loaded
        }
        mutableData.value
    }

    suspend fun update(transform: (NomadCompanionData) -> NomadCompanionData) {
        initialLoadFinished.await()
        writeMutex.withLock {
            loadFailure?.let { failure ->
                mutableError.value = failure.message ?: "Companion data cannot be safely overwritten."
                throw failure
            }
            val normalized = transform(mutableData.value).copy(schemaVersion = COMPANION_SCHEMA_VERSION)
            persist(normalized)
            mutableData.value = normalized
        }
    }

    internal suspend fun mergeBackup(codec: CompanionBackupCodec, serialized: String, now: Long): CompanionImportPreview {
        initialLoadFinished.await()
        return writeMutex.withLock {
            loadFailure?.let { failure ->
                mutableError.value = failure.message ?: "Companion data cannot be safely overwritten."
                throw failure
            }
            val (merged, preview) = withContext(ioDispatcher) { codec.merge(mutableData.value, serialized, now) }
            if (preview.accepted) {
                val normalized = merged.copy(schemaVersion = COMPANION_SCHEMA_VERSION)
                persist(normalized)
                mutableData.value = normalized
            }
            preview
        }
    }

    private suspend fun persist(normalized: NomadCompanionData) {
        withContext(ioDispatcher) {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            FileOutputStream(temporary).use { stream ->
                stream.write(json.encodeToString(NomadCompanionData.serializer(), normalized).toByteArray())
                stream.fd.sync()
            }
            if (!temporary.renameTo(file)) {
                temporary.delete()
                throw IllegalStateException("Could not atomically save Companion data")
            }
        }
    }
}

internal class CompanionBackupCodec(private val json: Json) {
    fun export(data: NomadCompanionData, now: Long): String = json.encodeToString(
        NomadCompanionBackup.serializer(),
        NomadCompanionBackup(exportedAtEpochMillis = now, data = data),
    )

    fun decode(serialized: String): NomadCompanionBackup? = when {
        serialized.length > MAX_BACKUP_CHARS -> null
        else -> runCatching { json.decodeFromString(NomadCompanionBackup.serializer(), serialized) }
            .getOrNull()?.takeIf { it.format == "nomad-companion" && it.version == COMPANION_SCHEMA_VERSION && isValid(it.data) }
    }

    fun preview(current: NomadCompanionData, serialized: String): CompanionImportPreview {
        val backup = decode(serialized) ?: return CompanionImportPreview(false, message = "This is not a supported Companion backup.")
        return CompanionImportPreview(
            accepted = true,
            version = backup.version,
            essentialsToAdd = backup.data.essentials.count { incoming -> current.essentials.none { it.id == incoming.id } },
            visitsToAdd = backup.data.workplaceVisits.count { incoming -> current.workplaceVisits.none { it.id == incoming.id } },
            plannersToAdd = backup.data.planners.count { incoming -> current.planners.none { it.id == incoming.id } },
            conflicts = backup.data.essentials.count { incoming -> current.essentials.any { it.id == incoming.id && it != incoming } } +
                backup.data.workplaceVisits.count { incoming -> current.workplaceVisits.any { it.id == incoming.id && it != incoming } } +
                backup.data.planners.count { incoming -> current.planners.any { it.id == incoming.id && it != incoming } },
            message = "Review additions and conflicts before merging. Credentials and dashboard history are never included.",
        )
    }

    fun merge(current: NomadCompanionData, serialized: String, now: Long): Pair<NomadCompanionData, CompanionImportPreview> {
        val preview = preview(current, serialized)
        val backup = decode(serialized) ?: return current to preview
        fun <T> mergeById(currentItems: List<T>, incoming: List<T>, id: (T) -> String): List<T> =
            currentItems + incoming.filter { candidate -> currentItems.none { id(it) == id(candidate) } }
        val merged = current.copy(
            essentials = mergeById(current.essentials, backup.data.essentials, { it.id }),
            workplaceVisits = mergeById(current.workplaceVisits, backup.data.workplaceVisits, { it.id }),
            planners = mergeById(current.planners, backup.data.planners, { it.id }),
            conflictNotes = current.conflictNotes + if (preview.conflicts == 0) emptyList() else listOf(
                CompanionConflictNote("backup-${now}", "Merged ${preview.conflicts} conflicting item(s); the local item was kept.", now),
            ),
        )
        return merged to preview.copy(message = if (preview.conflicts == 0) "Backup merged." else "Backup merged; local conflicting items were kept and noted.")
    }

    private companion object { const val MAX_BACKUP_CHARS = 1_000_000 }

    private fun isValid(data: NomadCompanionData): Boolean {
        if (data.schemaVersion != COMPANION_SCHEMA_VERSION || data.essentials.size > 500 || data.workplaceVisits.size > 2_000 || data.planners.size > 100) return false
        fun validIds(ids: List<String>) = ids.all { it.length in 1..128 } && ids.distinct().size == ids.size
        return validIds(data.essentials.map { it.id }) && validIds(data.workplaceVisits.map { it.id }) && validIds(data.planners.map { it.id }) &&
            data.essentials.all { it.title.length <= 200 && it.content.length <= 10_000 && (it.mapUrl?.let(::validMapUrl) != false) } &&
            data.workplaceVisits.all { it.placeLabel.length <= 200 && (it.connectionName?.length ?: 0) <= 128 && it.mapsUrl?.let(::validMapUrl) != false && it.latencyMs?.isFinite() != false } &&
            data.planners.all { it.label.length <= 200 && runCatching { java.time.ZoneId.of(it.homeZoneId); java.time.ZoneId.of(it.clientZoneId) }.isSuccess && it.homeStartMinute in 0..1439 && it.homeEndMinute in 0..1439 && it.clientStartMinute in 0..1439 && it.clientEndMinute in 0..1439 }
    }

    private fun validMapUrl(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        uri.scheme in setOf("https", "geo") && (uri.scheme == "geo" || (uri.host == "google.com" || uri.host?.endsWith(".google.com") == true) || (uri.host == "googleapis.com" || uri.host?.endsWith(".googleapis.com") == true))
    }.getOrDefault(false)
}
