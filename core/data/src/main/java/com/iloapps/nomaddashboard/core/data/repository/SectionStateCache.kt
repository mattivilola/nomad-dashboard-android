package com.iloapps.nomaddashboard.core.data.repository

import com.iloapps.nomaddashboard.core.database.entity.DashboardSectionCacheEntity
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.SectionRefreshState
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
private data class SectionStateDto(val successMillis: Long?, val locationKey: String, val locationLabel: String)

internal fun SectionRefreshState.toCacheEntity(card: DashboardCardId, json: Json): DashboardSectionCacheEntity =
    DashboardSectionCacheEntity(
        sectionId = "state_${card.name}",
        payloadJson = json.encodeToString(SectionStateDto(lastSuccessAt?.toEpochMilli(), locationKey, locationLabel)),
        fetchedAtEpochMillis = lastSuccessAt?.toEpochMilli() ?: 0,
    )

internal fun loadSectionStates(entries: List<DashboardSectionCacheEntity>, json: Json): Map<DashboardCardId, SectionRefreshState> =
    entries.mapNotNull { entry ->
        val card = DashboardCardId.entries.firstOrNull { entry.sectionId == "state_${it.name}" } ?: return@mapNotNull null
        runCatching {
            val dto = json.decodeFromString<SectionStateDto>(entry.payloadJson)
            card to SectionRefreshState(dto.successMillis?.let(Instant::ofEpochMilli), dto.locationKey, dto.locationLabel)
        }.getOrNull()
    }.toMap()
