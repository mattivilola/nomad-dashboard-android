package com.iloapps.nomaddashboard.core.travel.reference

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.iloapps.nomaddashboard.core.travel.safety.EmergencyNumberDirectory
import java.util.Locale
import org.junit.Test

class TravelReferenceDataTest {
    private val allRegions: List<String> = Locale.getISOCountries().toList()

    // Driving side

    @Test
    fun `well known countries drive on the expected side`() {
        for (code in listOf("GB", "IE", "JP", "TH", "ID", "IN", "AU", "NZ", "ZA", "KE", "MT", "CY", "HK", "JM")) {
            assertWithMessage("$code should drive on the left")
                .that(TravelReferenceData.drivingSide(code)).isEqualTo(DrivingSide.LEFT)
        }
        for (code in listOf("ES", "FI", "MA", "FR", "DE", "US", "CA", "MX", "BR", "CN", "VN", "PH", "EG", "GI", "MM", "VU", "IO")) {
            assertWithMessage("$code should drive on the right")
                .that(TravelReferenceData.drivingSide(code)).isEqualTo(DrivingSide.RIGHT)
        }
    }

    @Test
    fun `driving side lookup is case insensitive and trims whitespace`() {
        assertThat(TravelReferenceData.drivingSide("gb")).isEqualTo(DrivingSide.LEFT)
        assertThat(TravelReferenceData.drivingSide(" Gb ")).isEqualTo(DrivingSide.LEFT)
        assertThat(TravelReferenceData.drivingSide("es")).isEqualTo(DrivingSide.RIGHT)
    }

    @Test
    fun `unknown or missing codes have no driving side`() {
        assertThat(TravelReferenceData.drivingSide(null)).isNull()
        assertThat(TravelReferenceData.drivingSide("")).isNull()
        assertThat(TravelReferenceData.drivingSide("ZZ")).isNull()
        assertThat(TravelReferenceData.drivingSide("XX")).isNull()
        assertThat(TravelReferenceData.drivingSide("United Kingdom")).isNull()
    }

    @Test
    fun `every left driving entry is a valid region and the list has the verified size`() {
        val invalid = TravelReferenceData.leftDrivingRegions.filter { it !in allRegions }
        assertWithMessage("not ISO 3166-1 alpha-2 regions: ${invalid.sorted()}").that(invalid).isEmpty()
        assertThat(TravelReferenceData.leftDrivingRegions).hasSize(76)
    }

    @Test
    fun `the fifty four left driving un members are all on the list`() {
        val unMembers = setOf(
            "BW", "KE", "LS", "MW", "MU", "MZ", "NA", "SC", "SZ", "TZ", "UG", "ZA", "ZM", "ZW",
            "AG", "BS", "BB", "DM", "GD", "GY", "JM", "KN", "LC", "VC", "SR", "TT",
            "BD", "BT", "BN", "IN", "ID", "JP", "LK", "MV", "MY", "NP", "PK", "SG", "TH", "TL", "CY",
            "GB", "IE", "MT",
            "AU", "FJ", "KI", "NR", "NZ", "PG", "WS", "SB", "TO", "TV",
        )
        assertThat(unMembers).hasSize(54)
        assertThat(TravelReferenceData.leftDrivingRegions).containsAtLeastElementsIn(unMembers)
    }

    @Test
    fun `every valid region has a driving side`() {
        for (code in allRegions) {
            assertWithMessage("$code has no driving side").that(TravelReferenceData.drivingSide(code)).isNotNull()
        }
    }

    // Electricity

    @Test
    fun `spain and morocco list their plug types`() {
        assertThat(TravelReferenceData.electricity("ES"))
            .isEqualTo(CountryElectricity(plugTypes = listOf("C", "F"), voltage = 230, frequencyHertz = 50))
        assertThat(TravelReferenceData.electricity("MA"))
            .isEqualTo(CountryElectricity(plugTypes = listOf("C", "E"), voltage = 220, frequencyHertz = 50))
        assertThat(TravelReferenceData.electricity("us"))
            .isEqualTo(CountryElectricity(plugTypes = listOf("A", "B"), voltage = 120, frequencyHertz = 60))
        assertThat(TravelReferenceData.electricity("GB")?.plugTypes).containsExactly("G")
        assertThat(TravelReferenceData.electricity("AU")?.plugTypes).containsExactly("I")
    }

    @Test
    fun `unverified electricity is deliberately absent`() {
        assertThat(TravelReferenceData.electricity(null)).isNull()
        assertThat(TravelReferenceData.electricity("")).isNull()
        assertThat(TravelReferenceData.electricity("ZZ")).isNull()
        // Regional supplies the model cannot express with one voltage and one frequency.
        assertThat(TravelReferenceData.electricity("BR")).isNull()
        assertThat(TravelReferenceData.electricity("JP")).isNull()
        // Mixed supplies and sources that disagree.
        for (code in listOf("SA", "CU", "BO", "UZ")) {
            assertWithMessage("$code should be unverified").that(TravelReferenceData.electricity(code)).isNull()
        }
    }

    @Test
    fun `the table has 104 countries`() {
        assertThat(allRegions.count { TravelReferenceData.electricity(it) != null }).isEqualTo(104)
    }

    @Test
    fun `every emergency directory country has electricity except the unverified ones`() {
        val unverified = setOf("BR", "JP")
        for (code in allRegions.filter { EmergencyNumberDirectory.numbersFor(it) != null }) {
            if (code in unverified) {
                assertThat(TravelReferenceData.electricity(code)).isNull()
            } else {
                assertWithMessage("$code has no electricity entry").that(TravelReferenceData.electricity(code)).isNotNull()
            }
        }
    }

    @Test
    fun `electricity entries are well formed`() {
        val validTypes = "ABCDEFGHIJKLMNO".map { it.toString() }.toSet()
        for (code in allRegions) {
            val electricity = TravelReferenceData.electricity(code) ?: continue
            assertWithMessage("$code has no plug types").that(electricity.plugTypes).isNotEmpty()
            assertWithMessage("$code plug types are not sorted").that(electricity.plugTypes).isInStrictOrder()
            assertWithMessage("$code has an unknown plug type").that(validTypes).containsAtLeastElementsIn(electricity.plugTypes)
            assertWithMessage("$code voltage ${electricity.voltage}").that(electricity.voltage in 100..240).isTrue()
            assertWithMessage("$code frequency ${electricity.frequencyHertz}")
                .that(electricity.frequencyHertz in listOf(50, 60)).isTrue()
        }
    }

    @Test
    fun `the supply differs only when it matters to a traveller`() {
        val spain = CountryElectricity(listOf("C", "F"), 230, 50)
        val morocco = CountryElectricity(listOf("C", "E"), 220, 50)
        val france = CountryElectricity(listOf("C", "E"), 230, 50)
        val uk = CountryElectricity(listOf("G"), 230, 50)
        val kenya = CountryElectricity(listOf("G"), 240, 50)
        val usa = CountryElectricity(listOf("A", "B"), 120, 60)
        val japanLike = CountryElectricity(listOf("A", "B"), 100, 50)

        assertThat(spain.differsFrom(morocco)).isTrue()
        assertThat(morocco.differsFrom(spain)).isTrue()
        assertThat(morocco.differsFrom(france)).isFalse() // 220 V and 230 V are the same supply
        assertThat(uk.differsFrom(kenya)).isFalse() // 230 V and 240 V too
        assertThat(uk.differsFrom(spain)).isTrue()
        assertThat(spain.differsFrom(usa)).isTrue()
        assertThat(usa.differsFrom(japanLike)).isTrue() // same plugs, 100 V at 50 Hz
        assertThat(CountryElectricity(listOf("F", "C"), 230, 50).differsFrom(spain)).isFalse()
    }

    // Currency

    @Test
    fun `currency comes from region data`() {
        assertThat(TravelReferenceData.currencyCode("MA")).isEqualTo("MAD")
        assertThat(TravelReferenceData.currencyCode("ES")).isEqualTo("EUR")
        assertThat(TravelReferenceData.currencyCode("FI")).isEqualTo("EUR")
        assertThat(TravelReferenceData.currencyCode("US")).isEqualTo("USD")
        assertThat(TravelReferenceData.currencyCode("TH")).isEqualTo("THB")
        assertThat(TravelReferenceData.currencyCode(" th ")).isEqualTo("THB")
    }

    @Test
    fun `currency is null for unknown codes`() {
        assertThat(TravelReferenceData.currencyCode(null)).isNull()
        assertThat(TravelReferenceData.currencyCode("")).isNull()
        assertThat(TravelReferenceData.currencyCode("ZZ")).isNull()
        assertThat(TravelReferenceData.currencyCode("Morocco")).isNull()
    }

    @Test
    fun `currency lookup never throws for any valid region`() {
        for (code in allRegions) {
            val currency = TravelReferenceData.currencyCode(code)
            assertWithMessage(code).that(currency).isNotEqualTo("XXX")
        }
    }

    // Changes between countries

    @Test
    fun `changes compare the previous country with the new one`() {
        // Spain to the UK: other side, other plugs, other currency.
        assertThat(TravelReferenceData.drivingSideChanged("ES", "GB")).isTrue()
        assertThat(TravelReferenceData.electricityChanged("ES", "GB")).isTrue()
        assertThat(TravelReferenceData.currencyChanged("ES", "GB")).isTrue()
        // Spain to Portugal: nothing to switch.
        assertThat(TravelReferenceData.drivingSideChanged("ES", "PT")).isFalse()
        assertThat(TravelReferenceData.electricityChanged("ES", "PT")).isFalse()
        assertThat(TravelReferenceData.currencyChanged("ES", "PT")).isFalse()
        // Morocco and France share plugs and the same supply (220 V against 230 V).
        assertThat(TravelReferenceData.electricityChanged("FR", "MA")).isFalse()
    }

    @Test
    fun `changes are false when either country is unknown`() {
        assertThat(TravelReferenceData.drivingSideChanged(null, "GB")).isFalse()
        assertThat(TravelReferenceData.drivingSideChanged("ES", "ZZ")).isFalse()
        assertThat(TravelReferenceData.electricityChanged("BR", "ES")).isFalse()
        assertThat(TravelReferenceData.electricityChanged("ES", "JP")).isFalse()
        assertThat(TravelReferenceData.currencyChanged("", "ES")).isFalse()
        assertThat(TravelReferenceData.currencyChanged("ES", "ZZ")).isFalse()
    }
}
