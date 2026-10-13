package com.iloapps.nomaddashboard.core.data.widget

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.travel.health.HealthLevel
import com.iloapps.nomaddashboard.core.travel.widget.WidgetSnapshot
import com.iloapps.nomaddashboard.core.travel.widget.WidgetSnapshotCodec
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetSnapshotStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val savedAt = Instant.parse("2026-10-12T09:30:00Z")

    private fun snapshot(title: String = "Ready", at: Instant = savedAt) = WidgetSnapshot.of(
        verdict = WidgetSnapshot.Verdict(title, HealthLevel.READY),
        savedAt = at,
        placeName = "Rabat, Morocco",
        emergency = WidgetSnapshot.Emergency("Morocco", "19"),
    )

    private fun store(directory: File = File(folder.root, "widgets")) =
        WidgetSnapshotStore(directory, UnconfinedTestDispatcher())

    @Test
    fun `nothing is saved at first`() = runTest {
        assertThat(store().load()).isNull()
    }

    @Test
    fun `a saved snapshot is read back`() = runTest {
        val store = store()

        store.save(snapshot())

        assertThat(store.load()).isEqualTo(snapshot())
    }

    @Test
    fun `saving replaces the previous snapshot and leaves no temporary file`() = runTest {
        val directory = File(folder.root, "widgets")
        val store = store(directory)

        store.save(snapshot("Ready"))
        store.save(snapshot("Caution", at = savedAt.plusSeconds(60)))

        assertThat(store.load()?.verdict?.title).isEqualTo("Caution")
        assertThat(directory.list()?.toList()).containsExactly(WidgetSnapshotStore.FILE_NAME)
    }

    @Test
    fun `the file is the codec's JSON`() = runTest {
        val directory = File(folder.root, "widgets")
        store(directory).save(snapshot())

        val text = File(directory, WidgetSnapshotStore.FILE_NAME).readText()

        assertThat(WidgetSnapshotCodec.decode(text)).isEqualTo(snapshot())
    }

    @Test
    fun `a corrupt file reads as no snapshot`() = runTest {
        val directory = File(folder.root, "widgets").apply { mkdirs() }
        File(directory, WidgetSnapshotStore.FILE_NAME).writeText("{ not json")

        assertThat(store(directory).load()).isNull()
    }

    @Test
    fun `a file written by another schema version reads as no snapshot`() = runTest {
        val directory = File(folder.root, "widgets")
        val store = store(directory)
        store.save(snapshot())
        val file = File(directory, WidgetSnapshotStore.FILE_NAME)
        file.writeText(file.readText().replace("\"version\":1", "\"version\":2"))

        assertThat(store.load()).isNull()
    }

    @Test
    fun `a failed save keeps the previous snapshot`() = runTest {
        val directory = File(folder.root, "widgets")
        val store = store(directory)
        store.save(snapshot("Ready"))
        // A directory where the temporary file belongs makes the write fail.
        File(directory, "${WidgetSnapshotStore.FILE_NAME}.tmp").mkdirs()

        val failure = runCatching { store.save(snapshot("Caution")) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IOException::class.java)

        assertThat(store.load()?.verdict?.title).isEqualTo("Ready")
    }
}
