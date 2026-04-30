package com.iloapps.nomaddashboard.core.data.visited

import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedPlaceEvent
import kotlinx.coroutines.flow.Flow

interface VisitedHistoryStore {
    val visitedPlaces: Flow<List<VisitedPlace>>
    val visitedCountryDays: Flow<List<VisitedCountryDay>>
    val visitedPlaceEvents: Flow<List<VisitedPlaceEvent>>

    suspend fun recordObservation(observation: VisitedObservation)
    suspend fun clearHistory()
}
