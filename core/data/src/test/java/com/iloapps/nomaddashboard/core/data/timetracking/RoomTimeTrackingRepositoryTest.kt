package com.iloapps.nomaddashboard.core.data.timetracking

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingEntryDao
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingProjectDao
import com.iloapps.nomaddashboard.core.database.entity.TimeTrackingEntryEntity
import com.iloapps.nomaddashboard.core.database.entity.TimeTrackingProjectEntity
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
    fun `create project persists and emits alphabetical project list`() = runTest {
        val projectDao = FakeTimeTrackingProjectDao()
        val repository = repository(
            applicationScope = backgroundScope,
            projectDao = projectDao,
        )

        repository.createProject("Zulu")
        repository.createProject("Alpha")
        advanceUntilIdle()

        assertThat(repository.projects.first { it.size == 2 }.map { it.name }).containsExactly("Alpha", "Zulu").inOrder()
    }

    @Test
    fun `start tracking writes one open entry for the selected project`() = runTest {
        val projectDao = FakeTimeTrackingProjectDao()
        val entryDao = FakeTimeTrackingEntryDao()
        val repository = repository(
            applicationScope = backgroundScope,
            projectDao = projectDao,
            entryDao = entryDao,
        )
        val project = (repository.createProject("Client") as CreateProjectResult.Created).project

        val result = repository.startTracking(project.id)
        advanceUntilIdle()

        assertThat(result).isEqualTo(StartTrackingResult.Started)
        val active = repository.activeEntry.first { it != null }
        assertThat(active?.project?.name).isEqualTo("Client")
        assertThat(active?.entry?.endAt).isNull()
        assertThat(entryDao.getActive()?.projectId).isEqualTo(project.id.toString())
    }

    @Test
    fun `stop tracking closes the active entry`() = runTest {
        val projectDao = FakeTimeTrackingProjectDao()
        val entryDao = FakeTimeTrackingEntryDao()
        val repository = repository(
            applicationScope = backgroundScope,
            projectDao = projectDao,
            entryDao = entryDao,
        )
        val project = (repository.createProject("Client") as CreateProjectResult.Created).project
        repository.startTracking(project.id)

        val result = repository.stopTracking()
        advanceUntilIdle()

        assertThat(result).isEqualTo(StopTrackingResult.Stopped)
        assertThat(repository.activeEntry.first()).isNull()
        val recentEntries = repository.recentEntries.first { it.isNotEmpty() }
        assertThat(recentEntries).hasSize(1)
        assertThat(recentEntries.first().entry.endAt).isNotNull()
    }

    @Test
    fun `second start is rejected while an active entry exists`() = runTest {
        val repository = repository(applicationScope = backgroundScope)
        val first = (repository.createProject("Client A") as CreateProjectResult.Created).project
        val second = (repository.createProject("Client B") as CreateProjectResult.Created).project
        repository.startTracking(first.id)

        val result = repository.startTracking(second.id)
        advanceUntilIdle()

        assertThat(result).isEqualTo(StartTrackingResult.AlreadyTracking)
        assertThat(repository.activeEntry.first { it != null }?.project?.name).isEqualTo("Client A")
    }

    @Test
    fun `repository restores active entry from existing persistence state`() = runTest {
        val projectDao = FakeTimeTrackingProjectDao()
        val entryDao = FakeTimeTrackingEntryDao()
        val project = TimeTrackingProjectEntity(
            id = "00000000-0000-0000-0000-000000000101",
            name = "Recovered",
            isArchived = false,
        )
        projectDao.upsert(project)
        entryDao.upsert(
            TimeTrackingEntryEntity(
                id = "00000000-0000-0000-0000-000000000201",
                projectId = project.id,
                startAtEpochMillis = Instant.parse("2026-04-07T10:00:00Z").toEpochMilli(),
                endAtEpochMillis = null,
                bucket = "UNALLOCATED",
            ),
        )

        val repository = repository(
            applicationScope = backgroundScope,
            projectDao = projectDao,
            entryDao = entryDao,
        )

        advanceUntilIdle()
        assertThat(repository.currentActiveEntry()?.project?.name).isEqualTo("Recovered")
    }

    private fun repository(
        applicationScope: CoroutineScope,
        projectDao: FakeTimeTrackingProjectDao = FakeTimeTrackingProjectDao(),
        entryDao: FakeTimeTrackingEntryDao = FakeTimeTrackingEntryDao(),
    ): RoomTimeTrackingRepository = RoomTimeTrackingRepository(
        projectDao = projectDao,
        entryDao = entryDao,
        transactionRunner = ImmediateTimeTrackingTransactionRunner,
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
}
