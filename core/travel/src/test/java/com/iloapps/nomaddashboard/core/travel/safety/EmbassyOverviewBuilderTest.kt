package com.iloapps.nomaddashboard.core.travel.safety

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionKind.Consulate
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionKind.ConsulateGeneral
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionKind.Embassy
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionKind.HonoraryConsulate
import com.iloapps.nomaddashboard.core.travel.safety.EmbassyOverview.BadgeIcon
import com.iloapps.nomaddashboard.core.travel.safety.EmbassyOverview.BadgeTone
import com.iloapps.nomaddashboard.core.travel.safety.EmbassyOverview.State
import java.util.Locale
import org.junit.Test

class EmbassyOverviewBuilderTest {
    private val locale = Locale.US
    private val rabat = MissionCoordinate(34.020, -6.841)

    private fun mission(
        id: String,
        sending: String,
        host: String = "MA",
        kind: DiplomaticMissionKind = Embassy,
        name: String? = null,
        city: String? = "Rabat",
        latitude: Double = 34.02,
        longitude: Double = -6.84,
        address: String? = null,
        website: String? = null,
        approximate: Boolean = true,
    ) = DiplomaticMission(
        id = id,
        sendingCountry = sending,
        hostCountry = host,
        kind = kind,
        name = name ?: "Embassy of $sending",
        city = city,
        latitude = latitude,
        longitude = longitude,
        address = address,
        website = website,
        isApproximateLocation = approximate,
    )

    private val index = DiplomaticMissionIndex(
        missions = listOf(
            mission(
                "FI-RAB", "FI", name = "Embassy of Finland in Rabat", latitude = 34.05, longitude = -6.84,
                address = "Rue 1, Rabat", website = "https://finlandabroad.fi/rabat", approximate = false,
            ),
            mission(
                "FI-CAS", "FI", kind = HonoraryConsulate, name = "Honorary Consulate of Finland in Casablanca",
                city = "Casablanca", latitude = 33.57, longitude = -7.59,
            ),
            mission("SE-RAB", "SE"),
            mission("DE-RAB", "DE"),
            mission("DE-CAS", "DE", kind = ConsulateGeneral, city = "Casablanca", latitude = 33.57, longitude = -7.59),
            mission("FR-RAB", "FR"),
            mission("FR-CAS", "FR", kind = ConsulateGeneral, city = "Casablanca", latitude = 33.57, longitude = -7.59),
            mission("FR-TNG", "FR", kind = Consulate, city = "Tangier", latitude = 35.77, longitude = -5.80),
            mission("FR-AGA", "FR", kind = Consulate, city = "Agadir", latitude = 30.42, longitude = -9.60),
            mission("SE-ES", "SE", host = "ES", city = "Madrid", latitude = 40.4, longitude = -3.7),
            mission("DE-AU", "DE", host = "AU", city = "Canberra", latitude = -35.3, longitude = 149.1),
        ),
        source = "Wikidata (CC0)",
        generatedAt = "2026-10-05",
    )

    private fun builderFor(index: DiplomaticMissionIndex = this.index) =
        EmbassyOverviewBuilder(locale, indexProvider = { index })

    private fun overview(
        citizenships: List<String>,
        host: String?,
        coordinate: MissionCoordinate? = rabat,
    ): EmbassyOverview = builderFor().build(citizenships, host, coordinate)

    // region Own missions

    @Test
    fun finnInMoroccoSeesTheirEmbassyFirstWithKindCityAndDistance() {
        val card = overview(listOf("FI"), "MA")

        assertThat(card.state).isEqualTo(State.Abroad)
        assertThat(card.subtitle).isEqualTo("Finland in Morocco")
        assertThat(card.badge.title).isEqualTo("Listed")
        assertThat(card.missions.map { it.id }).containsExactly("FI-RAB", "FI-CAS").inOrder()

        val embassy = card.missions.first()
        assertThat(embassy.name).isEqualTo("Embassy of Finland in Rabat")
        assertThat(embassy.kindTitle).isEqualTo("Embassy")
        assertThat(embassy.locationText).isEqualTo("Rabat")
        assertThat(embassy.distanceText).isEqualTo("3.3 km")
        assertThat(embassy.address).isEqualTo("Rue 1, Rabat")
        assertThat(embassy.websiteUrl).isEqualTo("https://finlandabroad.fi/rabat")
        assertThat(embassy.mapsUri).isNotEmpty()
        assertThat(embassy.accessibilityLabel).isEqualTo("Embassy of Finland in Rabat, Embassy, Rabat, 3.3 km")
        assertThat(card.fallbackMissions).isEmpty()
        assertThat(card.fallbackNotice).isNull()
    }

    @Test
    fun approximateLocationsShowRoundedDistanceWithATilde() {
        val card = overview(listOf("FI"), "MA")

        assertThat(card.missions.last().distanceText).isEqualTo("~85 km")
        assertThat(EmbassyOverviewBuilder.distanceText(4.2, isApproximate = true)).isEqualTo("~4 km")
        assertThat(EmbassyOverviewBuilder.distanceText(0.2, isApproximate = true)).isEqualTo("~1 km")
        assertThat(EmbassyOverviewBuilder.distanceText(4.2, isApproximate = false)).isEqualTo("4.2 km")
        assertThat(EmbassyOverviewBuilder.distanceText(42.4, isApproximate = false)).isEqualTo("42 km")
        assertThat(EmbassyOverviewBuilder.distanceText(null, isApproximate = false)).isNull()
        assertThat(EmbassyOverviewBuilder.distanceText(Double.NaN, isApproximate = true)).isNull()
        assertThat(EmbassyOverviewBuilder.distanceText(-1.0, isApproximate = false)).isNull()
    }

    @Test
    fun distanceIsOmittedWithoutADeviceCoordinate() {
        val card = overview(listOf("FI"), "MA", coordinate = null)

        assertThat(card.missions).isNotEmpty()
        assertThat(card.missions.all { it.distanceText == null }).isTrue()
        assertThat(card.summary).isEqualTo("+358 9 1605 5555")
        assertThat(card.summaryHelp).isEqualTo("Embassy of Finland in Rabat · +358 9 1605 5555")
    }

    @Test
    fun missionsAreCappedAtThree() {
        val card = overview(listOf("FR"), "MA")

        assertThat(card.missions).hasSize(3)
        assertThat(card.missions.first().id).isEqualTo("FR-RAB")
    }

    @Test
    fun dualCitizensSeeBothCountriesInTheSubtitleAndBothHotlines() {
        val card = overview(listOf("FI", "SE"), "MA")

        assertThat(card.subtitle).isEqualTo("Finland and Sweden in Morocco")
        assertThat(card.hotlines.map { it.id }).containsExactly("FI", "SE").inOrder()
        assertThat(card.hotlines.first().title).isEqualTo("Finland · Ministry for Foreign Affairs")
        assertThat(card.links.map { it.id }).containsExactly("FI", "SE").inOrder()
    }

    @Test
    fun dualCitizenshipSkipsTheCountryTheUserIsHomeIn() {
        val card = overview(listOf("FI", "SE"), "FI", coordinate = null)

        assertThat(card.state).isEqualTo(State.Abroad)
        assertThat(card.subtitle).isEqualTo("Sweden in Finland")
        assertThat(card.hotlines.map { it.id }).containsExactly("FI", "SE").inOrder()
        assertThat(card.links.map { it.id }).containsExactly("SE")
    }

    @Test
    fun citizenshipsAreSanitizedAndCappedAtThree() {
        val card = overview(listOf(" fi ", "FI", "x", "SE", "NO", "DE"), "MA", coordinate = null)

        assertThat(card.hotlines.map { it.id }).containsExactly("FI", "SE", "NO").inOrder()
        assertThat(card.subtitle).isEqualTo("Finland, Sweden, and Norway in Morocco")
    }

    // endregion

    // region Hotline

    @Test
    fun hotlineUsesTheMinistryShortNameAndItsOwnWording() {
        val hotline = overview(listOf("FI"), "MA").hotlines.first()

        assertThat(hotline.title).isEqualTo("Ministry for Foreign Affairs")
        assertThat(hotline.detail).isEqualTo("24/7 consular line · consular emergencies only, no visa enquiries")
        assertThat(hotline.phone).isEqualTo("+358 9 1605 5555")
        assertThat(hotline.help).contains("Ministry for Foreign Affairs of Finland")
    }

    @Test
    fun hotlineNeverClaimsAllDayServiceWhereTheNoteSaysOtherwise() {
        val describe = EmbassyOverviewBuilder::lineDescription
        assertThat(describe("Consular Duty Centre · outside embassy hours")).isEqualTo("Consular Duty Centre · outside embassy hours")
        assertThat(describe("Emergency line · outside office hours")).isEqualTo("Emergency line · outside office hours")
        assertThat(describe("24/7")).isEqualTo("24/7 consular line")
        assertThat(describe("24/7 · Helpline FDFA")).isEqualTo("24/7 consular line · Helpline FDFA")
        assertThat(describe(null)).isEqualTo("Consular emergency line")
        assertThat(describe("  ")).isEqualTo("Consular emergency line")
    }

    @Test
    fun everyBundledMinistryGetsAUsableShortName() {
        for (code in ConsularContactsDirectory.citizenshipCodes) {
            val ministry = ConsularContactsDirectory.contactsFor(code)!!.ministryName
            val shortName = EmbassyOverviewBuilder.shortMinistryName(ministry)

            assertThat(shortName).isNotEmpty()
            assertThat(shortName.endsWith(" of")).isFalse()
            assertThat(ministry).startsWith(shortName)
        }
        assertThat(ConsularContactsDirectory.citizenshipCodes.size).isGreaterThan(20)
    }

    @Test
    fun shortMinistryNamesDropOnlyTheCountry() {
        val shorten = EmbassyOverviewBuilder::shortMinistryName
        assertThat(shorten("Ministry for Foreign Affairs of Finland")).isEqualTo("Ministry for Foreign Affairs")
        assertThat(shorten("Ministry of Foreign Affairs of the Czech Republic")).isEqualTo("Ministry of Foreign Affairs")
        assertThat(shorten("Federal Foreign Office of Germany")).isEqualTo("Federal Foreign Office")
        assertThat(shorten("Ministry of Foreign Affairs, European Union and Cooperation of Spain"))
            .isEqualTo("Ministry of Foreign Affairs, European Union and Cooperation")
        assertThat(shorten("U.S. Department of State")).isEqualTo("U.S. Department of State")
        assertThat(shorten("Global Affairs Canada")).isEqualTo("Global Affairs Canada")
    }

    @Test
    fun citizenshipWithoutBundledContactsHasNoHotlineButStillGetsMissions() {
        val card = overview(listOf("DE"), "AU", coordinate = null)
        // Germany has contacts; Brazil has none.
        val brazil = overview(listOf("BR"), "AU", coordinate = null)

        assertThat(card.hotlines).hasSize(1)
        assertThat(brazil.hotlines).isEmpty()
        assertThat(brazil.links).isEmpty()
    }

    // endregion

    // region Fallback

    @Test
    fun nordicCitizenWithoutOwnMissionGetsTheNordicNoticeAndSendingCountryNames() {
        val card = overview(listOf("FI"), "ES", coordinate = null)

        assertThat(card.missions).isEmpty()
        assertThat(card.fallbackNotice).isEqualTo(
            "Finland has no mission listed in Spain. " +
                "Nearest known Nordic missions help citizens of other Nordic countries when theirs isn't represented.",
        )
        val row = card.fallbackMissions.first()
        assertThat(row.locationText).isEqualTo("Sweden · Madrid")
        assertThat(row.reasonCaption).isNull()
        assertThat(card.badge.title).isEqualTo("Nordic help")
        assertThat(card.summary).isEqualTo("+358 9 1605 5555")
    }

    @Test
    fun euCitizenGetsTheEuConsularProtectionNotice() {
        val card = overview(listOf("FR"), "AU", coordinate = null)

        assertThat(card.fallbackNotice).isEqualTo(
            "France has no mission listed in Australia. Nearest known EU missions can help under EU consular protection.",
        )
        assertThat(card.fallbackMissions.map { it.id }).containsExactly("DE-AU")
        assertThat(card.badge.title).isEqualTo("EU help")
        assertThat(card.summary).isEqualTo("+33 1 53 59 11 00")
    }

    @Test
    fun aFallbackRowWithADifferentArrangementThanTheNoticeSaysSo() {
        // Finland has no mission in Morocco in this dataset variant: Sweden (Nordic) and Germany (EU) can help.
        val variant = DiplomaticMissionIndex(
            listOf(mission("SE-RAB", "SE"), mission("DE-RAB", "DE", kind = ConsulateGeneral)),
        )
        val card = builderFor(variant).build(listOf("FI"), "MA", null)

        assertThat(card.fallbackNotice).contains("Nordic")
        val captions = card.fallbackMissions.associate { it.id to it.reasonCaption }
        assertThat(captions["SE-RAB"]).isNull()
        assertThat(captions["DE-RAB"]).isEqualTo("Under EU consular protection")
    }

    @Test
    fun noMissionAtAllSaysSoAndPointsToTheOfficialDirectory() {
        val card = overview(listOf("FI"), "ZZ", coordinate = null)

        assertThat(card.missions).isEmpty()
        assertThat(card.fallbackMissions).isEmpty()
        assertThat(card.noMissionMessage).isEqualTo(
            "Finland has no mission listed in ZZ, and no EU or Nordic alternative is known. " +
                "Check the official missions directory.",
        )
        assertThat(card.badge.title).isEqualTo("Not listed")
        assertThat(card.badge.icon).isEqualTo(BadgeIcon.MapPinOff)
        assertThat(card.summary).isEqualTo("+358 9 1605 5555")
    }

    @Test
    fun nonEuCitizenWithoutAMissionGetsNoFallback() {
        val card = overview(listOf("US"), "AU", coordinate = null)

        assertThat(card.fallbackNotice).isNull()
        assertThat(card.fallbackMissions).isEmpty()
        assertThat(card.noMissionMessage).isNotNull()
    }

    @Test
    fun severalCitizenshipsWithoutAMissionUseThePluralSentence() {
        val card = overview(listOf("US", "CA"), "AU", coordinate = null)

        assertThat(card.noMissionMessage).startsWith("United States and Canada have no mission listed in Australia")
    }

    // endregion

    // region States

    @Test
    fun atHomeShowsOneLineAndKeepsTheHotline() {
        val card = overview(listOf("FI"), "FI")

        assertThat(card.state).isEqualTo(State.AtHome)
        assertThat(card.atHomeMessage).isEqualTo("You're home in Finland — embassy details appear when you travel")
        assertThat(card.hotlines).hasSize(1)
        assertThat(card.missions).isEmpty()
        assertThat(card.links).isEmpty()
        assertThat(card.footer).isNull()
        assertThat(card.badge.title).isEqualTo("Home")
        assertThat(card.badge.icon).isEqualTo(BadgeIcon.Home)
        assertThat(card.summary).isEqualTo("Home in Finland")
    }

    @Test
    fun unknownHostCountryAsksForLocationAndKeepsTheHotlineAndLinks() {
        for (host in listOf(null, "", "  ")) {
            val card = overview(listOf("FI"), host)

            assertThat(card.state).isEqualTo(State.LocationNeeded)
            assertThat(card.badge.title).isEqualTo("Location Needed")
            assertThat(card.badge.tone).isEqualTo(BadgeTone.Caution)
            assertThat(card.hotlines).hasSize(1)
            assertThat(card.links).hasSize(1)
            assertThat(card.summary).isEqualTo("+358 9 1605 5555")
            assertThat(card.summaryHelp).isEqualTo("Location needed to find your embassy · +358 9 1605 5555")
        }

        assertThat(EmbassyOverviewBuilder.LocationNeededTitle).isEqualTo("Location needed to find your embassy")
    }

    @Test
    fun withoutCitizenshipTheCardIsOff() {
        val card = overview(emptyList(), "MA")

        assertThat(card.state).isEqualTo(State.Off)
        assertThat(card.badge.title).isEqualTo("Off")
        assertThat(card.summary).isEqualTo("Off")
        assertThat(card.hotlines).isEmpty()
        assertThat(card.missions).isEmpty()
    }

    @Test
    fun hostCodeCaseAndWhitespaceDoNotMatter() {
        assertThat(overview(listOf("fi"), " ma ").missions).isNotEmpty()
        assertThat(overview(listOf("FI"), "fi").state).isEqualTo(State.AtHome)
    }

    @Test
    fun theMissionDatasetIsOnlyReadOnceTheUserIsAbroad() {
        var reads = 0
        val builder = EmbassyOverviewBuilder(locale, indexProvider = { reads++; index })

        builder.build(emptyList(), "MA", null)
        builder.build(listOf("FI"), null, null)
        builder.build(listOf("FI"), "FI", null)
        assertThat(reads).isEqualTo(0)

        builder.build(listOf("FI"), "MA", null)
        assertThat(reads).isEqualTo(1)
    }

    // endregion

    // region Summary

    @Test
    fun collapsedSummaryLeadsWithTheConsularLine() {
        val card = overview(listOf("FI"), "MA", coordinate = MissionCoordinate(34.020, -6.841))

        assertThat(card.summary).isEqualTo("+358 9 1605 5555")
        assertThat(card.summaryHelp).isEqualTo("Embassy of Finland in Rabat · 3.3 km · +358 9 1605 5555")
    }

    @Test
    fun collapsedSummaryFallsBackToTheCityAndDistanceWithoutAConsularLine() {
        val brazilian = DiplomaticMissionIndex(listOf(mission("BR-RAB", "BR", city = "Rabat")))
        val card = builderFor(brazilian).build(listOf("BR"), "MA", rabat)

        assertThat(card.hotlines).isEmpty()
        assertThat(card.summary).isEqualTo("Rabat ~1 km")
    }

    @Test
    fun collapsedSummaryDoesNotRepeatTheBadge() {
        val nordic = overview(listOf("FI"), "ES", coordinate = null)

        assertThat(nordic.badge.title).isEqualTo("Nordic help")
        assertThat(nordic.summary).doesNotContain("Nordic")
        assertThat(nordic.summaryHelp).contains("Nearest known Nordic missions")
    }

    @Test
    fun summaryForAnApproximateMissionKeepsTheTildeDistanceInTheHelp() {
        val card = overview(listOf("SE"), "MA", coordinate = MissionCoordinate(34.020, -6.841))

        assertThat(card.summary).isEqualTo("+46 8 405 50 05")
        assertThat(card.summaryHelp).contains("~1 km")
    }

    // endregion

    // region Links and footer

    @Test
    fun linksCarryTheRegistrationServiceAndTheMissionsDirectory() {
        val group = overview(listOf("FI"), "MA").links.first()

        assertThat(group.registration?.title).isEqualTo("Travel notification")
        assertThat(group.registration?.url).isEqualTo("https://matkustusilmoitus.fi/")
        assertThat(group.missionsDirectory?.title).isEqualTo("All missions")
        assertThat(group.missionsDirectory?.url).isEqualTo("https://finlandabroad.fi/frontpage")
    }

    @Test
    fun countriesWithoutARegistrationServiceStillLinkTheDirectory() {
        val group = overview(listOf("BG"), "MA").links.first()

        assertThat(group.registration).isNull()
        assertThat(group.missionsDirectory).isNotNull()
    }

    @Test
    fun footerNamesTheSourceTheDatasetDateAndTheOfficialSiteAdvice() {
        val card = overview(listOf("FI"), "MA")

        assertThat(card.footer)
            .isEqualTo("Locations: Wikidata (CC0) · updated Oct 5, 2026 · Check details on the official site before you go")
    }

    @Test
    fun footerFallsBackToRawDateAndOmitsMissingOnes() {
        assertThat(builderFor().formattedDate("soon")).isEqualTo("soon")

        val undated = DiplomaticMissionIndex(listOf(mission("FI-RAB", "FI")))
        val card = builderFor(undated).build(listOf("FI"), "MA", null)
        assertThat(card.footer).isEqualTo("Locations: Wikidata (CC0) · Check details on the official site before you go")
    }

    // endregion

    // region Integration with the bundled data

    @Test
    fun cardUsesTheBundledDatasetByDefault() {
        val card = EmbassyOverviewBuilder(locale).build(listOf("FI"), "ES", MissionCoordinate(40.4, -3.7))

        assertThat(card.state).isEqualTo(State.Abroad)
        assertThat(card.subtitle).isEqualTo("Finland in Spain")
        assertThat(card.missions).isNotEmpty()
        assertThat(card.footer).startsWith("Locations: Wikidata (CC0) · updated ")
        assertThat(card.footer).endsWith(" · Check details on the official site before you go")
    }

    @Test
    fun cardWithoutAnyCountryAsksForLocation() {
        assertThat(EmbassyOverviewBuilder(locale).build(listOf("FI"), null, null).state).isEqualTo(State.LocationNeeded)
    }

    @Test
    fun collapsedSummaryAtHomeAndOff() {
        assertThat(EmbassyOverviewBuilder(locale).build(listOf("FI"), "FI", null).summary).startsWith("Home in ")
        assertThat(EmbassyOverviewBuilder(locale).build(emptyList(), "FI", null).summary).isEqualTo("Off")
    }

    @Test
    fun essentialsListTheHotlineAndTheNearestOwnMission() {
        val essentials = EmbassyOverviewBuilder(locale).build(listOf("FI"), "ES", MissionCoordinate(40.4, -3.7)).essentials()!!

        assertThat(essentials.hotlines)
            .containsExactly(EmbassyEssentials.Hotline("Ministry for Foreign Affairs", "+358 9 1605 5555"))
        assertThat(essentials.nearestMission).isNotEmpty()
    }

    @Test
    fun essentialsAreEmptyWithoutCitizenship() {
        assertThat(EmbassyOverviewBuilder(locale).build(emptyList(), "ES", null).essentials()).isNull()
    }

    @Test
    fun essentialsKeepTheHotlineWhenHome() {
        val essentials = EmbassyOverviewBuilder(locale).build(listOf("FI"), "FI", null).essentials()!!

        assertThat(essentials.nearestMission).isNull()
        assertThat(essentials.hotlines).hasSize(1)
    }

    // endregion

    // region Citizenship settings text

    @Test
    fun countryNamesAreLocalizedAndFallBackToTheCode() {
        val english = CountryNames.forLocale(Locale.US)
        assertThat(english.name("FI")).isEqualTo("Finland")
        assertThat(english.name(" ma ")).isEqualTo("Morocco")
        assertThat(english.name("ZZ")).isEqualTo("ZZ")
        assertThat(english.name("")).isEqualTo("")
        assertThat(CountryNames.forLocale(Locale.forLanguageTag("fi-FI")).name("FI")).isEqualTo("Suomi")
    }

    @Test
    fun countryListJoinsNamesNaturally() {
        val english = CountryNames.forLocale(Locale.US)
        assertThat(english.list(listOf("FI"))).isEqualTo("Finland")
        assertThat(english.list(listOf("FI", "SE"))).isEqualTo("Finland and Sweden")
        assertThat(english.list(listOf("FI", "SE", "NO"))).isEqualTo("Finland, Sweden, and Norway")
    }

    @Test
    fun embassyStatusSaysWhetherTheConsularLineIsBundled() {
        val names = CountryNames.forLocale(Locale.US)
        assertThat(CitizenshipPresentation.embassyStatus(emptyList(), names))
            .isEqualTo("No citizenship chosen. The Your Embassy card is off.")
        assertThat(CitizenshipPresentation.embassyStatus(listOf("FI"), names))
            .isEqualTo("Finland · verified consular emergency line included")
        assertThat(CitizenshipPresentation.embassyStatus(listOf("BR"), names))
            .isEqualTo("Brazil · no verified consular line yet, embassy locations still work")
        assertThat(CitizenshipPresentation.embassyStatus(listOf("FI", "BR"), names))
            .isEqualTo("Finland and Brazil · verified consular line for Finland")
    }

    @Test
    fun theSectionFooterNamesTheSourcesAndTheLegalCaveat() {
        assertThat(CitizenshipPresentation.EmbassySectionFooter).contains("Wikidata (CC0)")
        assertThat(CitizenshipPresentation.EmbassySectionFooter).contains("not a legal guarantee")
    }

    // endregion
}
