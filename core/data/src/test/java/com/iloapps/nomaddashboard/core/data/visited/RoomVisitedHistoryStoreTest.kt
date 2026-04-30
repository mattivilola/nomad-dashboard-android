package com.iloapps.nomaddashboard.core.data.visited

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.database.dao.VisitedCountryDayDao
import com.iloapps.nomaddashboard.core.database.dao.VisitedPlaceEventDao
import com.iloapps.nomaddashboard.core.database.dao.VisitedPlaceDao
import com.iloapps.nomaddashboard.core.database.entity.VisitedCountryDayEntity
import com.iloapps.nomaddashboard.core.database.entity.VisitedPlaceEventEntity
import com.iloapps.nomaddashboard.core.database.entity.VisitedPlaceEntity
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlaceEvent
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import com.iloapps.nomaddashboard.core.model.travelStopsForYear
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomVisitedHistoryStoreTest {
    @Test
    fun `record observation merges places and prefers device coordinates`() = runTest {
        val placeDao = FakeVisitedPlaceDao()
        val eventDao = FakeVisitedPlaceEventDao()
        val dayDao = FakeVisitedCountryDayDao()
        val store = RoomVisitedHistoryStore(placeDao, eventDao, dayDao, ImmediateTransactionRunner)

        store.recordObservation(
            observation(
                city = "Malaga",
                country = "Spain",
                countryCode = "ES",
                source = VisitedPlaceSource.PUBLIC_IP_GEOLOCATION,
                observedAt = Instant.parse("2026-01-01T10:00:00Z"),
            ),
        )
        store.recordObservation(
            observation(
                city = "Malaga",
                region = "Andalusia",
                country = "Spain",
                countryCode = "ES",
                latitude = 36.72,
                longitude = -4.42,
                source = VisitedPlaceSource.DEVICE_LOCATION,
                observedAt = Instant.parse("2026-01-05T10:00:00Z"),
            ),
        )

        val places = store.visitedPlaces.first()

        assertThat(places).hasSize(1)
        assertThat(places.first().sources).containsExactly(
            VisitedPlaceSource.PUBLIC_IP_GEOLOCATION,
            VisitedPlaceSource.DEVICE_LOCATION,
        ).inOrder()
        assertThat(places.first().region).isEqualTo("Andalusia")
        assertThat(places.first().latitude).isEqualTo(36.72)
        assertThat(places.first().firstVisitedAt).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"))
        assertThat(places.first().lastVisitedAt).isEqualTo(Instant.parse("2026-01-05T10:00:00Z"))
    }

    @Test
    fun `same day device observation replaces ip country day`() = runTest {
        val placeDao = FakeVisitedPlaceDao()
        val eventDao = FakeVisitedPlaceEventDao()
        val dayDao = FakeVisitedCountryDayDao()
        val store = RoomVisitedHistoryStore(placeDao, eventDao, dayDao, ImmediateTransactionRunner)

        store.recordObservation(
            observation(
                city = "Helsinki",
                country = "Finland",
                countryCode = "FI",
                source = VisitedPlaceSource.PUBLIC_IP_GEOLOCATION,
                observedAt = Instant.parse("2026-01-12T10:00:00Z"),
            ),
        )
        store.recordObservation(
            observation(
                city = "Stockholm",
                country = "Sweden",
                countryCode = "SE",
                source = VisitedPlaceSource.DEVICE_LOCATION,
                observedAt = Instant.parse("2026-01-12T12:00:00Z"),
            ),
        )

        val days = store.visitedCountryDays.first()

        assertThat(days).hasSize(1)
        assertThat(days.first().countryCode).isEqualTo("SE")
        assertThat(days.first().source).isEqualTo(VisitedPlaceSource.DEVICE_LOCATION)
        assertThat(days.first().isInferred).isFalse()
    }

    @Test
    fun `rebuilt country days fills odd gaps with extra day on earlier country`() {
        val rebuilt = rebuiltCountryDays(
            listOf(
                VisitedCountryDay(
                    date = LocalDate.of(2026, 1, 1),
                    country = "Spain",
                    countryCode = "ES",
                    source = VisitedPlaceSource.DEVICE_LOCATION,
                    isInferred = false,
                ),
                VisitedCountryDay(
                    date = LocalDate.of(2026, 1, 5),
                    country = "France",
                    countryCode = "FR",
                    source = VisitedPlaceSource.DEVICE_LOCATION,
                    isInferred = false,
                ),
            ),
        )

        assertThat(rebuilt.map { it.countryCode }).containsExactly("ES", "ES", "ES", "FR", "FR").inOrder()
        assertThat(rebuilt.map(VisitedCountryDay::isInferred)).containsExactly(false, true, true, true, false).inOrder()
    }

    @Test
    fun `later observed day replaces inferred day and rebuilds following gap`() = runTest {
        val placeDao = FakeVisitedPlaceDao()
        val eventDao = FakeVisitedPlaceEventDao()
        val dayDao = FakeVisitedCountryDayDao()
        val store = RoomVisitedHistoryStore(placeDao, eventDao, dayDao, ImmediateTransactionRunner)

        store.recordObservation(
            observation(
                city = "Madrid",
                country = "Spain",
                countryCode = "ES",
                source = VisitedPlaceSource.DEVICE_LOCATION,
                observedAt = Instant.parse("2026-01-01T10:00:00Z"),
            ),
        )
        store.recordObservation(
            observation(
                city = "Paris",
                country = "France",
                countryCode = "FR",
                source = VisitedPlaceSource.DEVICE_LOCATION,
                observedAt = Instant.parse("2026-01-05T10:00:00Z"),
            ),
        )
        store.recordObservation(
            observation(
                city = "Amsterdam",
                country = "Netherlands",
                countryCode = "NL",
                source = VisitedPlaceSource.DEVICE_LOCATION,
                observedAt = Instant.parse("2026-01-03T10:00:00Z"),
            ),
        )

        val days = store.visitedCountryDays.first()

        assertThat(days.map { it.date.dayOfMonth to it.countryCode }).containsExactly(
            1 to "ES",
            2 to "ES",
            3 to "NL",
            4 to "NL",
            5 to "FR",
        ).inOrder()
        assertThat(days.map(VisitedCountryDay::isInferred)).containsExactly(false, true, false, true, false).inOrder()
    }

    @Test
    fun `same place same day observations merge into one event`() = runTest {
        val store = RoomVisitedHistoryStore(
            FakeVisitedPlaceDao(),
            FakeVisitedPlaceEventDao(),
            FakeVisitedCountryDayDao(),
            ImmediateTransactionRunner,
        )

        store.recordObservation(
            observation(
                city = "Lisbon",
                country = "Portugal",
                countryCode = "PT",
                source = VisitedPlaceSource.PUBLIC_IP_GEOLOCATION,
                observedAt = Instant.parse("2026-04-01T08:00:00Z"),
            ),
        )
        store.recordObservation(
            observation(
                city = "Lisbon",
                country = "Portugal",
                countryCode = "PT",
                latitude = 38.7223,
                longitude = -9.1393,
                source = VisitedPlaceSource.DEVICE_LOCATION,
                observedAt = Instant.parse("2026-04-01T18:00:00Z"),
            ),
        )

        val events = store.visitedPlaceEvents.first()

        assertThat(events).hasSize(1)
        assertThat(events.single().source).isEqualTo(VisitedPlaceSource.DEVICE_LOCATION)
        assertThat(events.single().firstObservedAt).isEqualTo(Instant.parse("2026-04-01T08:00:00Z"))
        assertThat(events.single().lastObservedAt).isEqualTo(Instant.parse("2026-04-01T18:00:00Z"))
        assertThat(events.single().latitude).isEqualTo(38.7223)
    }

    @Test
    fun `same place different day observations stay separate and group into one stop`() = runTest {
        val store = RoomVisitedHistoryStore(
            FakeVisitedPlaceDao(),
            FakeVisitedPlaceEventDao(),
            FakeVisitedCountryDayDao(),
            ImmediateTransactionRunner,
        )

        store.recordObservation(
            observation(
                city = "Tallinn",
                country = "Estonia",
                countryCode = "EE",
                latitude = 59.437,
                longitude = 24.7536,
                source = VisitedPlaceSource.DEVICE_LOCATION,
                observedAt = Instant.parse("2026-04-01T08:00:00Z"),
            ),
        )
        store.recordObservation(
            observation(
                city = "Tallinn",
                country = "Estonia",
                countryCode = "EE",
                latitude = 59.437,
                longitude = 24.7536,
                source = VisitedPlaceSource.DEVICE_LOCATION,
                observedAt = Instant.parse("2026-04-02T08:00:00Z"),
            ),
        )

        val events = store.visitedPlaceEvents.first()
        val stops = events.travelStopsForYear(2026)

        assertThat(events).hasSize(2)
        assertThat(stops).hasSize(1)
        assertThat(stops.single().dayCount).isEqualTo(2)
    }

    @Test
    fun `clear history removes aggregate days places and events`() = runTest {
        val store = RoomVisitedHistoryStore(
            FakeVisitedPlaceDao(),
            FakeVisitedPlaceEventDao(),
            FakeVisitedCountryDayDao(),
            ImmediateTransactionRunner,
        )

        store.recordObservation(
            observation(
                city = "Riga",
                country = "Latvia",
                countryCode = "LV",
                source = VisitedPlaceSource.DEVICE_LOCATION,
                observedAt = Instant.parse("2026-04-01T08:00:00Z"),
            ),
        )
        store.clearHistory()

        assertThat(store.visitedPlaces.first()).isEmpty()
        assertThat(store.visitedCountryDays.first()).isEmpty()
        assertThat(store.visitedPlaceEvents.first()).isEmpty()
    }

    private fun observation(
        city: String?,
        country: String,
        countryCode: String?,
        source: VisitedPlaceSource,
        observedAt: Instant,
        region: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ) = VisitedObservation(
        city = city,
        region = region,
        country = country,
        countryCode = countryCode,
        latitude = latitude,
        longitude = longitude,
        source = source,
        observedAt = observedAt,
    )
}

private object ImmediateTransactionRunner : DatabaseTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

private class FakeVisitedPlaceDao : VisitedPlaceDao {
    private val state = MutableStateFlow<List<VisitedPlaceEntity>>(emptyList())

    override fun observeAll(): Flow<List<VisitedPlaceEntity>> = state

    override suspend fun getById(id: String): VisitedPlaceEntity? =
        state.value.firstOrNull { it.id == id }

    override suspend fun upsert(place: VisitedPlaceEntity) {
        val next = state.value.toMutableList()
        val index = next.indexOfFirst { it.id == place.id }
        if (index >= 0) {
            next[index] = place
        } else {
            next += place
        }
        state.value = next.sortedByDescending { it.lastVisitedAtEpochMillis }
    }

    override suspend fun clearAll() {
        state.value = emptyList()
    }
}

private class FakeVisitedPlaceEventDao : VisitedPlaceEventDao {
    private val state = MutableStateFlow<List<VisitedPlaceEventEntity>>(emptyList())

    override fun observeAll(): Flow<List<VisitedPlaceEventEntity>> = state

    override suspend fun getById(id: String): VisitedPlaceEventEntity? =
        state.value.firstOrNull { it.id == id }

    override suspend fun upsert(event: VisitedPlaceEventEntity) {
        val next = state.value.toMutableList()
        val index = next.indexOfFirst { it.id == event.id }
        if (index >= 0) {
            next[index] = event
        } else {
            next += event
        }
        state.value = next.sortedWith(
            compareBy<VisitedPlaceEventEntity> { it.observedDayIso }
                .thenBy { it.firstObservedAtEpochMillis },
        )
    }

    override suspend fun clearAll() {
        state.value = emptyList()
    }
}

private class FakeVisitedCountryDayDao : VisitedCountryDayDao {
    private val state = MutableStateFlow<List<VisitedCountryDayEntity>>(emptyList())

    override fun observeAll(): Flow<List<VisitedCountryDayEntity>> = state

    override suspend fun loadAll(): List<VisitedCountryDayEntity> = state.value

    override suspend fun insertAll(items: List<VisitedCountryDayEntity>) {
        state.value = items.sortedBy { it.dateIso }
    }

    override suspend fun clearAll() {
        state.value = emptyList()
    }
}
