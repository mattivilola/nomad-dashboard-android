package com.iloapps.nomaddashboard.core.data.visited

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.database.dao.VisitedCountryDayDao
import com.iloapps.nomaddashboard.core.database.dao.VisitedPlaceDao
import com.iloapps.nomaddashboard.core.database.entity.VisitedCountryDayEntity
import com.iloapps.nomaddashboard.core.database.entity.VisitedPlaceEntity
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
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
        val dayDao = FakeVisitedCountryDayDao()
        val store = RoomVisitedHistoryStore(placeDao, dayDao, ImmediateTransactionRunner)

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
        val dayDao = FakeVisitedCountryDayDao()
        val store = RoomVisitedHistoryStore(placeDao, dayDao, ImmediateTransactionRunner)

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
        val dayDao = FakeVisitedCountryDayDao()
        val store = RoomVisitedHistoryStore(placeDao, dayDao, ImmediateTransactionRunner)

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
