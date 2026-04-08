package com.iloapps.nomaddashboard.core.data.timetracking

import androidx.datastore.core.DataStore
import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingEntryDao
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingInterruptionDao
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingProjectDao
import com.iloapps.nomaddashboard.core.database.entity.TimeTrackingEntryEntity
import com.iloapps.nomaddashboard.core.database.entity.TimeTrackingInterruptionEntity
import com.iloapps.nomaddashboard.core.database.entity.TimeTrackingProjectEntity
import com.iloapps.nomaddashboard.core.datastore.AppSettingsProto
import com.iloapps.nomaddashboard.core.datastore.NomadSettingsDataSource
import com.iloapps.nomaddashboard.core.model.TimeTrackingBucket
import com.iloapps.nomaddashboard.core.model.TimeTrackingOtherProjectId
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomTimeTrackingRepositoryTest {
    @Test
    fun `create project persists and emits project list with built-in other`() = runTest {
        val repository = repository(applicationScope = backgroundScope)

        repository.createProject("Zulu")
        repository.createProject("Alpha")
        advanceUntilIdle()

        assertThat(repository.projects.first { it.size == 3 }.map { it.name })
            .containsExactly("Alpha", "Zulu", "Other")
            .inOrder()
    }

    @Test
    fun `start tracking writes one open unallocated entry`() = runTest {
        val entryDao = FakeTimeTrackingEntryDao()
        val repository = repository(
            applicationScope = backgroundScope,
            entryDao = entryDao,
        )

        val result = repository.startTracking()
        advanceUntilIdle()

        assertThat(result).isEqualTo(StartTrackingResult.Started)
        val active = repository.activeEntry.first { it != null }
        assertThat(active?.entry?.endAt).isNull()
        assertThat(active?.entry?.bucket).isEqualTo(TimeTrackingBucket.UNALLOCATED_MANUAL.name)
        assertThat(entryDao.getActive()?.projectId).isEqualTo(TimeTrackingOtherProjectId.toString())
    }

    @Test
    fun `stop tracking moves active time into pending buffer`() = runTest {
        val repository = repository(applicationScope = backgroundScope)
        advanceUntilIdle()
        repository.startTracking()

        val result = repository.stopTracking()
        advanceUntilIdle()

        assertThat(result).isEqualTo(StopTrackingResult.Stopped)
        assertThat(repository.activeEntry.first()).isNull()
        assertThat(repository.pendingEntries.first { it.isNotEmpty() }).hasSize(1)
        assertThat(repository.recentEntries.first()).isEmpty()
    }

    @Test
    fun `allocate tracked time files pending segments into the chosen project`() = runTest {
        val repository = repository(applicationScope = backgroundScope)
        advanceUntilIdle()
        val project = (repository.createProject("Client") as CreateProjectResult.Created).project
        repository.startTracking()
        repository.stopTracking()

        val result = repository.allocateTrackedTime(project.id)
        advanceUntilIdle()

        assertThat(result).isEqualTo(AllocateTrackedTimeResult.Allocated(entryCount = 1))
        assertThat(repository.pendingEntries.first()).isEmpty()
        assertThat(repository.recentEntries.first { it.isNotEmpty() }.first().project.name).isEqualTo("Client")
    }

    @Test
    fun `second start is rejected while an active entry exists`() = runTest {
        val repository = repository(applicationScope = backgroundScope)
        advanceUntilIdle()
        repository.startTracking()

        val result = repository.startTracking()
        advanceUntilIdle()

        assertThat(result).isEqualTo(StartTrackingResult.AlreadyTracking)
    }

    @Test
    fun `repository restores active entry from existing persistence state`() = runTest {
        val projectDao = FakeTimeTrackingProjectDao()
        val entryDao = FakeTimeTrackingEntryDao()
        projectDao.upsert(
            TimeTrackingProjectEntity(
                id = TimeTrackingOtherProjectId.toString(),
                name = "Other",
                isArchived = false,
            ),
        )
        entryDao.upsert(
            TimeTrackingEntryEntity(
                id = "00000000-0000-0000-0000-000000000201",
                projectId = TimeTrackingOtherProjectId.toString(),
                startAtEpochMillis = Instant.parse("2026-04-07T10:00:00Z").toEpochMilli(),
                endAtEpochMillis = null,
                bucket = TimeTrackingBucket.UNALLOCATED_MANUAL.name,
            ),
        )

        val repository = repository(
            applicationScope = backgroundScope,
            projectDao = projectDao,
            entryDao = entryDao,
        )

        advanceUntilIdle()
        assertThat(repository.currentActiveEntry()?.project?.name).isEqualTo("Other")
    }

    @Test
    fun `report interruption increments today counter and estimated focus loss`() = runTest {
        val interruptionDao = FakeTimeTrackingInterruptionDao()
        val repository = repository(
            applicationScope = backgroundScope,
            interruptionDao = interruptionDao,
        )
        advanceUntilIdle()
        repository.startTracking()

        val result = repository.reportInterruption(Instant.now())
        advanceUntilIdle()

        assertThat(result).isEqualTo(ReportInterruptionResult.Recorded)
        assertThat(interruptionDao.getAll()).hasSize(1)
    }

    private fun repository(
        applicationScope: CoroutineScope,
        projectDao: FakeTimeTrackingProjectDao = FakeTimeTrackingProjectDao(),
        entryDao: FakeTimeTrackingEntryDao = FakeTimeTrackingEntryDao(),
        interruptionDao: FakeTimeTrackingInterruptionDao = FakeTimeTrackingInterruptionDao(),
        settingsDataSource: NomadSettingsDataSource = fakeSettingsDataSource(),
    ): RoomTimeTrackingRepository = RoomTimeTrackingRepository(
        projectDao = projectDao,
        entryDao = entryDao,
        interruptionDao = interruptionDao,
        transactionRunner = ImmediateTimeTrackingTransactionRunner,
        settingsDataSource = settingsDataSource,
        applicationScope = applicationScope,
    )
}

private object ImmediateTimeTrackingTransactionRunner : TimeTrackingTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

private class FakeTimeTrackingProjectDao : TimeTrackingProjectDao {
    private val state = MutableStateFlow<List<TimeTrackingProjectEntity>>(emptyList())

    override fun observeAll(): Flow<List<TimeTrackingProjectEntity>> = state

    override suspend fun getById(id: String): TimeTrackingProjectEntity? =
        state.value.firstOrNull { it.id == id }

    override suspend fun findByName(name: String): TimeTrackingProjectEntity? =
        state.value.firstOrNull { it.name.equals(name, ignoreCase = true) }

    override suspend fun upsert(project: TimeTrackingProjectEntity) {
        val next = state.value.toMutableList()
        val index = next.indexOfFirst { it.id == project.id }
        if (index >= 0) {
            next[index] = project
        } else {
            next += project
        }
        state.value = next.sortedBy { it.name.lowercase() }
    }
}

private class FakeTimeTrackingEntryDao : TimeTrackingEntryDao {
    private val state = MutableStateFlow<List<TimeTrackingEntryEntity>>(emptyList())

    override fun observeAll(): Flow<List<TimeTrackingEntryEntity>> = state

    override fun observeCompleted(): Flow<List<TimeTrackingEntryEntity>> =
        state.map { items -> items.filter { it.endAtEpochMillis != null }.sortedByDescending { it.startAtEpochMillis } }

    override fun observeActive(): Flow<TimeTrackingEntryEntity?> =
        state.map { items -> items.firstOrNull { it.endAtEpochMillis == null } }

    override suspend fun getActive(): TimeTrackingEntryEntity? =
        state.value.firstOrNull { it.endAtEpochMillis == null }

    override suspend fun getAll(): List<TimeTrackingEntryEntity> = state.value

    override suspend fun getById(id: String): TimeTrackingEntryEntity? =
        state.value.firstOrNull { it.id == id }

    override suspend fun upsert(entry: TimeTrackingEntryEntity) {
        val next = state.value.toMutableList()
        val index = next.indexOfFirst { it.id == entry.id }
        if (index >= 0) {
            next[index] = entry
        } else {
            next += entry
        }
        state.value = next.sortedByDescending { it.startAtEpochMillis }
    }

    override suspend fun upsertAll(entries: List<TimeTrackingEntryEntity>) {
        entries.forEach { upsert(it) }
    }
}

private class FakeTimeTrackingInterruptionDao : TimeTrackingInterruptionDao {
    private val state = MutableStateFlow<List<TimeTrackingInterruptionEntity>>(emptyList())

    override fun observeAll(): Flow<List<TimeTrackingInterruptionEntity>> = state

    override suspend fun getAll(): List<TimeTrackingInterruptionEntity> = state.value

    override suspend fun upsert(interruption: TimeTrackingInterruptionEntity) {
        val next = state.value.toMutableList()
        val index = next.indexOfFirst { it.id == interruption.id }
        if (index >= 0) {
            next[index] = interruption
        } else {
            next += interruption
        }
        state.value = next.sortedByDescending { it.occurredAtEpochMillis }
    }
}

private fun fakeSettingsDataSource(): NomadSettingsDataSource =
    NomadSettingsDataSource(
        FakeAppSettingsDataStore(
            AppSettingsProto.getDefaultInstance()
                .toBuilder()
                .setProjectTimeTrackingEnabled(true)
                .setProjectTimeTrackingAutoStartMinutes(1)
                .setProjectTimeTrackingAutoStopMinutes(1)
                .build(),
        ),
    )

private class FakeAppSettingsDataStore(
    initialValue: AppSettingsProto = AppSettingsProto.getDefaultInstance(),
) : DataStore<AppSettingsProto> {
    private val state = MutableStateFlow(initialValue)

    override val data: Flow<AppSettingsProto> = state

    override suspend fun updateData(transform: suspend (t: AppSettingsProto) -> AppSettingsProto): AppSettingsProto {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
