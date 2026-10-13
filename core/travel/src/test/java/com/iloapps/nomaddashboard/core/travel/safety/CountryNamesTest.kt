package com.iloapps.nomaddashboard.core.travel.safety

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

class CountryNamesTest {
    private val english = CountryNames.forLocale(Locale.US)

    @Test
    fun `names are localized to the given locale`() {
        assertThat(english.name("FI")).isEqualTo("Finland")
        assertThat(CountryNames.forLocale(Locale.forLanguageTag("fi-FI")).name("FI")).isEqualTo("Suomi")
    }

    @Test
    fun `codes are normalized before the lookup`() {
        assertThat(english.name(" ma ")).isEqualTo("Morocco")
    }

    @Test
    fun `a code the system cannot name falls back to the normalized code`() {
        assertThat(english.name("ZZ")).isEqualTo("ZZ")
        assertThat(english.name(" zz ")).isEqualTo("ZZ")
        assertThat(english.name("  ")).isEmpty()
    }
}
