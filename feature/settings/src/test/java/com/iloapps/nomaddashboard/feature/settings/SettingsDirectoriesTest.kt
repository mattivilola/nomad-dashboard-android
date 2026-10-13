package com.iloapps.nomaddashboard.feature.settings

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

class SettingsDirectoriesTest {
    private val countries = CountryDirectory.all(Locale.ENGLISH)

    @Test
    fun countries_are_named_and_sorted() {
        assertThat(countries.first { it.code == "FI" }.name).isEqualTo("Finland")
        assertThat(countries.indexOfFirst { it.code == "AF" }).isLessThan(countries.indexOfFirst { it.code == "ZW" })
        assertThat(countries.none { it.name == it.code }).isTrue()
    }

    @Test
    fun country_search_ignores_case_and_diacritics() {
        assertThat(CountryDirectory.matching(countries, "FINL").map { it.code }).contains("FI")
        assertThat(CountryDirectory.matching(countries, "cote d").map { it.code }).contains("CI")
        assertThat(CountryDirectory.matching(countries, "curacao").map { it.code }).contains("CW")
    }

    @Test
    fun country_search_also_matches_the_code() {
        assertThat(CountryDirectory.matching(countries, "fi").map { it.code }).contains("FI")
    }

    @Test
    fun an_empty_search_lists_everything_except_excluded_countries() {
        val all = CountryDirectory.matching(countries, "")
        val withoutFinland = CountryDirectory.matching(countries, "", excluding = listOf("FI"))

        assertThat(all).hasSize(countries.size)
        assertThat(withoutFinland.map { it.code }).doesNotContain("FI")
        assertThat(withoutFinland).hasSize(countries.size - 1)
    }

    @Test
    fun a_search_with_no_match_is_empty() {
        assertThat(CountryDirectory.matching(countries, "zzzzzz")).isEmpty()
    }

    @Test
    fun time_zones_are_region_city_ids_and_searchable() {
        val zones = TimeZoneDirectory.all(Locale.ENGLISH)

        assertThat(zones.map { it.id }).contains("Europe/Helsinki")
        assertThat(zones.none { it.id.startsWith("Etc/") }).isTrue()
        assertThat(TimeZoneDirectory.matching(zones, "helsinki").map { it.id }).containsExactly("Europe/Helsinki")
        assertThat(TimeZoneDirectory.matching(zones, "new york").map { it.id }).contains("America/New_York")
        assertThat(TimeZoneDirectory.matching(zones, "")).hasSize(zones.size)
    }

    @Test
    fun a_zone_option_shows_city_region_and_offset() {
        val option = TimeZoneDirectory.option("America/New_York", Locale.ENGLISH)

        assertThat(option.title).isEqualTo("New York")
        assertThat(option.subtitle).contains("America")
        assertThat(option.subtitle).contains("UTC")
    }

    @Test
    fun an_unknown_zone_id_still_produces_an_option() {
        val option = TimeZoneDirectory.option("Not/AZone", Locale.ENGLISH)

        assertThat(option.id).isEqualTo("Not/AZone")
        assertThat(option.title).isEqualTo("AZone")
    }
}
