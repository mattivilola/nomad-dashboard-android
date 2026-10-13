package com.iloapps.nomaddashboard.core.travel.stays

import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionDirectory

/**
 * The Schengen area and the 90/180-day rule for short stays of third-country nationals.
 *
 * Source: European Commission, DG Migration and Home Affairs, "Schengen Area"
 * https://home-affairs.ec.europa.eu/policies/schengen/schengen-area_en
 * Verified 2026-10: 29 countries, 25 EU member states plus Iceland, Liechtenstein, Norway and
 * Switzerland.
 * - Bulgaria and Romania have been full members since 1 January 2025.
 * - Ireland is not a member (it opted out under the Schengen Protocol and keeps its own border policy).
 * - Cyprus is not a member yet: it takes part in Schengen cooperation, but the Council has not decided
 *   to lift internal border checks (status per the Commission page above and its 2026 State of Schengen
 *   report). Re-verify when the Council takes that decision.
 *
 * Free movement (no 90/180 limit) applies to citizens of every EU member state, including Ireland and
 * Cyprus, to citizens of the EEA states Iceland, Liechtenstein and Norway, and to Swiss citizens.
 * - EEA = EU member states plus IS, LI, NO: https://www.efta.int/eea/eea-agreement (verified 2026-10).
 * - Swiss citizens (and EEA citizens) are "persons enjoying the right of free movement" under the
 *   EU-Switzerland Agreement on the Free Movement of Persons and are outside the 90/180 rule per
 *   Article 2(5) of the Schengen Borders Code, Regulation (EU) 2016/399:
 *   https://eur-lex.europa.eu/eli/reg/2016/399/oj
 */
object SchengenArea {
    /** The 27 EU member states, the same list the EU consular-protection fallback uses. */
    val euMemberStates: List<String> = DiplomaticMissionDirectory.euMemberStates

    /** EU member states that are not (yet) in the Schengen area. */
    private val euStatesOutsideSchengen: Set<String> = setOf("CY", "IE")

    /** EEA states outside the EU. All three are Schengen members and have free movement rights. */
    val eeaStatesOutsideEu: List<String> = listOf("IS", "LI", "NO")

    /**
     * Switzerland is neither EU nor EEA, but its citizens have free movement rights through bilateral
     * agreements and Switzerland is a Schengen member.
     */
    const val Switzerland = "CH"

    /**
     * Schengen member states (ISO 3166-1 alpha-2), sorted: the EU states except Cyprus and Ireland, plus
     * Iceland, Liechtenstein, Norway and Switzerland. Microstates with open borders (Monaco, San Marino,
     * Vatican City, Andorra) are not members and are not counted.
     */
    val memberStates: List<String> =
        (euMemberStates.filterNot { it in euStatesOutsideSchengen } + eeaStatesOutsideEu + Switzerland).sorted()

    /** Citizens of these states (EU, EEA, Switzerland) have free movement and no 90/180-day limit. */
    val freeMovementStates: List<String> = (euMemberStates + eeaStatesOutsideEu + Switzerland).sorted()

    /** Days a third-country national may stay within any rolling window of [WindowDays]. */
    const val MaximumStayDays = 90
    const val WindowDays = 180

    private val members: Set<String> = memberStates.toSet()
    private val freeMovement: Set<String> = freeMovementStates.toSet()

    fun isMember(countryCode: String): Boolean = countryCode.uppercase() in members

    /** True when any of the citizenships has free movement rights, so the 90/180-day limit does not apply. */
    fun isExempt(citizenships: List<String>): Boolean =
        citizenships.any { it.trim().uppercase() in freeMovement }
}
