package com.iloapps.nomaddashboard.core.travel.safety

import kotlinx.serialization.json.Json

/**
 * An immutable, indexed set of missions. [bundled] is the shipped dataset; tests build their own.
 */
class DiplomaticMissionIndex(
    /** Every mission in the index, in the order given. */
    val missions: List<DiplomaticMission>,
    val source: String? = null,
    val generatedAt: String? = null,
) {
    private val missionsByRoute: Map<Route, List<DiplomaticMission>> = missions.groupBy {
        Route(sending = it.sendingCountry.normalizedCountryCode(), host = it.hostCountry.normalizedCountryCode())
    }

    private data class Route(val sending: String, val host: String)

    internal fun missions(sendingCountry: String, hostCountry: String): List<DiplomaticMission> =
        missionsByRoute[Route(sendingCountry.normalizedCountryCode(), hostCountry.normalizedCountryCode())].orEmpty()

    companion object {
        const val BundledResourcePath = "diplomatic-missions.json"

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * The dataset bundled with this module, decoded once on first use (about 2 MB of JSON, so
         * call it off the main thread). An unreadable resource yields an empty index rather than a
         * crash, so the card simply has nothing to show.
         */
        val bundled: DiplomaticMissionIndex by lazy { fromResource(BundledResourcePath) }

        /** Decodes a dataset in the on-disk format, or returns an empty index when it cannot be read. */
        fun parse(text: String): DiplomaticMissionIndex = runCatching {
            val dataset = json.decodeFromString<DiplomaticMissionDataset>(text)
            DiplomaticMissionIndex(dataset.missions, source = dataset.source, generatedAt = dataset.generatedAt)
        }.getOrElse { DiplomaticMissionIndex(emptyList()) }

        private fun fromResource(path: String): DiplomaticMissionIndex {
            val text = DiplomaticMissionIndex::class.java.classLoader
                ?.getResourceAsStream(path)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return DiplomaticMissionIndex(emptyList())
            return parse(text)
        }
    }
}

internal fun String.normalizedCountryCode(): String = trim().uppercase()
