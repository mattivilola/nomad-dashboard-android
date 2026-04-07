package com.iloapps.nomaddashboard.core.data.travelalerts

import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CountryNameResolver @Inject constructor() {
    private val locale = Locale.US

    fun primaryName(countryCode: String): String? {
        val normalizedCode = countryCode.uppercase(Locale.US)
        val regionLocale = Locale.Builder().setRegion(normalizedCode).build()
        return regionLocale.getDisplayCountry(locale).takeIf { it.isNotBlank() }
            ?: aliasOverridesByCode[normalizedCode]?.firstOrNull()
    }

    fun candidateNames(countryCode: String): List<String> {
        val normalizedCode = countryCode.uppercase(Locale.US)
        return buildList {
            primaryName(normalizedCode)?.let(::add)
            addAll(aliasOverridesByCode[normalizedCode].orEmpty())
        }.uniqued()
    }

    fun normalized(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase(locale)
            .replace("&", " and ")
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    private companion object {
        val aliasOverridesByCode: Map<String, List<String>> = mapOf(
            "BO" to listOf("Bolivia", "Bolivia Plurinational State of"),
            "BN" to listOf("Brunei", "Brunei Darussalam"),
            "CV" to listOf("Cape Verde", "Cabo Verde"),
            "CI" to listOf("Ivory Coast", "Cote d Ivoire", "Cote d'Ivoire"),
            "CZ" to listOf("Czech Republic", "Czechia"),
            "IR" to listOf("Iran", "Iran Islamic Republic of"),
            "KP" to listOf("North Korea", "Democratic People's Republic of Korea", "Korea Democratic People's Republic of"),
            "KR" to listOf("South Korea", "Republic of Korea", "Korea Republic of"),
            "LA" to listOf("Laos", "Lao People's Democratic Republic"),
            "MD" to listOf("Moldova", "Republic of Moldova"),
            "PS" to listOf("Palestine", "State of Palestine"),
            "RU" to listOf("Russia", "Russian Federation"),
            "SY" to listOf("Syria", "Syrian Arab Republic"),
            "TR" to listOf("Turkey", "Turkiye", "Türkiye"),
            "TW" to listOf("Taiwan", "Taiwan Province of China"),
            "TZ" to listOf("Tanzania", "United Republic of Tanzania"),
            "VE" to listOf("Venezuela", "Venezuela Bolivarian Republic of"),
            "VN" to listOf("Vietnam", "Viet Nam"),
        )
    }
}

internal fun <T> List<T>.uniqued(): List<T> {
    val seen = LinkedHashSet<T>()
    forEach(seen::add)
    return seen.toList()
}
