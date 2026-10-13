package com.iloapps.nomaddashboard.core.travel.safety

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.net.URI
import org.junit.Test

class ConsularContactsTest {
    private val coveredCodes = listOf(
        "AT", "BG", "CZ", "DK", "EE", "FI", "FR", "DE", "HU", "IE", "IT", "LV", "LU", "MT", "NL", "PT", "SK", "SI",
        "ES", "SE", "NO", "IS", "CH", "GB", "US", "CA", "AU", "NZ",
    )

    private val excludedCodes = listOf("BE", "HR", "CY", "GR", "LT", "PL", "RO")

    private fun allContacts(): List<Pair<String, ConsularContacts>> = coveredCodes.mapNotNull { code ->
        ConsularContactsDirectory.contactsFor(code)?.let { code to it }
    }

    @Test
    fun lookupIsCaseInsensitiveAndTrimsWhitespace() {
        assertThat(ConsularContactsDirectory.contactsFor("fi")).isEqualTo(ConsularContactsDirectory.contactsFor("FI"))
        assertThat(ConsularContactsDirectory.contactsFor(" Fi ")).isEqualTo(ConsularContactsDirectory.contactsFor("FI"))
        assertThat(ConsularContactsDirectory.contactsFor("FI")).isNotNull()
    }

    @Test
    fun unknownOrMissingCodesReturnNull() {
        assertThat(ConsularContactsDirectory.contactsFor(null)).isNull()
        assertThat(ConsularContactsDirectory.contactsFor("")).isNull()
        assertThat(ConsularContactsDirectory.contactsFor("ZZ")).isNull()
        assertThat(ConsularContactsDirectory.contactsFor("Finland")).isNull()
    }

    @Test
    fun unverifiedCountriesAreDeliberatelyAbsent() {
        for (code in excludedCodes) {
            assertWithMessage("$code must stay excluded until verified")
                .that(ConsularContactsDirectory.contactsFor(code)).isNull()
        }
    }

    @Test
    fun everyCoveredCountryIsPresentAndNothingElse() {
        assertThat(allContacts()).hasSize(coveredCodes.size)
        assertThat(coveredCodes).hasSize(28)
        assertThat(coveredCodes.toSet()).isEqualTo(ConsularContactsDirectory.citizenshipCodes)
        assertThat(coveredCodes.toSet().intersect(excludedCodes.toSet())).isEmpty()
    }

    @Test
    fun finlandSpotCheck() {
        val finland = ConsularContactsDirectory.contactsFor("FI")
        assertThat(finland?.ministryName).isEqualTo("Ministry for Foreign Affairs of Finland")
        assertThat(finland?.emergencyPhone).isEqualTo("+358 9 1605 5555")
        assertThat(finland?.travelRegistrationName).isEqualTo("Travel notification")
        assertThat(URI(finland?.travelRegistrationUrl.orEmpty()).host).isEqualTo("matkustusilmoitus.fi")
        assertThat(URI(finland?.missionsDirectoryUrl.orEmpty()).host).isEqualTo("finlandabroad.fi")
    }

    @Test
    fun englishSpeakingSpotChecks() {
        assertThat(ConsularContactsDirectory.contactsFor("GB")?.emergencyPhone).isEqualTo("+44 20 7008 5000")
        assertThat(ConsularContactsDirectory.contactsFor("GB")?.travelRegistrationUrl).isNull()
        assertThat(ConsularContactsDirectory.contactsFor("US")?.emergencyPhone).isEqualTo("+1 202 501 4444")
        assertThat(ConsularContactsDirectory.contactsFor("US")?.travelRegistrationName)
            .isEqualTo("Smart Traveler Enrollment Program (STEP)")
        assertThat(ConsularContactsDirectory.contactsFor("CA")?.emergencyPhone).isEqualTo("+1 613 996 8885")
        assertThat(ConsularContactsDirectory.contactsFor("CA")?.travelRegistrationName)
            .isEqualTo("Registration of Canadians Abroad")
        assertThat(ConsularContactsDirectory.contactsFor("AU")?.emergencyPhone).isEqualTo("+61 2 6261 3305")
        assertThat(ConsularContactsDirectory.contactsFor("AU")?.travelRegistrationName)
            .isEqualTo("Smartraveller subscription")
    }

    @Test
    fun registrationFieldsAreSetTogether() {
        for ((code, contacts) in allContacts()) {
            assertWithMessage("$code must set the registration URL and name together")
                .that(contacts.travelRegistrationUrl == null).isEqualTo(contacts.travelRegistrationName == null)
        }
    }

    @Test
    fun everyEntryHasAnInternationalPhoneNumber() {
        for ((code, contacts) in allContacts()) {
            assertWithMessage("$code has an empty phone number").that(contacts.emergencyPhone).isNotEmpty()
            assertWithMessage("$code phone number must start with +").that(contacts.emergencyPhone).startsWith("+")
            val digits = contacts.emergencyPhone.count { it.isDigit() }
            assertWithMessage("$code phone number has an implausible length").that(digits in 8..15).isTrue()
            assertWithMessage("$code has an empty ministry name").that(contacts.ministryName).isNotEmpty()
        }
    }

    @Test
    fun everyUrlUsesHttps() {
        for ((code, contacts) in allContacts()) {
            for (url in listOfNotNull(contacts.travelRegistrationUrl, contacts.missionsDirectoryUrl)) {
                val uri = URI(url)
                assertWithMessage("$code has a non-https URL: $url").that(uri.scheme).isEqualTo("https")
                assertWithMessage("$code has a URL without a host: $url").that(uri.host.orEmpty()).isNotEmpty()
            }
        }
    }

    @Test
    fun everyEntryListsAnOfficialMissionsDirectory() {
        for ((code, contacts) in allContacts()) {
            assertWithMessage("$code is missing a missions directory").that(contacts.missionsDirectoryUrl).isNotNull()
        }
    }
}
