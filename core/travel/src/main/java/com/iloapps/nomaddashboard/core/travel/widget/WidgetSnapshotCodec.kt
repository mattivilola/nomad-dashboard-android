package com.iloapps.nomaddashboard.core.travel.widget

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The JSON form of a [WidgetSnapshot], for the file the app shares with its widgets.
 *
 * The store that owns the file must write it atomically (write a temporary file in the same directory,
 * then rename it over the old one) so a widget never reads a half-written snapshot, keep it in storage
 * only this app can open, and exclude it from backup like the rest of the app's local data.
 */
object WidgetSnapshotCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(snapshot: WidgetSnapshot): String = json.encodeToString(WidgetSnapshot.serializer(), snapshot)

    /** The snapshot in [text], null when it cannot be read or was written by a different schema version. */
    fun decode(text: String): WidgetSnapshot? =
        try {
            val root = json.parseToJsonElement(text).jsonObject
            if (root.version() != WidgetSnapshot.SCHEMA_VERSION) {
                null
            } else {
                json.decodeFromJsonElement(WidgetSnapshot.serializer(), root)
            }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun JsonObject.version(): Int? = this["version"]?.jsonPrimitive?.intOrNull
}
