package com.iloapps.nomaddashboard.feature.settings

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

internal data class CountryOption(val code: String, val name: String)

/** The countries a person can pick from, with search that ignores case and diacritics. */
internal object CountryDirectory {
    /** ISO 3166-1 regions with a real name in [locale], sorted by that name. */
    fun all(locale: Locale): List<CountryOption> =
        Locale.getISOCountries()
            .mapNotNull { code ->
                val name = runCatching { Locale.Builder().setRegion(code).build().getDisplayCountry(locale) }.getOrDefault("")
                name.takeIf { it.isNotBlank() && it != code }?.let { CountryOption(code, it) }
            }
            .sortedWith(compareBy(java.text.Collator.getInstance(locale)) { it.name })

    fun matching(options: List<CountryOption>, query: String, excluding: Collection<String> = emptyList()): List<CountryOption> {
        val needle = query.searchKey()
        return options.filter { option ->
            option.code !in excluding &&
                (needle.isEmpty() || option.name.searchKey().contains(needle) || option.code.lowercase() == needle)
        }
    }
}

internal data class TimeZoneOption(val id: String, val title: String, val subtitle: String)

/** The IANA zones a person can choose as their home, with search over the zone id and display name. */
internal object TimeZoneDirectory {
    fun all(locale: Locale, now: Instant = Instant.now()): List<TimeZoneOption> =
        ZoneId.getAvailableZoneIds()
            .filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
            .sorted()
            .map { id -> option(id, locale, now) }

    fun option(id: String, locale: Locale, now: Instant = Instant.now()): TimeZoneOption {
        val zone = runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.of("UTC"))
        val city = id.substringAfterLast('/').replace('_', ' ')
        val region = id.substringBeforeLast('/', "").replace('_', ' ')
        val offset = zone.rules.getOffset(now).id.let { if (it == "Z") "+00:00" else it }
        val zoneName = zone.getDisplayName(TextStyle.FULL, locale)
        return TimeZoneOption(
            id = id,
            title = city,
            subtitle = listOf(region, "UTC$offset", zoneName).filter { it.isNotBlank() }.distinct().joinToString(" · "),
        )
    }

    fun matching(options: List<TimeZoneOption>, query: String): List<TimeZoneOption> {
        val needle = query.searchKey()
        if (needle.isEmpty()) return options
        return options.filter { option ->
            (option.id.replace('_', ' ') + " " + option.subtitle).searchKey().contains(needle)
        }
    }
}

private val combiningMarks = Regex("\\p{M}+")

internal fun String.searchKey(): String =
    Normalizer.normalize(trim(), Normalizer.Form.NFD).replace(combiningMarks, "").lowercase()
