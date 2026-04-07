package com.iloapps.nomaddashboard.core.data.visited

import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import kotlinx.coroutines.flow.Flow

interface VisitedHistoryStore {
    val visitedPlaces: Flow<List<VisitedPlace>>
    val visitedCountryDays: Flow<List<VisitedCountryDay>>

    suspend fun recordObservation(observation: VisitedObservation)
}
