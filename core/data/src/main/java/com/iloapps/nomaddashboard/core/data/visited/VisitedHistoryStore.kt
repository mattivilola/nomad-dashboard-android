package com.iloapps.nomaddashboard.core.data.visited

import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedPlaceEvent
import com.iloapps.nomaddashboard.core.model.VisitedCountryDayProvenance
import kotlinx.coroutines.flow.Flow

interface VisitedHistoryStore {
    val visitedPlaces: Flow<List<VisitedPlace>>
    val visitedCountryDays: Flow<List<VisitedCountryDay>>
    val visitedPlaceEvents: Flow<List<VisitedPlaceEvent>>

    suspend fun recordObservation(observation: VisitedObservation)
    suspend fun correctCountryDay(
        date: java.time.LocalDate,
        country: String,
        countryCode: String?,
    ) = Unit
    suspend fun clearHistory()
}
