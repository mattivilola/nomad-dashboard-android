package com.iloapps.nomaddashboard.core.data.visited

import com.iloapps.nomaddashboard.core.database.dao.VisitedCountryDayDao
import com.iloapps.nomaddashboard.core.database.dao.VisitedPlaceEventDao
import com.iloapps.nomaddashboard.core.database.dao.VisitedPlaceDao
import com.iloapps.nomaddashboard.core.database.entity.toEntity
import com.iloapps.nomaddashboard.core.database.entity.toExternalModel
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedPlaceEvent
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import com.iloapps.nomaddashboard.core.model.visitedPlaceStorageKey
import java.text.Normalizer
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomVisitedHistoryStore @Inject constructor(
    private val visitedPlaceDao: VisitedPlaceDao,
    private val visitedPlaceEventDao: VisitedPlaceEventDao,
    private val visitedCountryDayDao: VisitedCountryDayDao,
    private val transactionRunner: DatabaseTransactionRunner,
) : VisitedHistoryStore {
    override val visitedPlaces: Flow<List<VisitedPlace>> =
        visitedPlaceDao.observeAll().map { items -> items.map { it.toExternalModel() } }

    override val visitedCountryDays: Flow<List<VisitedCountryDay>> =
        visitedCountryDayDao.observeAll().map { items -> items.map { it.toExternalModel() } }

    override val visitedPlaceEvents: Flow<List<VisitedPlaceEvent>> =
        visitedPlaceEventDao.observeAll().map { items -> items.map { it.toExternalModel() } }

    override suspend fun recordObservation(observation: VisitedObservation) {
        val normalized = observation.normalized() ?: return
        transactionRunner.run {
            recordVisitedPlaceEvent(normalized)
            recordVisitedPlace(normalized)
            recordVisitedCountryDay(normalized)
        }
    }

    override suspend fun clearHistory() {
        transactionRunner.run {
            visitedPlaceDao.clearAll()
            visitedPlaceEventDao.clearAll()
            visitedCountryDayDao.clearAll()
        }
    }

    private suspend fun recordVisitedPlaceEvent(observation: VisitedObservation) {
        val observedDay = observation.observedAt.atZone(ZoneId.systemDefault()).toLocalDate()
        val id = visitedPlaceEventStorageKey(observation, observedDay)
        val existing = visitedPlaceEventDao.getById(id)?.toExternalModel()
        val updated = if (existing == null) {
            VisitedPlaceEvent(
                id = id,
                city = observation.city,
                region = observation.region,
                country = observation.country,
                countryCode = observation.countryCode,
                latitude = observation.latitude,
                longitude = observation.longitude,
                source = observation.source,
                firstObservedAt = observation.observedAt,
                lastObservedAt = observation.observedAt,
                observedDay = observedDay,
            )
        } else {
            val preferIncomingCoordinates = observation.source == VisitedPlaceSource.DEVICE_LOCATION ||
                existing.latitude == null ||
                existing.longitude == null
            VisitedPlaceEvent(
                id = existing.id,
                city = observation.city ?: existing.city,
                region = observation.region ?: existing.region,
                country = observation.country.ifBlank { existing.country },
                countryCode = observation.countryCode ?: existing.countryCode,
                latitude = if (preferIncomingCoordinates) observation.latitude ?: existing.latitude else existing.latitude ?: observation.latitude,
                longitude = if (preferIncomingCoordinates) observation.longitude ?: existing.longitude else existing.longitude ?: observation.longitude,
                source = if (observation.source == VisitedPlaceSource.DEVICE_LOCATION) observation.source else existing.source,
                firstObservedAt = minOf(existing.firstObservedAt, observation.observedAt),
                lastObservedAt = maxOf(existing.lastObservedAt, observation.observedAt),
                observedDay = existing.observedDay,
            )
        }

        visitedPlaceEventDao.upsert(updated.toEntity())
    }

    private suspend fun recordVisitedPlace(observation: VisitedObservation) {
        val id = visitedPlaceStorageKey(
            countryCode = observation.countryCode,
            country = observation.country,
            city = observation.city,
        )
        val existing = visitedPlaceDao.getById(id)?.toExternalModel()
        val updated = if (existing == null) {
            VisitedPlace(
                city = observation.city,
                region = observation.region,
                country = observation.country,
                countryCode = observation.countryCode,
                latitude = observation.latitude,
                longitude = observation.longitude,
                sources = listOf(observation.source),
                firstVisitedAt = observation.observedAt,
                lastVisitedAt = observation.observedAt,
            )
        } else {
            val preferIncomingCoordinates = observation.source == VisitedPlaceSource.DEVICE_LOCATION ||
                existing.latitude == null ||
                existing.longitude == null
            VisitedPlace(
                city = observation.city ?: existing.city,
                region = observation.region ?: existing.region,
                country = observation.country.ifBlank { existing.country },
                countryCode = observation.countryCode ?: existing.countryCode,
                latitude = if (preferIncomingCoordinates) observation.latitude ?: existing.latitude else existing.latitude ?: observation.latitude,
                longitude = if (preferIncomingCoordinates) observation.longitude ?: existing.longitude else existing.longitude ?: observation.longitude,
                sources = (existing.sources + observation.source).distinct(),
                firstVisitedAt = minOf(existing.firstVisitedAt, observation.observedAt),
                lastVisitedAt = maxOf(existing.lastVisitedAt, observation.observedAt),
            )
        }

        visitedPlaceDao.upsert(updated.toEntity())
    }

    private suspend fun recordVisitedCountryDay(observation: VisitedObservation) {
        val observedEntries = visitedCountryDayDao.loadAll()
            .map { it.toExternalModel() }
            .filter { it.isInferred.not() }
            .sortedBy(VisitedCountryDay::date)
            .toMutableList()

        val entry = VisitedCountryDay(
            date = observation.observedAt.atZone(ZoneId.systemDefault()).toLocalDate(),
            country = observation.country,
            countryCode = observation.countryCode,
            source = observation.source,
            isInferred = false,
        )

        val existingIndex = observedEntries.indexOfFirst { it.date == entry.date }
        if (existingIndex >= 0) {
            val existing = observedEntries[existingIndex]
            val shouldReplace = existing.source == VisitedPlaceSource.PUBLIC_IP_GEOLOCATION &&
                entry.source == VisitedPlaceSource.DEVICE_LOCATION
            if (shouldReplace.not()) {
                return
            }
            observedEntries[existingIndex] = entry
        } else {
            observedEntries += entry
        }

        visitedCountryDayDao.replaceAll(
            rebuiltCountryDays(observedEntries).map { it.toEntity() },
        )
    }
}

private fun visitedPlaceEventStorageKey(
    observation: VisitedObservation,
    observedDay: java.time.LocalDate,
): String =
    listOf(
        visitedPlaceStorageKey(
            countryCode = observation.countryCode,
            country = observation.country,
            city = observation.city,
        ),
        observedDay.toString(),
    ).joinToString("|")

internal fun rebuiltCountryDays(observedEntries: List<VisitedCountryDay>): List<VisitedCountryDay> {
    val sorted = observedEntries
        .filter { it.isInferred.not() }
        .sortedBy(VisitedCountryDay::date)

    if (sorted.isEmpty()) {
        return emptyList()
    }

    val rebuilt = mutableListOf(sorted.first())
    for (index in 1 until sorted.size) {
        val previous = sorted[index - 1]
        val current = sorted[index]
        val gapDays = java.time.temporal.ChronoUnit.DAYS.between(previous.date, current.date).toInt() - 1
        if (gapDays > 0) {
            rebuilt += inferredCountryDays(previous, current, gapDays)
        }
        rebuilt += current
    }

    return rebuilt
}

private fun inferredCountryDays(
    previous: VisitedCountryDay,
    current: VisitedCountryDay,
    gapDays: Int,
): List<VisitedCountryDay> {
    val usesSameCountry = previous.countryCode.equals(current.countryCode, ignoreCase = true) ||
        (
            previous.countryCode == null &&
                current.countryCode == null &&
                previous.country.normalizedCountryName() == current.country.normalizedCountryName()
            )

    val previousCountryCount = if (usesSameCountry) {
        gapDays
    } else {
        (gapDays + 1) / 2
    }

    return (1..gapDays).map { offset ->
        val template = if (offset <= previousCountryCount) previous else current
        VisitedCountryDay(
            date = previous.date.plusDays(offset.toLong()),
            country = template.country,
            countryCode = template.countryCode,
            source = template.source,
            isInferred = true,
        )
    }
}

private fun VisitedObservation.normalized(): VisitedObservation? {
    val normalizedCountry = country.trim()
    if (normalizedCountry.isEmpty()) {
        return null
    }

    return copy(
        city = city.trimmedOrNull(),
        region = region.trimmedOrNull(),
        country = normalizedCountry,
        countryCode = countryCode.trimmedOrNull()?.uppercase(),
    )
}

private fun String?.trimmedOrNull(): String? =
    this?.trim()?.takeIf(String::isNotBlank)

private fun String.normalizedCountryName(): String =
    Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
