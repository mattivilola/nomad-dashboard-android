package com.iloapps.nomaddashboard.core.model

import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.ZoneId
import kotlinx.serialization.Serializable

/** Device-local information deliberately kept separate from AppSettings and provider credentials. */
@Serializable
data class NomadCompanionData(
    val schemaVersion: Int = COMPANION_SCHEMA_VERSION,
    val preferences: CompanionPreferences = CompanionPreferences(),
    val essentials: List<OfflineEssential> = emptyList(),
    val workplaceVisits: List<WorkplaceVisit> = emptyList(),
    val planners: List<TimezonePlanner> = emptyList(),
    val conflictNotes: List<CompanionConflictNote> = emptyList(),
)

const val COMPANION_SCHEMA_VERSION = 1

@Serializable
data class CompanionPreferences(
    val homeTimeZoneId: String = ZoneId.systemDefault().id,
    val workplaceDiaryEnabled: Boolean = false,
    /** Names are never retained unless this separate opt-in is enabled. */
    val retainConnectionName: Boolean = false,
    /** Location-derived map links are never retained unless this separate opt-in is enabled. */
    val retainWorkplaceMapLink: Boolean = false,
    val quietNotificationsEnabled: Boolean = false,
    val quietHoursStartMinute: Int = 1_320,
    val quietHoursEndMinute: Int = 420,
    val notificationCooldownMinutes: Int = 120,
    val lastNotificationAtEpochMillis: Long = 0,
)

@Serializable
enum class OfflineEssentialKind { ADDRESS, CONTACT, NOTE, MAP_LINK }

@Serializable
data class OfflineEssential(
    val id: String,
    val kind: OfflineEssentialKind,
    val title: String,
    val content: String,
    /** A user-supplied Google Maps or geo URL; this is never inferred from a provider. */
    val mapUrl: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class WorkplaceVisit(
    val id: String,
    val placeLabel: String,
    val placeLabelConfirmed: Boolean = false,
    val mapsUrl: String? = null,
    val observedAtEpochMillis: Long,
    val internetState: String,
    val vpnActive: Boolean,
    val latencyMs: Double? = null,
    val powerPercent: Int? = null,
    val charging: Boolean = false,
    val connectionName: String? = null,
    val reliabilityNote: String? = null,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class TimezonePlanner(
    val id: String,
    val label: String,
    val homeZoneId: String,
    val clientZoneId: String,
    val homeStartMinute: Int = 540,
    val homeEndMinute: Int = 1_020,
    val clientStartMinute: Int = 540,
    val clientEndMinute: Int = 1_020,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class CompanionConflictNote(
    val itemId: String,
    val message: String,
    val recordedAtEpochMillis: Long,
)

@Serializable
data class NomadCompanionBackup(
    val format: String = "nomad-companion",
    val version: Int = COMPANION_SCHEMA_VERSION,
    val exportedAtEpochMillis: Long,
    val data: NomadCompanionData,
)

data class CompanionImportPreview(
    val accepted: Boolean,
    val version: Int? = null,
    val essentialsToAdd: Int = 0,
    val visitsToAdd: Int = 0,
    val plannersToAdd: Int = 0,
    val conflicts: Int = 0,
    val message: String,
)

data class WorkReadiness(
    val title: String,
    val detail: String,
    val isReady: Boolean?,
)

data class TimeOverlapSuggestion(
    val start: Instant,
    val end: Instant,
    val homeLabel: String,
    val clientLabel: String,
)

/** Pure IANA-zone calculation. Iterating instants makes DST transitions unambiguous. */
object TimezoneOverlapPlanner {
    fun nextOverlap(
        planner: TimezonePlanner,
        from: Instant = Instant.now(),
        minimumMinutes: Int = 30,
    ): TimeOverlapSuggestion? {
        val homeZone = runCatching { ZoneId.of(planner.homeZoneId) }.getOrNull() ?: return null
        val clientZone = runCatching { ZoneId.of(planner.clientZoneId) }.getOrNull() ?: return null
        var cursor = ((from.epochSecond + 899) / 900) * 900
        val end = cursor + 8L * 24 * 60 * 60
        var runStart: Long? = null
        while (cursor <= end) {
            val instant = Instant.ofEpochSecond(cursor)
            val suitable = inWorkingWindow(instant.atZone(homeZone).toLocalTime(), planner.homeStartMinute, planner.homeEndMinute) &&
                inWorkingWindow(instant.atZone(clientZone).toLocalTime(), planner.clientStartMinute, planner.clientEndMinute)
            if (suitable && runStart == null) runStart = cursor
            if ((!suitable || cursor == end) && runStart != null) {
                val runEnd = if (suitable) cursor + 900 else cursor
                if (runEnd - runStart >= minimumMinutes * 60L) {
                    val start = Instant.ofEpochSecond(runStart)
                    val finish = Instant.ofEpochSecond(runEnd)
                    return TimeOverlapSuggestion(
                        start = start,
                        end = finish,
                        homeLabel = ZonedDateTime.ofInstant(start, homeZone).toString(),
                        clientLabel = ZonedDateTime.ofInstant(start, clientZone).toString(),
                    )
                }
                runStart = null
            }
            cursor += 900
        }
        return null
    }

    private fun inWorkingWindow(time: LocalTime, startMinute: Int, endMinute: Int): Boolean {
        val minute = time.hour * 60 + time.minute
        return if (startMinute <= endMinute) minute in startMinute until endMinute
        else minute >= startMinute || minute < endMinute
    }
}
