package com.iloapps.nomaddashboard.core.data.visited

import android.content.Context
import android.net.Uri
import com.iloapps.nomaddashboard.core.common.IoDispatcher
import com.iloapps.nomaddashboard.core.database.dao.TravelStayReminderDao
import com.iloapps.nomaddashboard.core.database.dao.VisitedCountryDayDao
import com.iloapps.nomaddashboard.core.database.dao.VisitedPlaceDao
import com.iloapps.nomaddashboard.core.database.dao.VisitedPlaceEventDao
import com.iloapps.nomaddashboard.core.database.entity.toEntity
import com.iloapps.nomaddashboard.core.database.entity.toExternalModel
import com.iloapps.nomaddashboard.core.model.TravelHistoryBackup
import com.iloapps.nomaddashboard.core.model.TravelHistoryImportPreview
import com.iloapps.nomaddashboard.core.model.TravelStayReminder
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedCountryDayOriginalObservation
import com.iloapps.nomaddashboard.core.model.VisitedCountryDayProvenance
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedPlaceEvent
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

private const val MAX_BACKUP_BYTES = 5L * 1024L * 1024L
private const val MAX_BACKUP_RECORDS_PER_COLLECTION = 20_000
private const val MAX_COUNTRY_LENGTH = 128
private const val MAX_CITY_LENGTH = 256
private const val MAX_REGION_LENGTH = 256
private const val MAX_REMINDER_LABEL_LENGTH = 160
private const val MAX_REMINDER_NOTE_LENGTH = 2_000

/** Local-only export/import and reminder operations for the travel ledger. */
@Singleton
class TravelHistoryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val placeDao: VisitedPlaceDao,
    private val eventDao: VisitedPlaceEventDao,
    private val dayDao: VisitedCountryDayDao,
    private val reminderDao: TravelStayReminderDao,
    private val transactionRunner: DatabaseTransactionRunner,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    val reminders: Flow<List<TravelStayReminder>> = reminderDao.observeAll().map { items ->
        items.map { it.toExternalModel() }
    }

    suspend fun correctCountryDay(date: LocalDate, country: String, countryCode: String?) =
        withContext(ioDispatcher) {
            require(country.trim().isNotBlank() && country.trim().length <= MAX_COUNTRY_LENGTH) { "A valid country is required." }
            require(countryCode == null || countryCode.trim().length in 2..3) { "Country code is invalid." }
            val current = dayDao.loadAll().map { it.toExternalModel() }
            val old = current.firstOrNull { it.date == date }
            val original = old?.originalObservation ?: old?.let {
                VisitedCountryDayOriginalObservation(it.country, it.countryCode, it.source, it.isInferred, null)
            }
            val replacement = VisitedCountryDay(
                date = date,
                country = country.trim(),
                countryCode = countryCode?.trim()?.uppercase()?.takeIf(String::isNotBlank),
                source = old?.source ?: VisitedPlaceSource.DEVICE_LOCATION,
                isInferred = false,
                provenance = VisitedCountryDayProvenance.MANUAL,
                originalObservation = original,
            )
            transactionRunner.run {
                dayDao.replaceAll((current.filterNot { it.date == date } + replacement).map { it.toEntity() })
            }
        }

    suspend fun saveReminder(
        label: String,
        country: String?,
        targetDate: LocalDate,
        note: String?,
    ) = withContext(ioDispatcher) {
        val cleanLabel = label.trim()
        require(cleanLabel.isNotBlank()) { "A reminder label is required." }
        reminderDao.upsert(
            TravelStayReminder(
                id = UUID.randomUUID().toString(),
                label = cleanLabel,
                country = country?.trim()?.takeIf(String::isNotBlank),
                targetDate = targetDate,
                createdAt = Instant.now(),
                note = note?.trim()?.takeIf(String::isNotBlank),
            ).toEntity(),
        )
    }

    suspend fun deleteReminder(id: String) = withContext(ioDispatcher) { reminderDao.deleteById(id) }

    suspend fun restoreOriginalCountryDay(date: LocalDate) = withContext(ioDispatcher) {
        transactionRunner.run {
            val current = dayDao.loadAll().map { it.toExternalModel() }
            val manual = current.firstOrNull { it.date == date } ?: return@run
            val original = manual.originalObservation ?: return@run
            val restored = VisitedCountryDay(
                date = date,
                country = original.country,
                countryCode = original.countryCode,
                source = original.source,
                isInferred = original.isInferred,
                provenance = if (original.isInferred) VisitedCountryDayProvenance.INFERRED else VisitedCountryDayProvenance.OBSERVED,
            )
            dayDao.replaceAll((current.filterNot { it.date == date } + restored).sortedBy(VisitedCountryDay::date).map { it.toEntity() })
        }
    }

    suspend fun exportText(year: Int?): String = withContext(ioDispatcher) {
        val days = transactionRunner.run {
            dayDao.loadAll().map { it.toExternalModel() }.filter { year == null || it.date.year == year }
        }
        val heading = year?.let { "Nomad travel record for $it" } ?: "Nomad travel record"
        buildString {
            appendLine(heading)
            appendLine("Local travel history. Provenance: observed, inferred, or manual correction.")
            days.forEach { day ->
                append(day.date).append(" | ").append(day.country)
                    .append(" | ").append(day.provenance.name.lowercase())
                    .appendLine()
            }
        }.trimEnd()
    }

    suspend fun exportCsv(year: Int?): String = withContext(ioDispatcher) {
        val days = transactionRunner.run {
            dayDao.loadAll().map { it.toExternalModel() }.filter { year == null || it.date.year == year }
        }
        buildString {
            appendLine("format_version,day,country,country_code,origin,capture_source,is_inferred,is_manual")
            days.forEach { day ->
                appendLine(listOf(
                    "1", day.date.toString(), day.country, day.countryCode.orEmpty(), day.provenance.name.lowercase(),
                    if (day.provenance == VisitedCountryDayProvenance.MANUAL) "" else day.source.name.lowercase(),
                    (day.provenance == VisitedCountryDayProvenance.INFERRED).toString(),
                    (day.provenance == VisitedCountryDayProvenance.MANUAL).toString(),
                ).joinToString(",") { csvValue(it) })
            }
        }.trimEnd()
    }

    suspend fun exportBackup(): String = withContext(ioDispatcher) {
        val backup = transactionRunner.run { readBackupSnapshot() }
        json.encodeToString(JsonObject.serializer(), backup.toJson())
    }

    /** Writes a portable JSON backup to a user-selected document URI on the IO dispatcher. */
    suspend fun saveBackup(uri: Uri) = withContext(ioDispatcher) {
        context.contentResolver.openOutputStream(uri, "w")
            ?.bufferedWriter()
            ?.use { it.write(exportBackup()) }
            ?: error("The selected backup destination could not be opened.")
    }

    suspend fun previewImport(uri: Uri): TravelHistoryImportPreview = withContext(ioDispatcher) {
        preview(decode(readUri(uri)))
    }

    suspend fun importFrom(uri: Uri): TravelHistoryImportPreview = withContext(ioDispatcher) {
        val backup = decode(readUri(uri))
        val preview = preview(backup)
        if (preview.conflictCount > 0) return@withContext preview
        transactionRunner.run {
            val transactionPreview = preview(backup)
            if (transactionPreview.conflictCount > 0) return@run transactionPreview
            val placeIds = placeDao.loadAll().map { it.id }.toSet()
            backup.places.filter { it.id !in placeIds }.forEach { placeDao.upsert(it.toEntity()) }
            val eventIds = eventDao.loadAll().map { it.id }.toSet()
            backup.placeEvents.filter { it.id !in eventIds }.forEach { eventDao.upsert(it.toEntity()) }
            val dayDates = dayDao.loadAll().map { it.dateIso }.toSet()
            backup.countryDays.filter { it.date.toString() !in dayDates }.let { additions ->
                if (additions.isNotEmpty()) dayDao.insertAll(additions.map { it.toEntity() })
            }
            backup.stayReminders.forEach { reminder ->
                if (reminderDao.getById(reminder.id) == null) reminderDao.upsert(reminder.toEntity())
            }
            transactionPreview
        }
    }

    private suspend fun preview(backup: TravelHistoryBackup): TravelHistoryImportPreview {
        val existingPlaces = placeDao.loadAll().map { it.toExternalModel() }.associateBy { it.id }
        val existingEvents = eventDao.loadAll().map { it.toExternalModel() }.associateBy { it.id }
        val existingDays = dayDao.loadAll().map { it.toExternalModel() }.associateBy { it.date }
        val existingReminders = reminderDao.loadAll().map { it.toExternalModel() }.associateBy { it.id }
        var duplicates = 0; var conflicts = 0
        fun check(existing: Any?, incoming: Any?) { if (existing != null) if (existing == incoming) duplicates++ else conflicts++ }
        backup.places.forEach { check(existingPlaces[it.id], it) }
        backup.placeEvents.forEach { check(existingEvents[it.id], it) }
        backup.countryDays.forEach { check(existingDays[it.date], it) }
        backup.stayReminders.forEach { check(existingReminders[it.id], it) }
        return TravelHistoryImportPreview(
            schemaVersion = backup.schemaVersion,
            placesToAdd = backup.places.count { it.id !in existingPlaces },
            placeEventsToAdd = backup.placeEvents.count { it.id !in existingEvents },
            countryDaysToAdd = backup.countryDays.count { it.date !in existingDays },
            remindersToAdd = backup.stayReminders.count { it.id !in existingReminders },
            duplicateCount = duplicates,
            conflictCount = conflicts,
            warnings = if (conflicts > 0) listOf("Conflicts were found. Import will not overwrite local records.") else emptyList(),
        )
    }

    private suspend fun readBackupSnapshot(): TravelHistoryBackup = TravelHistoryBackup(
        exportedAt = Instant.now(),
        places = placeDao.loadAll().map { it.toExternalModel() },
        placeEvents = eventDao.loadAll().map { it.toExternalModel() },
        countryDays = dayDao.loadAll().map { it.toExternalModel() },
        stayReminders = reminderDao.loadAll().map { it.toExternalModel() },
    )

    private fun readUri(uri: Uri): String = context.contentResolver.openInputStream(uri)
        ?.use { input ->
            val declaredSize = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            require(declaredSize == null || declaredSize <= MAX_BACKUP_BYTES) { "Backup exceeds the 5 MB import limit." }
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                require(output.size().toLong() <= MAX_BACKUP_BYTES) { "Backup exceeds the 5 MB import limit." }
            }
            output.toString(Charsets.UTF_8.name())
        }
        ?: error("The selected backup could not be opened.")

    private fun decode(raw: String): TravelHistoryBackup {
        val root = json.parseToJsonElement(raw).jsonObject
        val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: error("Backup schema version is missing.")
        require(version == 1) { "This backup schema is not supported." }
        return TravelHistoryBackup(
            schemaVersion = version,
            exportedAt = Instant.parse(root["exportedAt"]!!.jsonPrimitive.content),
            places = root.array("places").map(::placeFromJson),
            placeEvents = root.array("placeEvents").map(::eventFromJson),
            countryDays = root.array("countryDays").map(::dayFromJson),
            stayReminders = root.array("stayReminders").map(::reminderFromJson),
        ).also(::validateTravelHistoryBackup)
    }
}

private fun TravelHistoryBackup.toJson(): JsonObject = buildJsonObject {
    put("schemaVersion", schemaVersion); put("exportedAt", exportedAt.toString())
    put("places", places.toJsonArray { it.toJson() }); put("placeEvents", placeEvents.toJsonArray { it.toJson() })
    put("countryDays", countryDays.toJsonArray { it.toJson() }); put("stayReminders", stayReminders.toJsonArray { it.toJson() })
}
private fun VisitedPlace.toJson() = buildJsonObject { put("city", city); put("region", region); put("country", country); put("countryCode", countryCode); put("latitude", latitude); put("longitude", longitude); put("sources", sources.toJsonArray { JsonPrimitive(it.name) }); put("firstVisitedAt", firstVisitedAt.toString()); put("lastVisitedAt", lastVisitedAt.toString()) }
private fun VisitedPlaceEvent.toJson() = buildJsonObject { put("id", id); put("city", city); put("region", region); put("country", country); put("countryCode", countryCode); put("latitude", latitude); put("longitude", longitude); put("source", source.name); put("firstObservedAt", firstObservedAt.toString()); put("lastObservedAt", lastObservedAt.toString()); put("observedDay", observedDay.toString()) }
private fun VisitedCountryDay.toJson() = buildJsonObject { put("date", date.toString()); put("country", country); put("countryCode", countryCode); put("source", source.name); put("isInferred", isInferred); put("provenance", provenance.name); originalObservation?.let { put("original", buildJsonObject { put("country", it.country); put("countryCode", it.countryCode); put("source", it.source.name); put("isInferred", it.isInferred); put("observedAt", it.observedAt?.toString()) }) } }
private fun TravelStayReminder.toJson() = buildJsonObject { put("id", id); put("label", label); put("country", country); put("targetDate", targetDate.toString()); put("createdAt", createdAt.toString()); put("note", note) }
private fun <T> List<T>.toJsonArray(transform: (T) -> JsonElement) = buildJsonArray { forEach { add(transform(it)) } }
private fun JsonObject.array(key: String) = this[key]?.jsonArray ?: JsonArray(emptyList())
private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.content ?: error("Backup field $key is missing.")
private fun JsonObject.optionalString(key: String) = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.optionalDouble(key: String) = this[key]?.jsonPrimitive?.doubleOrNull
private fun placeFromJson(value: kotlinx.serialization.json.JsonElement): VisitedPlace { val o=value.jsonObject; return VisitedPlace(o.optionalString("city"),o.optionalString("region"),o.string("country"),o.optionalString("countryCode"),o.optionalDouble("latitude"),o.optionalDouble("longitude"),o.array("sources").map { VisitedPlaceSource.valueOf(it.jsonPrimitive.content) },Instant.parse(o.string("firstVisitedAt")),Instant.parse(o.string("lastVisitedAt"))) }
private fun eventFromJson(value: kotlinx.serialization.json.JsonElement): VisitedPlaceEvent { val o=value.jsonObject; return VisitedPlaceEvent(o.string("id"),o.optionalString("city"),o.optionalString("region"),o.string("country"),o.optionalString("countryCode"),o.optionalDouble("latitude"),o.optionalDouble("longitude"),VisitedPlaceSource.valueOf(o.string("source")),Instant.parse(o.string("firstObservedAt")),Instant.parse(o.string("lastObservedAt")),LocalDate.parse(o.string("observedDay"))) }
private fun dayFromJson(value: kotlinx.serialization.json.JsonElement): VisitedCountryDay { val o=value.jsonObject; val original=o["original"]?.jsonObject?.let { VisitedCountryDayOriginalObservation(it.string("country"),it.optionalString("countryCode"),VisitedPlaceSource.valueOf(it.string("source")),it["isInferred"]?.jsonPrimitive?.booleanOrNull ?: false,it.optionalString("observedAt")?.let(Instant::parse)) }; val inferred=o["isInferred"]?.jsonPrimitive?.booleanOrNull ?: false; return VisitedCountryDay(LocalDate.parse(o.string("date")),o.string("country"),o.optionalString("countryCode"),VisitedPlaceSource.valueOf(o.string("source")),inferred,o.optionalString("provenance")?.let(VisitedCountryDayProvenance::valueOf) ?: if(inferred) VisitedCountryDayProvenance.INFERRED else VisitedCountryDayProvenance.OBSERVED,original) }
private fun reminderFromJson(value: kotlinx.serialization.json.JsonElement): TravelStayReminder { val o=value.jsonObject; return TravelStayReminder(o.string("id"),o.string("label"),o.optionalString("country"),LocalDate.parse(o.string("targetDate")),Instant.parse(o.string("createdAt")),o.optionalString("note")) }
private fun csvValue(value: String) = "\"${value.replace("\"", "\"\"")}\""

internal fun validateTravelHistoryBackup(backup: TravelHistoryBackup) {
    fun <T> List<T>.requireBounded(label: String) {
        require(size <= MAX_BACKUP_RECORDS_PER_COLLECTION) { "$label exceeds the import record limit." }
    }
    backup.places.requireBounded("Places")
    backup.placeEvents.requireBounded("Place events")
    backup.countryDays.requireBounded("Country days")
    backup.stayReminders.requireBounded("Stay reminders")
    require(backup.places.map(VisitedPlace::id).distinct().size == backup.places.size) { "Backup contains duplicate place IDs." }
    require(backup.placeEvents.map(VisitedPlaceEvent::id).distinct().size == backup.placeEvents.size) { "Backup contains duplicate event IDs." }
    require(backup.countryDays.map(VisitedCountryDay::date).distinct().size == backup.countryDays.size) { "Backup contains duplicate country-day dates." }
    require(backup.stayReminders.map(TravelStayReminder::id).distinct().size == backup.stayReminders.size) { "Backup contains duplicate reminder IDs." }
    backup.places.forEach { place ->
        validateLocationText(place.country, place.countryCode, place.city, place.region)
        validateCoordinates(place.latitude, place.longitude)
        require(place.firstVisitedAt <= place.lastVisitedAt) { "Place observation chronology is invalid." }
    }
    backup.placeEvents.forEach { event ->
        validateLocationText(event.country, event.countryCode, event.city, event.region)
        validateCoordinates(event.latitude, event.longitude)
        require(event.firstObservedAt <= event.lastObservedAt) { "Place event chronology is invalid." }
    }
    backup.countryDays.forEach { day ->
        validateLocationText(day.country, day.countryCode, city = null, region = null)
        day.originalObservation?.let { original ->
            validateLocationText(original.country, original.countryCode, city = null, region = null)
        }
    }
    backup.stayReminders.forEach { reminder ->
        require(reminder.id.isNotBlank() && reminder.id.length <= 128) { "Reminder ID is invalid." }
        require(reminder.label.isNotBlank() && reminder.label.length <= MAX_REMINDER_LABEL_LENGTH) { "Reminder label is invalid." }
        require((reminder.country?.length ?: 0) <= MAX_COUNTRY_LENGTH) { "Reminder country is too long." }
        require((reminder.note?.length ?: 0) <= MAX_REMINDER_NOTE_LENGTH) { "Reminder note is too long." }
    }
}

private fun validateLocationText(country: String, countryCode: String?, city: String?, region: String?) {
    require(country.isNotBlank() && country.length <= MAX_COUNTRY_LENGTH) { "Country is invalid." }
    require(countryCode == null || countryCode.length in 2..3) { "Country code is invalid." }
    require(city == null || city.length <= MAX_CITY_LENGTH) { "City is too long." }
    require(region == null || region.length <= MAX_REGION_LENGTH) { "Region is too long." }
}

private fun validateCoordinates(latitude: Double?, longitude: Double?) {
    require((latitude == null) == (longitude == null)) { "Coordinates must include latitude and longitude together." }
    if (latitude != null && longitude != null) {
        require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            "Coordinates are outside valid bounds."
        }
    }
}
