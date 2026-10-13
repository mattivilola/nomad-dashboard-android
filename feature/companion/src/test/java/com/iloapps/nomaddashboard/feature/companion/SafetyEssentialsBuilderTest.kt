package com.iloapps.nomaddashboard.feature.companion

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.travel.safety.CountryNames
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMission
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionIndex
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionKind
import com.iloapps.nomaddashboard.core.travel.safety.EmbassyOverviewBuilder
import com.iloapps.nomaddashboard.core.travel.safety.SafetyLocation
import java.util.Locale
import org.junit.Test

class SafetyEssentialsBuilderTest {
    private val index = DiplomaticMissionIndex(
        missions = listOf(
            DiplomaticMission(
                id = "FI-RAB", sendingCountry = "FI", hostCountry = "MA", kind = DiplomaticMissionKind.Embassy,
                name = "Embassy of Finland", city = "Rabat", latitude = 34.02, longitude = -6.84,
            ),
        ),
        generatedAt = "2026-10-05",
    )
    private val builder = SafetyEssentialsBuilder(
        embassyOverviewBuilder = EmbassyOverviewBuilder(Locale.US, indexProvider = { index }),
        countryNames = CountryNames.forLocale(Locale.US),
    )

    private fun location(country: String?) = SafetyLocation.of(TravelContextSnapshot(deviceCountryCode = country), null)

    @Test
    fun `abroad shows the local numbers the nearest embassy and the consular line`() {
        val essentials = builder.build(listOf("FI"), location("MA"))

        assertThat(essentials.countryName).isEqualTo("Morocco")
        assertThat(essentials.emergencyNumbers!!.headline).isEqualTo("Emergency 19")
        assertThat(essentials.emergencyNumbers!!.othersText).isEqualTo("Ambulance/Fire 15 · Gendarmerie 177")
        assertThat(essentials.embassy!!.nearestMission).isEqualTo("Embassy of Finland, Rabat")
        assertThat(essentials.embassy!!.hotlines.map { it.phone }).containsExactly("+358 9 1605 5555")
        assertThat(essentials.isEmpty).isFalse()
    }

    @Test
    fun `without a citizenship only the emergency numbers remain`() {
        val essentials = builder.build(emptyList(), location("MA"))

        assertThat(essentials.emergencyNumbers).isNotNull()
        assertThat(essentials.embassy).isNull()
    }

    @Test
    fun `at home the consular line stays and no mission is named`() {
        val essentials = builder.build(listOf("FI"), location("FI"))

        assertThat(essentials.emergencyNumbers!!.headline).isEqualTo("Emergency 112")
        assertThat(essentials.embassy!!.nearestMission).isNull()
        assertThat(essentials.embassy!!.hotlines).hasSize(1)
    }

    @Test
    fun `an unknown country shows no numbers rather than a guess`() {
        val essentials = builder.build(listOf("FI"), location(null))

        assertThat(essentials.emergencyNumbers).isNull()
        assertThat(essentials.countryName).isNull()
        assertThat(essentials.embassy!!.hotlines).hasSize(1)
    }

    @Test
    fun `a country without verified numbers shows none`() {
        val essentials = builder.build(emptyList(), location("LA"))

        assertThat(essentials.isEmpty).isTrue()
    }

    @Test
    fun `without a citizenship or a country there is nothing to show`() {
        assertThat(builder.build(emptyList(), location(null)).isEmpty).isTrue()
    }
}
