package com.iloapps.nomaddashboard.core.travel.safety

/** Where a traveller can get consular help in the country they are in. */
data class EmbassyLookup(
    /** Missions of the traveller's own state(s) in the host country. */
    val ownMatches: List<DiplomaticMissionMatch>,
    /** Missions of other states that can help. Only filled when there is no own mission at all. */
    val fallbackMatches: List<DiplomaticMissionMatch>,
    /** The arrangement behind the best fallback match, null when [fallbackMatches] is empty. */
    val fallbackReason: FallbackReason?,
    /** Upper-case host country code the lookup was made for. */
    val hostCountry: String,
    /** Upper-case citizenships the lookup was made for, in the order given, without duplicates. */
    val citizenships: List<String>,
) {
    /** Why the fallback missions are shown instead of the traveller's own. */
    enum class FallbackReason {
        /** EU citizens can use any EU member state's mission when their own is absent (Directive (EU) 2015/637). */
        EuConsularProtection,

        /**
         * Nordic missions assist citizens of other Nordic countries that are not represented locally
         * (Nordic cooperation; a cooperative fallback, not an individual entitlement).
         */
        NordicCooperation,
    }

    val isEmpty: Boolean
        get() = ownMatches.isEmpty() && fallbackMatches.isEmpty()

    /**
     * The arrangement that lets this lookup's citizens use [mission], null when none applies (for
     * example a mission from the own list). The Nordic arrangement is preferred where both apply,
     * since it is the more specific one.
     */
    fun fallbackReasonFor(mission: DiplomaticMission): FallbackReason? =
        fallbackReasonFor(mission.sendingCountry, citizenships.filter { it != hostCountry })

    companion object {
        const val DefaultLimit = 3

        /**
         * - [citizenships]: ISO codes of the user's citizenships.
         * - [hostCountry]: ISO code of the country the user is in.
         * - [coordinate]: Device location, when available, used to rank by distance.
         * - [limit]: Maximum matches in each of [ownMatches] and [fallbackMatches].
         *
         * A citizenship equal to the host country is skipped, because the user is at home for that
         * passport. A dual citizen abroad on one of their passports still gets that passport's missions.
         * Fallback states are only consulted when none of the remaining citizenships has a mission
         * in the host country.
         */
        fun find(
            citizenships: List<String>,
            hostCountry: String?,
            coordinate: MissionCoordinate?,
            limit: Int = DefaultLimit,
            index: DiplomaticMissionIndex = DiplomaticMissionIndex.bundled,
        ): EmbassyLookup {
            val host = hostCountry?.normalizedCountryCode().orEmpty()
            val normalizedCitizenships = citizenships
                .map { it.normalizedCountryCode() }
                .filter { it.isNotEmpty() }
                .distinct()
            val abroadCitizenships = normalizedCitizenships.filter { it != host }

            if (host.isEmpty() || abroadCitizenships.isEmpty()) {
                return EmbassyLookup(emptyList(), emptyList(), null, host, normalizedCitizenships)
            }

            val own = DiplomaticMissionDirectory.nearest(abroadCitizenships, host, coordinate, limit, index)
            if (own.isNotEmpty()) {
                return EmbassyLookup(own, emptyList(), null, host, normalizedCitizenships)
            }

            val ownStates = normalizedCitizenships.toSet()
            val fallbackStates = abroadCitizenships
                .flatMap(DiplomaticMissionDirectory::fallbackStates)
                .toSet()
                .minus(ownStates)
                .minus(host)
                .sorted()
            val fallback = DiplomaticMissionDirectory.nearest(
                sendingCountries = fallbackStates,
                hostCountry = host,
                near = coordinate,
                limit = limit,
                index = index,
                arrangementRank = { mission ->
                    // The Nordic arrangement is the more specific one, so those missions come first.
                    when (fallbackReasonFor(mission.sendingCountry, abroadCitizenships)) {
                        FallbackReason.NordicCooperation -> 0
                        FallbackReason.EuConsularProtection -> 1
                        null -> 2
                    }
                },
            )
            val reason = fallback.firstOrNull()?.let { fallbackReasonFor(it.mission.sendingCountry, abroadCitizenships) }

            return EmbassyLookup(emptyList(), fallback, reason, host, normalizedCitizenships)
        }

        private fun fallbackReasonFor(sendingCountry: String, citizenships: List<String>): FallbackReason? {
            val sending = sendingCountry.normalizedCountryCode()
            val nordic = DiplomaticMissionDirectory.nordicStates
            val eu = DiplomaticMissionDirectory.euMemberStates

            if (sending in nordic && citizenships.any { it in nordic && it != sending }) {
                return FallbackReason.NordicCooperation
            }
            if (sending in eu && citizenships.any { it in eu && it != sending }) {
                return FallbackReason.EuConsularProtection
            }
            return null
        }
    }
}
