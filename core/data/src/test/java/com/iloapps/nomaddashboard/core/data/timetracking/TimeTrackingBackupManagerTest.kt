package com.iloapps.nomaddashboard.core.data.timetracking

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class TimeTrackingBackupManagerTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val projectId = "11111111-1111-1111-1111-111111111111"
    private val entryId = "22222222-2222-2222-2222-222222222222"
    private val interruptionId = "33333333-3333-3333-3333-333333333333"

    @Test
    fun `decoder rejects active entries and duplicate identities`() {
        val active = backup(entries = listOf(entry(end = null)))
        val duplicate = backup(projects = listOf(project(), project()))
        val invalidRange = backup(entries = listOf(entry(start = 300L, end = 200L)))

        assertThat(runCatching { decode(active) }.exceptionOrNull()?.message).contains("Active entries")
        assertThat(runCatching { decode(duplicate) }.exceptionOrNull()?.message).contains("duplicate IDs")
        assertThat(runCatching { decode(invalidRange) }.exceptionOrNull()?.message).contains("time range")
    }

    @Test
    fun `reference validation rejects unavailable project interrupted unknown entry and active replacement`() {
        val missingProject = backup(entries = listOf(entry(project = "44444444-4444-4444-4444-444444444444")))
        val unknownInterruption = backup(interruptions = listOf(interruption(entry = "55555555-5555-5555-5555-555555555555")))
        val activeReplacement = backup(entries = listOf(entry()))

        assertThat(runCatching {
            validateTimeTrackingBackupReferences(missingProject, emptySet(), emptySet(), null)
        }.exceptionOrNull()?.message).contains("project")
        assertThat(runCatching {
            validateTimeTrackingBackupReferences(unknownInterruption, setOf(projectId), emptySet(), null)
        }.exceptionOrNull()?.message).contains("completed entries")
        assertThat(runCatching {
            validateTimeTrackingBackupReferences(activeReplacement, setOf(projectId), setOf(entryId), entryId)
        }.exceptionOrNull()?.message).contains("active capture")
    }

    @Test
    fun `merge counts keep exact duplicate and surface changed local record as conflict`() {
        val incoming = backup(
            projects = listOf(project()),
            entries = listOf(entry()),
            interruptions = listOf(interruption(entry = entryId)),
        )

        val counts = calculateTimeTrackingImportCounts(
            backup = incoming,
            existingProjects = mapOf(projectId to project()),
            existingEntries = mapOf(entryId to entry(start = 99L)),
            existingInterruptions = emptyMap(),
        )

        assertThat(counts.projectsToAdd).isEqualTo(0)
        assertThat(counts.entriesToAdd).isEqualTo(0)
        assertThat(counts.interruptionsToAdd).isEqualTo(1)
        assertThat(counts.duplicateCount).isEqualTo(1)
        assertThat(counts.conflictCount).isEqualTo(1)
    }

    private fun decode(backup: TimeTrackingBackup) =
        decodeTimeTrackingBackup(json, json.encodeToString(TimeTrackingBackup.serializer(), backup))

    private fun backup(
        projects: List<ProjectBackup> = listOf(project()),
        entries: List<EntryBackup> = emptyList(),
        interruptions: List<InterruptionBackup> = emptyList(),
    ) = TimeTrackingBackup(
        exportedAtEpochMillis = 1_700_000_000_000,
        projects = projects,
        completedEntries = entries,
        interruptions = interruptions,
    )

    private fun project(name: String = "Client work") = ProjectBackup(projectId, name, archived = false)

    private fun entry(
        project: String = projectId,
        start: Long = 100L,
        end: Long? = 200L,
    ) = EntryBackup(entryId, project, start, end, "UNALLOCATED_MANUAL")

    private fun interruption(entry: String?) = InterruptionBackup(interruptionId, entry, 150L)
}
