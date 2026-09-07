package com.iloapps.nomaddashboard.core.data.companion

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.NomadCompanionData
import com.iloapps.nomaddashboard.core.model.OfflineEssential
import com.iloapps.nomaddashboard.core.model.OfflineEssentialKind
import kotlinx.serialization.json.Json
import org.junit.Test

class CompanionBackupCodecTest {
    private val codec = CompanionBackupCodec(Json { explicitNulls = false })

    @Test
    fun `preview and merge retain stable id and report conflict`() {
        val local = NomadCompanionData(essentials = listOf(essential("a", "Local", 10)))
        val imported = NomadCompanionData(essentials = listOf(essential("a", "Imported", 20), essential("b", "New", 20)))
        val serialized = codec.export(imported, 30)

        val preview = codec.preview(local, serialized)
        val (merged, result) = codec.merge(local, serialized, 40)

        assertThat(preview.accepted).isTrue()
        assertThat(preview.essentialsToAdd).isEqualTo(1)
        assertThat(preview.conflicts).isEqualTo(1)
        assertThat(result.accepted).isTrue()
        assertThat(merged.essentials.map { it.title }).containsExactly("Local", "New")
        assertThat(merged.conflictNotes).hasSize(1)
    }

    private fun essential(id: String, title: String, updatedAt: Long) = OfflineEssential(
        id = id,
        kind = OfflineEssentialKind.NOTE,
        title = title,
        content = title,
        createdAtEpochMillis = updatedAt,
        updatedAtEpochMillis = updatedAt,
    )
}
