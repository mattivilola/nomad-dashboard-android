package com.iloapps.nomaddashboard.core.travel.safety

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionKind.Consulate
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionKind.ConsulateGeneral
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionKind.Embassy
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionKind.HonoraryConsulate
import com.iloapps.nomaddashboard.core.travel.safety.MissionFixtures.agadir
import com.iloapps.nomaddashboard.core.travel.safety.MissionFixtures.amman
import com.iloapps.nomaddashboard.core.travel.safety.MissionFixtures.casablanca
import com.iloapps.nomaddashboard.core.travel.safety.MissionFixtures.ids
import com.iloapps.nomaddashboard.core.travel.safety.MissionFixtures.marrakesh
import com.iloapps.nomaddashboard.core.travel.safety.MissionFixtures.mission
import com.iloapps.nomaddashboard.core.travel.safety.MissionFixtures.rabat
import org.junit.Test

class DiplomaticMissionsTest {
    /**
     * Finland has an embassy in Rabat only; Germany has the full range; Sweden and Denmark have
     * an embassy in Rabat; Estonia and the US have missions elsewhere in Morocco or none.
     */
    private val fixture = DiplomaticMissionIndex(
        missions = listOf(
            mission("Q1", "FI", Embassy, "Embassy of Finland, Rabat", rabat),
            mission("Q2", "DE", Embassy, "Embassy of Germany, Rabat", rabat),
            mission("Q3", "DE", ConsulateGeneral, "Consulate General of Germany, Casablanca", casablanca),
            mission("Q4", "DE", HonoraryConsulate, "Honorary Consulate of Germany, Agadir", agadir),
            mission("Q5", "DE", Consulate, "Consulate of Germany, Marrakesh", marrakesh),
            mission("Q6", "SE", Embassy, "Embassy of Sweden, Rabat", rabat),
            mission("Q7", "DK", Embassy, "Embassy of Denmark, Rabat", rabat),
            mission("Q8", "US", Embassy, "Embassy of the United States, Rabat", rabat),
            mission("Q9", "NO", HonoraryConsulate, "Honorary Consulate of Norway, Casablanca", casablanca),
            mission("Q10", "DE", Embassy, "Embassy of Germany, Madrid", MissionCoordinate(40.4168, -3.7038), host = "ES"),
            mission("Q11", "FI", Embassy, "Embassy of Finland, Madrid", MissionCoordinate(40.4168, -3.7038), host = "ES"),
        ),
        source = "fixture",
        generatedAt = "2026-10-12",
    )

    // region Directory

    @Test
    fun missionsAreLookedUpCaseInsensitivelyAndSortedByKind() {
        val germanMissions = DiplomaticMissionDirectory.missions(" de ", "ma", fixture)

        assertThat(germanMissions.map { it.id }).containsExactly("Q2", "Q3", "Q5", "Q4").inOrder()
        assertThat(DiplomaticMissionDirectory.missions("FI", "MA", fixture).map { it.id }).containsExactly("Q1")
        assertThat(DiplomaticMissionDirectory.missions("FI", "ES", fixture).map { it.id }).containsExactly("Q11")
    }

    @Test
    fun unknownCodesReturnNothing() {
        assertThat(DiplomaticMissionDirectory.missions("ZZ", "MA", fixture)).isEmpty()
        assertThat(DiplomaticMissionDirectory.missions("FI", "ZZ", fixture)).isEmpty()
        assertThat(DiplomaticMissionDirectory.missions("", "", fixture)).isEmpty()
        assertThat(DiplomaticMissionDirectory.nearest(listOf("ZZ"), "MA", null, 3, fixture)).isEmpty()
        assertThat(DiplomaticMissionDirectory.nearest(emptyList(), "MA", null, 3, fixture)).isEmpty()
    }

    @Test
    fun withoutACoordinateKindDecidesAndHonoraryIsLast() {
        val matches = DiplomaticMissionDirectory.nearest(listOf("DE"), "MA", null, 10, fixture)

        assertThat(ids(matches)).containsExactly("Q2", "Q3", "Q5", "Q4").inOrder()
        assertThat(matches.all { it.distanceKilometers == null }).isTrue()
    }

    @Test
    fun limitCapsTheResultsAndNonPositiveLimitsReturnNothing() {
        assertThat(ids(DiplomaticMissionDirectory.nearest(listOf("DE"), "MA", null, 2, fixture)))
            .containsExactly("Q2", "Q3").inOrder()
        assertThat(DiplomaticMissionDirectory.nearest(listOf("DE"), "MA", null, 0, fixture)).isEmpty()
        assertThat(DiplomaticMissionDirectory.nearest(listOf("DE"), "MA", null, -1, fixture)).isEmpty()
    }

    @Test
    fun withACoordinateTheNearestCareerMissionComesFirst() {
        // In Casablanca the consulate-general beats the embassy 87 km away.
        val inCasablanca = DiplomaticMissionDirectory.nearest(listOf("DE"), "MA", casablanca, 10, fixture)
        assertThat(ids(inCasablanca)).containsExactly("Q3", "Q2", "Q5", "Q4").inOrder()
        assertThat(inCasablanca.first().distanceKilometers!!).isLessThan(1.0)

        // Marrakesh: the consulate there is closest, then Casablanca, then Rabat.
        val inMarrakesh = DiplomaticMissionDirectory.nearest(listOf("DE"), "MA", marrakesh, 10, fixture)
        assertThat(ids(inMarrakesh)).containsExactly("Q5", "Q3", "Q2", "Q4").inOrder()
    }

    @Test
    fun honoraryConsulatesNeverOutrankACareerMissionEvenWhenCloser() {
        // Standing in Agadir, the honorary consulate is ~0 km away but the embassy still comes first.
        val matches = DiplomaticMissionDirectory.nearest(listOf("DE"), "MA", agadir, 10, fixture)

        assertThat(matches.last().mission.id).isEqualTo("Q4")
        assertThat(matches.last().distanceKilometers!!).isLessThan(1.0)
        assertThat(matches.first().mission.id).isEqualTo("Q5")
    }

    @Test
    fun equallyNearMissionsAreTiedByKind() {
        // The Danish, Finnish and Swedish embassies all share Rabat: same distance, ordered by name.
        val nordic = DiplomaticMissionDirectory.nearest(listOf("SE", "DK", "FI"), "MA", rabat, 10, fixture)
        assertThat(ids(nordic)).containsExactly("Q7", "Q1", "Q6").inOrder()

        // An embassy and a consulate-general a few kilometres apart count as equally near; the embassy wins.
        val index = DiplomaticMissionIndex(
            listOf(
                mission("A", "FR", ConsulateGeneral, "CG", MissionCoordinate(34.0200, -6.8400)),
                mission("B", "FR", Embassy, "Embassy", MissionCoordinate(34.0500, -6.8400)),
            ),
        )
        val matches = DiplomaticMissionDirectory.nearest(listOf("FR"), "MA", rabat, 2, index)
        assertThat(ids(matches)).containsExactly("B", "A").inOrder()
    }

    @Test
    fun distancesAreGreatCircleKilometres() {
        val matches = DiplomaticMissionDirectory.nearest(listOf("DE"), "MA", rabat, 10, fixture)
        val casablancaDistance = matches.first { it.mission.id == "Q3" }.distanceKilometers!!

        // Rabat to Casablanca is about 87 km as the crow flies.
        assertThat(casablancaDistance).isWithin(3.0).of(87.0)
        assertThat(matches.first { it.mission.id == "Q2" }.distanceKilometers!!).isLessThan(0.001)
    }

    @Test
    fun missionsListedForSeveralCitizenshipsAppearOnce() {
        val index = DiplomaticMissionIndex(listOf(mission("X", "FI", Embassy, "Shared", rabat)))
        assertThat(DiplomaticMissionDirectory.nearest(listOf("FI", "fi", "FI"), "MA", null, 5, index)).hasSize(1)
    }

    @Test
    fun euMemberStatesAreTheTwentySevenUniqueCodes() {
        val states = DiplomaticMissionDirectory.euMemberStates
        assertThat(states).hasSize(27)
        assertThat(states.toSet()).hasSize(27)
        assertThat(states).containsAtLeast("FI", "IE", "HR")
        assertThat(states).containsNoneOf("GB", "NO", "CH")
        assertThat(DiplomaticMissionDirectory.nordicStates).containsExactly("DK", "FI", "IS", "NO", "SE").inOrder()
    }

    @Test
    fun fallbackStatesForEuNordicAndOtherCitizens() {
        val finland = DiplomaticMissionDirectory.fallbackStates("FI")
        assertThat(finland).containsAtLeast("DE", "SE", "NO", "IS")
        assertThat(finland).doesNotContain("FI")
        assertThat(finland).hasSize(28) // 26 other EU states + Norway + Iceland

        val germany = DiplomaticMissionDirectory.fallbackStates("de")
        assertThat(germany).hasSize(26)
        assertThat(germany).doesNotContain("NO")
        assertThat(germany).doesNotContain("DE")

        assertThat(DiplomaticMissionDirectory.fallbackStates("NO")).containsExactly("DK", "FI", "IS", "SE").inOrder()
        assertThat(DiplomaticMissionDirectory.fallbackStates("IS")).containsExactly("DK", "FI", "NO", "SE").inOrder()
        assertThat(DiplomaticMissionDirectory.fallbackStates("US")).isEmpty()
        assertThat(DiplomaticMissionDirectory.fallbackStates("GB")).isEmpty()
        assertThat(DiplomaticMissionDirectory.fallbackStates("ZZ")).isEmpty()
        assertThat(DiplomaticMissionDirectory.fallbackStates("")).isEmpty()
    }

    // endregion

    // region Lookup

    @Test
    fun atHomeYieldsNothing() {
        val lookup = EmbassyLookup.find(listOf("FI"), "FI", null, index = fixture)

        assertThat(lookup.isEmpty).isTrue()
        assertThat(lookup.fallbackReason).isNull()

        assertThat(EmbassyLookup.find(listOf("fi"), " fi ", null, index = fixture).isEmpty).isTrue()
    }

    @Test
    fun ownMissionsAreListedAndSuppressTheFallback() {
        val lookup = EmbassyLookup.find(listOf("FI"), "MA", rabat, index = fixture)

        assertThat(ids(lookup.ownMatches)).containsExactly("Q1")
        assertThat(lookup.fallbackMatches).isEmpty()
        assertThat(lookup.fallbackReason).isNull()
        assertThat(lookup.hostCountry).isEqualTo("MA")
    }

    @Test
    fun finnWithoutAMissionGetsEuAndNordicStatesRankedTogether() {
        val index = DiplomaticMissionIndex(
            listOf(
                mission("Q2", "DE", Embassy, "Embassy of Germany, Rabat", rabat),
                mission("Q6", "SE", Embassy, "Embassy of Sweden, Rabat", rabat),
                mission("Q12", "FR", ConsulateGeneral, "Consulate General of France, Casablanca", casablanca),
            ),
        )
        val lookup = EmbassyLookup.find(listOf("FI"), "MA", rabat, index = index)

        assertThat(lookup.ownMatches).isEmpty()
        // Sweden and Germany are equally near; the Nordic arrangement is more specific, so Sweden leads.
        assertThat(ids(lookup.fallbackMatches)).containsExactly("Q6", "Q2", "Q12").inOrder()
        assertThat(lookup.fallbackReason).isEqualTo(EmbassyLookup.FallbackReason.NordicCooperation)
        assertThat(lookup.fallbackReasonFor(lookup.fallbackMatches[0].mission))
            .isEqualTo(EmbassyLookup.FallbackReason.NordicCooperation)
        assertThat(lookup.fallbackReasonFor(lookup.fallbackMatches[1].mission))
            .isEqualTo(EmbassyLookup.FallbackReason.EuConsularProtection)
    }

    @Test
    fun nordicReasonIsReportedWhenTheBestMatchIsNordic() {
        val index = DiplomaticMissionIndex(
            listOf(
                mission("Q6", "SE", Embassy, "Embassy of Sweden, Rabat", rabat),
                mission("Q2", "DE", Embassy, "Embassy of Germany, Casablanca", casablanca),
            ),
        )
        val lookup = EmbassyLookup.find(listOf("FI"), "MA", rabat, index = index)

        assertThat(ids(lookup.fallbackMatches)).containsExactly("Q6", "Q2").inOrder()
        assertThat(lookup.fallbackReason).isEqualTo(EmbassyLookup.FallbackReason.NordicCooperation)
    }

    @Test
    fun norwegianCitizensOnlyFallBackToNordicStates() {
        val index = DiplomaticMissionIndex(
            listOf(
                mission("Q2", "DE", Embassy, "Embassy of Germany, Rabat", rabat),
                mission("Q6", "SE", Embassy, "Embassy of Sweden, Rabat", rabat),
            ),
        )
        val lookup = EmbassyLookup.find(listOf("NO"), "MA", null, index = index)

        assertThat(ids(lookup.fallbackMatches)).containsExactly("Q6")
        assertThat(lookup.fallbackReason).isEqualTo(EmbassyLookup.FallbackReason.NordicCooperation)
    }

    @Test
    fun estonianCitizenGetsEuProtectionAndNeverTheHostOrOwnState() {
        val lookup = EmbassyLookup.find(listOf("EE"), "MA", rabat, limit = 2, index = fixture)

        assertThat(lookup.ownMatches).isEmpty()
        assertThat(lookup.fallbackMatches).hasSize(2)
        assertThat(lookup.fallbackReason).isEqualTo(EmbassyLookup.FallbackReason.EuConsularProtection)
        // Non-EU states with missions in the fixture (US, NO) are never offered.
        assertThat(lookup.fallbackMatches.none { it.mission.sendingCountry in listOf("US", "NO") }).isTrue()
    }

    @Test
    fun nonEuCitizensWithoutAMissionGetNothing() {
        // The fixture has a US embassy but a Canadian has no mission and no fallback arrangement.
        val lookup = EmbassyLookup.find(listOf("CA"), "MA", rabat, index = fixture)

        assertThat(lookup.isEmpty).isTrue()
        assertThat(lookup.fallbackReason).isNull()
    }

    @Test
    fun unknownHostOrEmptyCitizenshipsYieldNothing() {
        assertThat(EmbassyLookup.find(listOf("FI"), null, null, index = fixture).isEmpty).isTrue()
        assertThat(EmbassyLookup.find(listOf("FI"), "", null, index = fixture).isEmpty).isTrue()
        assertThat(EmbassyLookup.find(emptyList(), "MA", null, index = fixture).isEmpty).isTrue()
        assertThat(EmbassyLookup.find(listOf("", " "), "MA", null, index = fixture).isEmpty).isTrue()
        assertThat(EmbassyLookup.find(listOf("FI"), "ZZ", null, index = fixture).isEmpty).isTrue()
    }

    @Test
    fun dualCitizenAbroadOnOnePassportStillGetsThatPassportsMissions() {
        // FI + DE in Germany: at home for DE, abroad for FI.
        val index = DiplomaticMissionIndex(
            listOf(mission("Q20", "FI", Embassy, "Embassy of Finland, Berlin", MissionCoordinate(52.52, 13.405), host = "DE")),
        )
        val lookup = EmbassyLookup.find(listOf("FI", "DE"), "DE", null, index = index)

        assertThat(ids(lookup.ownMatches)).containsExactly("Q20")
        assertThat(lookup.fallbackMatches).isEmpty()
    }

    @Test
    fun dualCitizenWithOneOwnMissionSkipsTheFallback() {
        val lookup = EmbassyLookup.find(listOf("EE", "DE"), "MA", rabat, index = fixture)

        assertThat(ids(lookup.ownMatches)).contains("Q2")
        assertThat(lookup.fallbackMatches).isEmpty()
        assertThat(lookup.fallbackReason).isNull()
    }

    // endregion

    // region Model and bundled resource

    @Test
    fun missionDecodesCompactKeysAndDefaultsOptionalFields() {
        val index = DiplomaticMissionIndex.parse(
            """{"generatedAt":"2026-10-12","source":"Wikidata (CC0)","missions":[
            {"i":"Q1","s":"FI","h":"MA","k":"embassy","n":"Embassy","la":34.02,"lo":-6.84},
            {"i":"Q2","s":"FI","h":"MA","k":"honoraryConsulate","n":"Honorary","c":"Rabat","la":1,"lo":2,
             "a":"Street 1","w":"https://example.org","x":true}
            ]}""",
        )
        val (plain, full) = index.missions

        assertThat(plain.city).isNull()
        assertThat(plain.website).isNull()
        assertThat(plain.isApproximateLocation).isFalse()
        assertThat(full).isEqualTo(
            DiplomaticMission(
                id = "Q2",
                sendingCountry = "FI",
                hostCountry = "MA",
                kind = HonoraryConsulate,
                name = "Honorary",
                city = "Rabat",
                latitude = 1.0,
                longitude = 2.0,
                address = "Street 1",
                website = "https://example.org",
                isApproximateLocation = true,
            ),
        )
        assertThat(index.generatedAt).isEqualTo("2026-10-12")
    }

    @Test
    fun unreadableDatasetYieldsAnEmptyIndex() {
        assertThat(DiplomaticMissionIndex.parse("not json").missions).isEmpty()
        assertThat(DiplomaticMissionIndex.parse("""{"generatedAt":"x","missions":[{"i":"Q1"}]}""").missions).isEmpty()
    }

    @Test
    fun kindTitlesAreUserFacing() {
        assertThat(Embassy.title).isEqualTo("Embassy")
        assertThat(ConsulateGeneral.title).isEqualTo("Consulate-general")
        assertThat(Consulate.title).isEqualTo("Consulate")
        assertThat(HonoraryConsulate.title).isEqualTo("Honorary consulate")
        assertThat(DiplomaticMissionKind.Other.title).isEqualTo("Mission")
    }

    @Test
    fun bundledDatasetDecodesAndIsSane() {
        val index = DiplomaticMissionIndex.bundled

        assertThat(index.source).isEqualTo(DiplomaticMissionDataset.WikidataSource)
        assertThat(index.generatedAt).matches("\\d{4}-\\d{2}-\\d{2}")
        assertThat(index.missions).hasSize(10_181)

        // Spot checks that must hold for any acceptable refresh of the dataset.
        assertThat(DiplomaticMissionDirectory.missions("FI", "MA", index).any { it.kind == Embassy }).isTrue()
        assertThat(DiplomaticMissionDirectory.missions("US", "MA", index).any { it.kind == Embassy }).isTrue()
        assertThat(DiplomaticMissionDirectory.missions("GB", "MA", index)).isNotEmpty()
        assertThat(DiplomaticMissionDirectory.missions("DE", "ES", index).any { it.kind == Embassy }).isTrue()
        assertThat(DiplomaticMissionDirectory.missions("NL", "MA", index)).isNotEmpty()
        assertThat(DiplomaticMissionDirectory.missions("DK", "MA", index)).isNotEmpty()
    }

    @Test
    fun bundledMissionsAreWellFormed() {
        val seenIds = mutableSetOf<String>()

        for (mission in DiplomaticMissionIndex.bundled.missions) {
            assertWithMessage("duplicate id ${mission.id}").that(seenIds.add(mission.id)).isTrue()
            assertThat(mission.sendingCountry).matches("[A-Z]{2}")
            assertThat(mission.hostCountry).matches("[A-Z]{2}")
            assertThat(mission.sendingCountry).isNotEqualTo(mission.hostCountry)
            assertThat(mission.latitude in -90.0..90.0).isTrue()
            assertThat(mission.longitude in -180.0..180.0).isTrue()
            assertThat(mission.name).isNotEmpty()
            mission.website?.let { assertThat(it.substringBefore("://")).isAnyOf("http", "https") }
        }
    }

    // endregion

    // region Fallback ranking

    /** Alphabetically Austria, Belgium and Bulgaria come first; Sweden, Norway and Denmark are as near. */
    private val ammanFixture = DiplomaticMissionIndex(
        listOf(
            mission("AT", "AT", Embassy, "Embassy of Austria, Amman", amman, host = "JO"),
            mission("BE", "BE", Embassy, "Embassy of Belgium, Amman", amman, host = "JO"),
            mission("BG", "BG", Embassy, "Embassy of Bulgaria, Amman", amman, host = "JO"),
            mission("DE", "DE", ConsulateGeneral, "Consulate General of Germany, Amman", amman, host = "JO"),
            mission("SE", "SE", Embassy, "Embassy of Sweden, Amman", amman, host = "JO"),
            mission("NO", "NO", Embassy, "Embassy of Norway, Amman", amman, host = "JO"),
            mission("DK", "DK", Embassy, "Embassy of Denmark, Amman", amman, host = "JO"),
            mission("SE-AQ", "SE", HonoraryConsulate, "Honorary Consulate of Sweden, Aqaba", MissionCoordinate(29.53, 35.0), host = "JO"),
        ),
    )

    @Test
    fun nordicMissionsOutrankEuMissionsForANordicCitizenAtTheSameDistance() {
        val lookup = EmbassyLookup.find(listOf("FI"), "JO", amman, index = ammanFixture)

        assertThat(ids(lookup.fallbackMatches)).containsExactly("DK", "NO", "SE").inOrder()
        assertThat(lookup.fallbackReason).isEqualTo(EmbassyLookup.FallbackReason.NordicCooperation)
    }

    @Test
    fun nordicPreferenceAlsoAppliesWithoutACoordinate() {
        val lookup = EmbassyLookup.find(listOf("FI"), "JO", null, limit = 4, index = ammanFixture)

        assertThat(ids(lookup.fallbackMatches)).containsExactly("DK", "NO", "SE", "AT").inOrder()
    }

    @Test
    fun aMuchNearerEuMissionStillBeatsADistantNordicOne() {
        val index = DiplomaticMissionIndex(
            listOf(
                mission("AT-NEAR", "AT", Embassy, "Embassy of Austria, Amman", amman, host = "JO"),
                mission("SE-FAR", "SE", Embassy, "Embassy of Sweden, Aqaba", MissionCoordinate(29.53, 35.0), host = "JO"),
            ),
        )
        val lookup = EmbassyLookup.find(listOf("FI"), "JO", amman, index = index)

        assertThat(ids(lookup.fallbackMatches)).containsExactly("AT-NEAR", "SE-FAR").inOrder()
    }

    @Test
    fun anEuOnlyCitizenKeepsTheKindThenNameOrderingWithinABucket() {
        val lookup = EmbassyLookup.find(listOf("FR"), "JO", amman, limit = 6, index = ammanFixture)

        assertThat(ids(lookup.fallbackMatches)).containsExactly("AT", "BE", "BG", "DK", "SE", "DE").inOrder()
        assertThat(lookup.fallbackReason).isEqualTo(EmbassyLookup.FallbackReason.EuConsularProtection)
    }

    // endregion
}
