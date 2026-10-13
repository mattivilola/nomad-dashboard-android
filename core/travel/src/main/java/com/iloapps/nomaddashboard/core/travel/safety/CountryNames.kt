package com.iloapps.nomaddashboard.core.travel.safety

import java.util.Locale

/** Country names and lists for card wording. */
interface CountryNames {
    /** Name in the user's language for an ISO code, or the code itself when there is no name for it. */
    fun name(code: String): String

    /** "Finland", "Finland and Sweden", "Finland, Sweden, and Norway". */
    fun list(codes: List<String>): String

    companion object {
        /**
         * Names from the JVM's CLDR data for [locale]. The list wording is English, like the rest of
         * the card's text; a screen that localizes it can supply its own [CountryNames].
         */
        fun forLocale(locale: Locale): CountryNames = LocaleCountryNames(locale)
    }
}

private class LocaleCountryNames(private val locale: Locale) : CountryNames {
    private val unknownRegionName: String = displayName("ZZ")

    override fun name(code: String): String {
        val normalized = code.normalizedCountryCode()
        val name = displayName(normalized)
        return if (name.isBlank() || name == normalized || name == unknownRegionName) normalized else name
    }

    override fun list(codes: List<String>): String {
        val names = codes.map(::name)
        return when (names.size) {
            0 -> ""
            1 -> names[0]
            2 -> "${names[0]} and ${names[1]}"
            else -> names.dropLast(1).joinToString(", ") + ", and " + names.last()
        }
    }

    private fun displayName(regionCode: String): String =
        runCatching { Locale.Builder().setRegion(regionCode).build().getDisplayCountry(locale) }.getOrDefault("")
}
