package com.iloapps.nomaddashboard.core.travel.reference

import java.util.Currency
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

// Bundled, offline travel reference data: which side of the road a country drives on, what its
// sockets and mains supply look like, and its currency.
//
// A wrong answer is worse than none (a traveller who steps into traffic looking the wrong way, or
// plugs a 120 V appliance into 230 V), so a country is only listed when at least two independent
// sources agree. Everything else returns null.
//
// Ported from the macOS app (NomadCore TravelReferenceData.swift, verified 2026-10); the tables must
// stay identical to it, and any correction should land in both apps.
//
// Driving side
//
// The LEFT-driving list is encoded explicitly (76 jurisdictions: 54 UN member states and 22
// territories); every other valid ISO 3166-1 alpha-2 code drives on the right. A jurisdiction is on
// the list when at least two of these three compilations agree:
//  - Wikipedia, "Left- and right-hand traffic" (country table, retrieved 2026-10-12):
//    https://en.wikipedia.org/wiki/Left-_and_right-hand_traffic
//  - WorldStandards.eu, "List of left-driving countries":
//    https://www.worldstandards.eu/cars/list-of-left-driving-countries/
//  - Motoring Research, "Which countries drive on the left?":
//    https://www.motoringresearch.com/advice/which-countries-drive-the-left/
// Cross-check: the 54 UN member states in the list match the "54 left-driving UN countries" count
// Wikipedia reports. Samoa switched to the left on 7 September 2009 and is included. Myanmar,
// Gibraltar, the British Indian Ocean Territory and Vanuatu drive on the right (Myanmar's cars are
// mostly right-hand-drive imports, but traffic keeps right) and are deliberately absent.
// Ascension Island, Tristan da Cunha and the Akrotiri and Dhekelia base areas have no ISO 3166-1
// code of their own and are covered by SH and CY. No government or road-authority compilation of
// all countries could be read; the per-country FCDO travel advice pages do not state the side.
//
// Electricity
//
// Sources, both required to agree:
//  - IEC World Plugs (the authority named for this data): https://www.iec.ch/world-plugs. IEC
//    has since retired the interactive guide (the page reads "no longer available", checked
//    2026-10-12), so the figures are IEC's last published per-plug-type pages, read from the
//    Internet Archive snapshot of 2021-10-24 of http://pubweb2.iec.ch/worldplugs/type{A...N}.htm.
//  - Wikipedia, "Mains electricity by country" (retrieved 2026-10-12), which carries national
//    standards and current nominal voltages: https://en.wikipedia.org/wiki/Mains_electricity_by_country
// Rules applied to every row:
//  - `plugTypes` holds only the types BOTH sources list. Rarer types that only one lists (for
//    example Portugal's type E, Thailand's type F) are left out rather than guessed.
//  - `voltage` is the current nominal single-phase household voltage from Wikipedia, accepted only
//    when an IEC value is within 10 % of it. IEC's 220 V and 240 V entries are the pre-harmonisation
//    figures for what is now 230 V (+10 % / -6 %) in most countries, so rows can read 230 where IEC
//    reads 220. Morocco and Indonesia list a second, legacy voltage at IEC (127 V and 110 V); the
//    current single nominal system is stored.
//  - `frequencyHertz` must be identical in both sources.
// Left out, so the lookup returns null: Brazil (127 V or 220 V depending on the state), Japan (50 Hz
// in the east, 60 Hz in the west; the model holds one frequency), Saudi Arabia, Cuba and Bolivia
// (mixed supplies), Uzbekistan (the sources disagree on the plug types), and every country not in
// the table. Covered: every country in the emergency-number directory except Brazil and Japan, plus the
// most-visited other destinations.
//
// Currency
//
// Not stored: it comes from the platform's region data (`java.util.Currency`, CLDR/ICU backed) so it
// never goes stale here.

/** Which side of the road traffic keeps to. */
enum class DrivingSide {
    LEFT,
    RIGHT,
}

/** The mains supply a traveller finds in a country. */
data class CountryElectricity(
    /** IEC plug type letters (for example "C", "E"), sorted, that are in common use. */
    val plugTypes: List<String>,
    /** Nominal single-phase household voltage in volts. */
    val voltage: Int,
    val frequencyHertz: Int,
) {
    /**
     * True when an appliance and adapter from [other] need rethinking here: different plug types, a
     * voltage more than 10 % apart (230 V versus 240 V is the same supply, 120 V versus 230 V is not),
     * or a different frequency.
     */
    fun differsFrom(other: CountryElectricity): Boolean {
        if (plugTypes.toSet() != other.plugTypes.toSet() || frequencyHertz != other.frequencyHertz) {
            return true
        }
        val larger = max(voltage, other.voltage).toDouble()
        return abs(voltage - other.voltage) / larger > VOLTAGE_TOLERANCE
    }

    private companion object {
        const val VOLTAGE_TOLERANCE = 0.1
    }
}

object TravelReferenceData {
    // Driving side

    /**
     * The side of the road for an ISO 3166-1 alpha-2 country code (case-insensitive), or null when
     * the code is not a valid region.
     */
    fun drivingSide(countryCode: String?): DrivingSide? {
        val normalized = normalized(countryCode)?.takeIf { it in validRegions } ?: return null
        return if (normalized in leftDrivingRegions) DrivingSide.LEFT else DrivingSide.RIGHT
    }

    /** Jurisdictions that drive on the left, by ISO 3166-1 alpha-2 code. */
    internal val leftDrivingRegions: Set<String> = setOf(
        // Europe
        "GB", "IE", "MT", "CY", "GG", "JE", "IM",
        // Africa
        "BW", "KE", "LS", "MW", "MU", "MZ", "NA", "SC", "SZ", "TZ", "UG", "ZA", "ZM", "ZW",
        "SH",
        // Caribbean and South America
        "AG", "BS", "BB", "DM", "GD", "GY", "JM", "KN", "LC", "VC", "SR", "TT",
        "AI", "BM", "VG", "KY", "MS", "TC", "FK", "GS", "VI",
        // Asia
        "BD", "BT", "BN", "IN", "ID", "JP", "LK", "MV", "MY", "NP", "PK", "SG", "TH", "TL",
        "HK", "MO",
        // Oceania
        "AU", "CX", "CC", "NF", "CK", "NU", "FJ", "KI", "NR", "NZ", "PG", "WS", "SB", "TO", "TV",
        "PN", "TK",
    )

    // Electricity

    /**
     * Plug types and mains supply for an ISO 3166-1 alpha-2 country code (case-insensitive), or null
     * when the country could not be verified against two sources.
     */
    fun electricity(countryCode: String?): CountryElectricity? =
        normalized(countryCode)?.let { electricityEntries[it] }

    private val electricityEntries: Map<String, CountryElectricity> = mapOf(
        // European Union and EEA
        "AT" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "BE" to CountryElectricity(plugTypes = listOf("C", "E"), voltage = 230, frequencyHertz = 50),
        "BG" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "HR" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "CY" to CountryElectricity(plugTypes = listOf("G"), voltage = 240, frequencyHertz = 50),
        "CZ" to CountryElectricity(plugTypes = listOf("C", "E"), voltage = 230, frequencyHertz = 50),
        "DK" to CountryElectricity(plugTypes = listOf("C", "E", "F", "K"), voltage = 230, frequencyHertz = 50),
        "EE" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "FI" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "FR" to CountryElectricity(plugTypes = listOf("C", "E"), voltage = 230, frequencyHertz = 50),
        "DE" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "GR" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "HU" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "IS" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "IE" to CountryElectricity(plugTypes = listOf("G"), voltage = 230, frequencyHertz = 50),
        "IT" to CountryElectricity(plugTypes = listOf("C", "F", "L"), voltage = 230, frequencyHertz = 50),
        "LV" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "LI" to CountryElectricity(plugTypes = listOf("C", "J"), voltage = 230, frequencyHertz = 50),
        "LT" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "LU" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "MT" to CountryElectricity(plugTypes = listOf("G"), voltage = 230, frequencyHertz = 50),
        "NL" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "NO" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "PL" to CountryElectricity(plugTypes = listOf("C", "E"), voltage = 230, frequencyHertz = 50),
        "PT" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "RO" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "SK" to CountryElectricity(plugTypes = listOf("C", "E"), voltage = 230, frequencyHertz = 50),
        "SI" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "ES" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "SE" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),

        // Other European and post-Soviet countries
        "GB" to CountryElectricity(plugTypes = listOf("G"), voltage = 230, frequencyHertz = 50),
        "CH" to CountryElectricity(plugTypes = listOf("C", "J"), voltage = 230, frequencyHertz = 50),
        "TR" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "GE" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 220, frequencyHertz = 50),
        "AL" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "BA" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "ME" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "RS" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "MK" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "UA" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "MD" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "BY" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "AM" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "AZ" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 220, frequencyHertz = 50),
        "KZ" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "RU" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),

        // Americas
        "US" to CountryElectricity(plugTypes = listOf("A", "B"), voltage = 120, frequencyHertz = 60),
        "CA" to CountryElectricity(plugTypes = listOf("A", "B"), voltage = 120, frequencyHertz = 60),
        "MX" to CountryElectricity(plugTypes = listOf("A", "B"), voltage = 127, frequencyHertz = 60),
        "CR" to CountryElectricity(plugTypes = listOf("A", "B"), voltage = 120, frequencyHertz = 60),
        "PA" to CountryElectricity(plugTypes = listOf("A", "B"), voltage = 120, frequencyHertz = 60),
        "CO" to CountryElectricity(plugTypes = listOf("A", "B"), voltage = 120, frequencyHertz = 60),
        "PE" to CountryElectricity(plugTypes = listOf("A", "B", "C"), voltage = 220, frequencyHertz = 60),
        "CL" to CountryElectricity(plugTypes = listOf("C", "L"), voltage = 220, frequencyHertz = 50),
        "AR" to CountryElectricity(plugTypes = listOf("C", "I"), voltage = 220, frequencyHertz = 50),
        "EC" to CountryElectricity(plugTypes = listOf("A", "B"), voltage = 120, frequencyHertz = 60),
        "UY" to CountryElectricity(plugTypes = listOf("C", "F", "I", "L"), voltage = 230, frequencyHertz = 50),
        "DO" to CountryElectricity(plugTypes = listOf("A", "B"), voltage = 120, frequencyHertz = 60),
        "BS" to CountryElectricity(plugTypes = listOf("A", "B"), voltage = 120, frequencyHertz = 60),
        "JM" to CountryElectricity(plugTypes = listOf("A", "B"), voltage = 110, frequencyHertz = 50),
        "PR" to CountryElectricity(plugTypes = listOf("A", "B"), voltage = 120, frequencyHertz = 60),
        "GT" to CountryElectricity(plugTypes = listOf("A", "B"), voltage = 120, frequencyHertz = 60),

        // Asia-Pacific
        "TH" to CountryElectricity(plugTypes = listOf("A", "B", "C"), voltage = 230, frequencyHertz = 50),
        "VN" to CountryElectricity(plugTypes = listOf("A", "C", "F"), voltage = 220, frequencyHertz = 50),
        "ID" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "MY" to CountryElectricity(plugTypes = listOf("C", "G", "M"), voltage = 230, frequencyHertz = 50),
        "SG" to CountryElectricity(plugTypes = listOf("C", "G", "M"), voltage = 230, frequencyHertz = 50),
        "PH" to CountryElectricity(plugTypes = listOf("A", "B", "C"), voltage = 230, frequencyHertz = 60),
        "KR" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 220, frequencyHertz = 60),
        "TW" to CountryElectricity(plugTypes = listOf("A", "B"), voltage = 110, frequencyHertz = 60),
        "IN" to CountryElectricity(plugTypes = listOf("C", "D", "M"), voltage = 230, frequencyHertz = 50),
        "LK" to CountryElectricity(plugTypes = listOf("D", "G", "M"), voltage = 230, frequencyHertz = 50),
        "NP" to CountryElectricity(plugTypes = listOf("C", "D", "M"), voltage = 230, frequencyHertz = 50),
        "KH" to CountryElectricity(plugTypes = listOf("A", "C", "G"), voltage = 230, frequencyHertz = 50),
        "AU" to CountryElectricity(plugTypes = listOf("I"), voltage = 230, frequencyHertz = 50),
        "NZ" to CountryElectricity(plugTypes = listOf("I"), voltage = 230, frequencyHertz = 50),
        "CN" to CountryElectricity(plugTypes = listOf("A", "I"), voltage = 220, frequencyHertz = 50),
        "HK" to CountryElectricity(plugTypes = listOf("D", "G"), voltage = 220, frequencyHertz = 50),
        "MO" to CountryElectricity(plugTypes = listOf("D", "G", "M"), voltage = 230, frequencyHertz = 50),
        "LA" to CountryElectricity(plugTypes = listOf("A", "B", "C", "E", "F"), voltage = 230, frequencyHertz = 50),
        "MM" to CountryElectricity(plugTypes = listOf("C", "D", "F", "G"), voltage = 230, frequencyHertz = 50),
        "BD" to CountryElectricity(plugTypes = listOf("C", "D", "G"), voltage = 220, frequencyHertz = 50),
        "PK" to CountryElectricity(plugTypes = listOf("C", "D", "G", "M"), voltage = 230, frequencyHertz = 50),
        "MV" to CountryElectricity(plugTypes = listOf("D", "G", "J", "K", "L"), voltage = 230, frequencyHertz = 50),

        // Middle East and Africa
        "AE" to CountryElectricity(plugTypes = listOf("G"), voltage = 230, frequencyHertz = 50),
        "IL" to CountryElectricity(plugTypes = listOf("C", "H"), voltage = 230, frequencyHertz = 50),
        "JO" to CountryElectricity(plugTypes = listOf("B", "C", "D", "F", "G", "J"), voltage = 230, frequencyHertz = 50),
        "EG" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 220, frequencyHertz = 50),
        "ZA" to CountryElectricity(plugTypes = listOf("C", "M", "N"), voltage = 230, frequencyHertz = 50),
        "MA" to CountryElectricity(plugTypes = listOf("C", "E"), voltage = 220, frequencyHertz = 50),
        "KE" to CountryElectricity(plugTypes = listOf("G"), voltage = 240, frequencyHertz = 50),
        "TZ" to CountryElectricity(plugTypes = listOf("D", "G"), voltage = 230, frequencyHertz = 50),
        "BH" to CountryElectricity(plugTypes = listOf("G"), voltage = 230, frequencyHertz = 50),
        "QA" to CountryElectricity(plugTypes = listOf("D", "G"), voltage = 240, frequencyHertz = 50),
        "OM" to CountryElectricity(plugTypes = listOf("G"), voltage = 240, frequencyHertz = 50),
        "KW" to CountryElectricity(plugTypes = listOf("C", "G"), voltage = 240, frequencyHertz = 50),
        "LB" to CountryElectricity(plugTypes = listOf("A", "B", "C", "D", "G"), voltage = 220, frequencyHertz = 50),
        "TN" to CountryElectricity(plugTypes = listOf("C", "E"), voltage = 230, frequencyHertz = 50),
        "DZ" to CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50),
        "NG" to CountryElectricity(plugTypes = listOf("D", "G"), voltage = 230, frequencyHertz = 50),
        "GH" to CountryElectricity(plugTypes = listOf("D", "G"), voltage = 230, frequencyHertz = 50),
        "NA" to CountryElectricity(plugTypes = listOf("D", "M"), voltage = 220, frequencyHertz = 50),
        "MU" to CountryElectricity(plugTypes = listOf("C", "G"), voltage = 230, frequencyHertz = 50),
        "UG" to CountryElectricity(plugTypes = listOf("G"), voltage = 240, frequencyHertz = 50),
    )

    // Currency

    /**
     * The ISO 4217 currency code in use in an ISO 3166-1 alpha-2 country (for example "MAD" for "MA"),
     * from the platform's region data, or null when the region has no currency of its own.
     */
    fun currencyCode(countryCode: String?): String? {
        val normalized = normalized(countryCode)?.takeIf { it in validRegions } ?: return null
        val currency = try {
            Currency.getInstance(Locale.Builder().setRegion(normalized).build())
        } catch (_: IllegalArgumentException) {
            null
        }
        return currency?.currencyCode?.takeIf { it != NO_CURRENCY }
    }

    // Changes between two countries (the arrival briefing flags what a traveller must switch)

    /** True when [previousCountryCode] drove on the other side of the road. False when either side is unknown. */
    fun drivingSideChanged(previousCountryCode: String?, countryCode: String?): Boolean {
        val previous = drivingSide(previousCountryCode) ?: return false
        val current = drivingSide(countryCode) ?: return false
        return previous != current
    }

    /** True when plugs, voltage or frequency differ from the previous country. False when either is unverified. */
    fun electricityChanged(previousCountryCode: String?, countryCode: String?): Boolean {
        val previous = electricity(previousCountryCode) ?: return false
        val current = electricity(countryCode) ?: return false
        return current.differsFrom(previous)
    }

    /** True when the previous country used another currency. False when either has none. */
    fun currencyChanged(previousCountryCode: String?, countryCode: String?): Boolean {
        val previous = currencyCode(previousCountryCode) ?: return false
        val current = currencyCode(countryCode) ?: return false
        return previous != current
    }

    // Helpers

    private const val NO_CURRENCY = "XXX"

    private val validRegions: Set<String> = Locale.getISOCountries().toSet()

    private fun normalized(code: String?): String? =
        code?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
}
