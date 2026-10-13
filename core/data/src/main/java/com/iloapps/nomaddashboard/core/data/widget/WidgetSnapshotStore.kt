package com.iloapps.nomaddashboard.core.data.widget

import android.content.Context
import com.iloapps.nomaddashboard.core.common.IoDispatcher
import com.iloapps.nomaddashboard.core.travel.widget.WidgetSnapshot
import com.iloapps.nomaddashboard.core.travel.widget.WidgetSnapshotCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Reads and writes the [WidgetSnapshot] the app shares with its home screen widgets and the Quick
 * Settings tile.
 *
 * The file lives in the app's private no-backup storage, so only this app can open it and it never
 * reaches a cloud backup or a device transfer. Writes are atomic (a temporary file in the same
 * directory, renamed over the old one) so a widget never reads a half-written snapshot.
 *
 * [WidgetSnapshot] holds no IP address, no coordinates and no citizenship codes; see
 * [WidgetSnapshotCodec].
 */
@Singleton
class WidgetSnapshotStore internal constructor(
    private val directory: File,
    private val ioDispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(File(context.noBackupFilesDir, DIRECTORY_NAME), ioDispatcher)

    private val file: File get() = File(directory, FILE_NAME)
    private val writeMutex = Mutex()

    /** The saved snapshot, null when there is none, it cannot be read, or another schema version wrote it. */
    suspend fun load(): WidgetSnapshot? = withContext(ioDispatcher) {
        val text = try {
            file.readText()
        } catch (_: IOException) {
            return@withContext null
        }
        WidgetSnapshotCodec.decode(text)
    }

    /** @throws IOException when the snapshot cannot be written; the previous file is left as it was. */
    suspend fun save(snapshot: WidgetSnapshot) {
        val text = WidgetSnapshotCodec.encode(snapshot)
        writeMutex.withLock {
            withContext(ioDispatcher) {
                if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
                    throw IOException("Cannot create ${directory.name}")
                }
                val temporary = File(directory, "$FILE_NAME.tmp")
                try {
                    temporary.writeText(text)
                    Files.move(
                        temporary.toPath(),
                        file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } finally {
                    temporary.delete()
                }
            }
        }
    }

    companion object {
        const val FILE_NAME = "widget-snapshot.json"
        const val DIRECTORY_NAME = "widgets"
    }
}
