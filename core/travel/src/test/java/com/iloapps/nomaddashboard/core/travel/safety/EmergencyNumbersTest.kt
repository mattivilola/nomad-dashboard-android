package com.iloapps.nomaddashboard.core.travel.safety

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.Locale
import org.junit.Test

class EmergencyNumbersTest {
    private val isoCountries: Array<String> = Locale.getISOCountries()

    @Test
    fun lookupIsCaseInsensitiveAndTrimsWhitespace() {
        assertThat(EmergencyNumberDirectory.numbersFor("fr")).isEqualTo(EmergencyNumberDirectory.numbersFor("FR"))
        assertThat(EmergencyNumberDirectory.numbersFor(" Fr ")).isEqualTo(EmergencyNumberDirectory.numbersFor("FR"))
        assertThat(EmergencyNumberDirectory.numbersFor("FR")).isNotNull()
    }

    @Test
    fun unknownOrMissingCodesReturnNull() {
        assertThat(EmergencyNumberDirectory.numbersFor(null)).isNull()
        assertThat(EmergencyNumberDirectory.numbersFor("")).isNull()
        assertThat(EmergencyNumberDirectory.numbersFor("ZZ")).isNull()
        assertThat(EmergencyNumberDirectory.numbersFor("France")).isNull()
    }

    @Test
    fun unverifiedCountriesAreDeliberatelyAbsent() {
        assertThat(EmergencyNumberDirectory.numbersFor("LA")).isNull()
        assertThat(EmergencyNumberDirectory.numbersFor("GT")).isNull()
    }

    @Test
    fun directoryHoldsExactlyTheSeventySixVerifiedCountries() {
        // The macOS table has 76 entries (the parity brief says 77 but lists no extra country).
        assertThat(EmergencyNumberDirectory.countryCodes).hasSize(76)
        assertThat(EmergencyNumberDirectory.countryCodes.all { it in isoCountries }).isTrue()
    }

    @Test
    fun moroccoListsPoliceAmbulanceFireAndTheRuralGendarmerie() {
        val morocco = EmergencyNumberDirectory.numbersFor("MA")
        assertThat(morocco).isEqualTo(
            EmergencyNumbers(
                general = "19",
                ambulance = "15",
                fire = "15",
                other = listOf(EmergencyService(name = "Gendarmerie", number = "177")),
            ),
        )
        assertThat(morocco?.police).isNull()
        assertThat(morocco?.other).containsExactly(EmergencyService(name = "Gendarmerie", number = "177"))
    }

    @Test
    fun ruralAndHighwayPoliceAreStoredOnlyForVerifiedCountries() {
        val expected = mapOf(
            "MA" to listOf(EmergencyService("Gendarmerie", "177")),
            "ES" to listOf(EmergencyService("Guardia Civil", "062")),
            "BR" to listOf(EmergencyService("Federal Highway Police", "191")),
            "TH" to listOf(EmergencyService("Highway Police", "1193")),
        )
        for (code in isoCountries) {
            val numbers = EmergencyNumberDirectory.numbersFor(code) ?: continue
            assertWithMessage("unexpected other services for $code")
                .that(numbers.other).isEqualTo(expected[code].orEmpty())
        }
        for ((code, services) in expected) {
            assertWithMessage("missing other services for $code")
                .that(EmergencyNumberDirectory.numbersFor(code)?.other).isEqualTo(services)
        }
        assertThat(EmergencyNumberDirectory.numbersFor("FR")?.other).isEmpty()
    }

    @Test
    fun spainListsTheGuardiaCivilEmergencyNumber() {
        val spain = EmergencyNumberDirectory.numbersFor("ES")
        assertThat(spain).isEqualTo(
            EmergencyNumbers(general = "112", other = listOf(EmergencyService("Guardia Civil", "062"))),
        )
        assertThat(spain?.police).isNull()
    }

    @Test
    fun brazilListsTheFederalHighwayPolice() {
        assertThat(EmergencyNumberDirectory.numbersFor("BR")).isEqualTo(
            EmergencyNumbers(
                general = "190",
                ambulance = "192",
                fire = "193",
                other = listOf(EmergencyService("Federal Highway Police", "191")),
            ),
        )
    }

    @Test
    fun thailandListsTheHighwayPolice() {
        assertThat(EmergencyNumberDirectory.numbersFor("TH")).isEqualTo(
            EmergencyNumbers(
                general = "191",
                ambulance = "1669",
                fire = "199",
                other = listOf(EmergencyService("Highway Police", "1193")),
            ),
        )
    }

    @Test
    fun additionalServiceNumbersNeverDuplicateTheNumbersAlreadyShown() {
        for (code in isoCountries) {
            val numbers = EmergencyNumberDirectory.numbersFor(code) ?: continue
            val shown = listOfNotNull(numbers.general, numbers.police, numbers.ambulance, numbers.fire)
            for (service in numbers.other) {
                assertWithMessage("${service.name} duplicates a shown number for $code")
                    .that(service.number in shown).isFalse()
            }
        }
    }

    @Test
    fun europeanSpotChecks() {
        assertThat(EmergencyNumberDirectory.numbersFor("PT")).isEqualTo(EmergencyNumbers(general = "112"))
        assertThat(EmergencyNumberDirectory.numbersFor("FR"))
            .isEqualTo(EmergencyNumbers(general = "112", police = "17", ambulance = "15", fire = "18"))
        assertThat(EmergencyNumberDirectory.numbersFor("DE")).isEqualTo(EmergencyNumbers(general = "112", police = "110"))
        assertThat(EmergencyNumberDirectory.numbersFor("GB")?.general).isEqualTo("999")
        assertThat(EmergencyNumberDirectory.numbersFor("CH"))
            .isEqualTo(EmergencyNumbers(general = "112", police = "117", ambulance = "144", fire = "118"))
        assertThat(EmergencyNumberDirectory.numbersFor("NO"))
            .isEqualTo(EmergencyNumbers(general = "112", ambulance = "113", fire = "110"))
    }

    @Test
    fun worldSpotChecks() {
        assertThat(EmergencyNumberDirectory.numbersFor("US")).isEqualTo(EmergencyNumbers(general = "911"))
        assertThat(EmergencyNumberDirectory.numbersFor("AU")?.general).isEqualTo("000")
        assertThat(EmergencyNumberDirectory.numbersFor("NZ")?.general).isEqualTo("111")
        assertThat(EmergencyNumberDirectory.numbersFor("JP"))
            .isEqualTo(EmergencyNumbers(general = "110", ambulance = "119", fire = "119"))
        assertThat(EmergencyNumberDirectory.numbersFor("CO")?.general).isEqualTo("123")
        assertThat(EmergencyNumberDirectory.numbersFor("AE"))
            .isEqualTo(EmergencyNumbers(general = "999", ambulance = "998", fire = "997"))
    }

    @Test
    fun perServiceNumbersAreOnlyStoredWhenTheyDifferFromTheGeneralNumber() {
        for (code in isoCountries) {
            val numbers = EmergencyNumberDirectory.numbersFor(code) ?: continue
            assertWithMessage("police duplicates general for $code").that(numbers.police).isNotEqualTo(numbers.general)
            assertWithMessage("ambulance duplicates general for $code").that(numbers.ambulance).isNotEqualTo(numbers.general)
            assertWithMessage("fire duplicates general for $code").that(numbers.fire).isNotEqualTo(numbers.general)
            assertThat(numbers.general).isNotEmpty()
        }
    }

    @Test
    fun directoryCoversTheMostVisitedNomadCountries() {
        val required = listOf(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IS", "IE", "IT", "LV",
            "LI", "LT", "LU", "MT", "NL", "NO", "PL", "PT", "RO", "SK", "SI", "ES", "SE", "GB", "CH", "TR", "GE",
            "AL", "BA", "ME", "RS", "MK", "UA", "MD", "US", "CA", "MX", "CR", "PA", "CO", "PE", "CL", "AR", "BR",
            "EC", "UY", "TH", "VN", "ID", "MY", "SG", "PH", "JP", "KR", "TW", "IN", "LK", "NP", "KH", "AU", "NZ",
            "AE", "IL", "JO", "EG", "MA", "ZA", "KE", "TZ",
        )
        for (code in required) {
            assertWithMessage("missing $code").that(EmergencyNumberDirectory.numbersFor(code)).isNotNull()
        }
        assertThat(required.toSet()).isEqualTo(EmergencyNumberDirectory.countryCodes)
    }

    @Test
    fun headlineIsTheGeneralNumber() {
        assertThat(EmergencyNumberDirectory.numbersFor("PT")?.headline).isEqualTo("Emergency 112")
        assertThat(EmergencyNumberDirectory.numbersFor("MA")?.headline).isEqualTo("Emergency 19")
    }

    @Test
    fun othersTextListsOnlyTheNumbersThatDifferFromGeneral() {
        assertThat(EmergencyNumberDirectory.numbersFor("PT")?.othersText).isNull()
        assertThat(EmergencyNumberDirectory.numbersFor("DE")?.othersText).isEqualTo("Police 110")
        assertThat(EmergencyNumberDirectory.numbersFor("FR")?.othersText).isEqualTo("Police 17 · Ambulance 15 · Fire 18")
        assertThat(EmergencyNumberDirectory.numbersFor("JP")?.othersText).isEqualTo("Ambulance/Fire 119")
        assertThat(EmergencyNumberDirectory.numbersFor("MA")?.othersText).isEqualTo("Ambulance/Fire 15 · Gendarmerie 177")
        assertThat(EmergencyNumberDirectory.numbersFor("ES")?.othersText).isEqualTo("Guardia Civil 062")
        assertThat(EmergencyNumberDirectory.numbersFor("TH")?.othersText)
            .isEqualTo("Ambulance 1669 · Fire 199 · Highway Police 1193")
        assertThat(EmergencyNumbers(general = "1", police = "2", ambulance = "3", fire = "3").othersText)
            .isEqualTo("Police 2 · Ambulance/Fire 3")
    }

    @Test
    fun currentCountryCodePrefersDeviceLocationThenIpLocationThenLocalInfo() {
        assertThat(CurrentCountry.code("pt", "ES", "fr")).isEqualTo("PT")
        assertThat(CurrentCountry.code(null, "ES", "fr")).isEqualTo("ES")
        assertThat(CurrentCountry.code(null, null, "fr")).isEqualTo("FR")
        assertThat(CurrentCountry.code(null, null, null)).isNull()
    }

    @Test
    fun currentCountryCodeSkipsBlankCodes() {
        assertThat(CurrentCountry.code("  ", null, "de")).isEqualTo("DE")
        assertThat(CurrentCountry.code("  ", null, null)).isNull()
    }
}
