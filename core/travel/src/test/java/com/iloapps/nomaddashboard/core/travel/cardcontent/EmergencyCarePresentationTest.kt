package com.iloapps.nomaddashboard.core.travel.cardcontent

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.EmergencyCareFacility
import com.iloapps.nomaddashboard.core.model.EmergencyCareSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareStatus
import com.iloapps.nomaddashboard.core.model.HospitalOwnership
import com.iloapps.nomaddashboard.core.travel.safety.EmergencyNumberDirectory
import org.junit.Test

class EmergencyCarePresentationTest {
    private val spain = EmergencyNumberDirectory.numbersFor("ES")

    private fun facility(
        name: String,
        distance: Double,
        ownership: HospitalOwnership = HospitalOwnership.UNKNOWN,
        placeId: String? = null,
        address: String? = null,
    ) = EmergencyCareFacility(
        placeId = placeId,
        name = name,
        address = address,
        distanceKilometers = distance,
        latitude = 36.0,
        longitude = -5.6,
        ownership = ownership,
    )

    private fun subtitle(
        snapshot: EmergencyCareSnapshot,
        enabled: Boolean = true,
        waiting: Boolean = false,
        numbers: com.iloapps.nomaddashboard.core.travel.safety.EmergencyNumbers? = spain,
    ) = EmergencyCarePresentation.subtitle(enabled, waiting, snapshot, numbers)

    @Test
    fun `the subtitle has the radius and the emergency number`() {
        assertThat(subtitle(EmergencyCareSnapshot(status = EmergencyCareStatus.READY)))
            .isEqualTo("Within 25 km · Emergency 112")
    }

    @Test
    fun `the radius follows the search`() {
        for (radius in listOf(50.0, 100.0)) {
            val snapshot = EmergencyCareSnapshot(status = EmergencyCareStatus.READY, searchRadiusKilometers = radius)

            assertThat(subtitle(snapshot)).isEqualTo("Within ${radius.toInt()} km · Emergency 112")
        }
    }

    @Test
    fun `the number is left out when the country is not listed`() {
        assertThat(subtitle(EmergencyCareSnapshot(status = EmergencyCareStatus.READY), numbers = null))
            .isEqualTo("Within 25 km")
    }

    @Test
    fun `the number stays when the search cannot run`() {
        assertThat(subtitle(EmergencyCareSnapshot(status = EmergencyCareStatus.PERMISSION_REQUIRED)))
            .isEqualTo("Precise location is required · Emergency 112")
        assertThat(subtitle(EmergencyCareSnapshot(status = EmergencyCareStatus.CONFIGURATION_REQUIRED)))
            .isEqualTo("Configuration · Emergency 112")
        assertThat(subtitle(EmergencyCareSnapshot(status = EmergencyCareStatus.UNAVAILABLE, sourceName = "Google Places")))
            .isEqualTo("Google Places · Emergency 112")
        assertThat(subtitle(EmergencyCareSnapshot(status = EmergencyCareStatus.LOADING)))
            .isEqualTo("Looking for nearby hospitals · Emergency 112")
    }

    @Test
    fun `the number is the general number to call first`() {
        val japan = EmergencyNumberDirectory.numbersFor("JP")

        assertThat(subtitle(EmergencyCareSnapshot(status = EmergencyCareStatus.READY), numbers = japan))
            .isEqualTo("Within 25 km · Emergency 110")
    }

    @Test
    fun `a card that is off says off and waiting for the location says so`() {
        assertThat(subtitle(EmergencyCareSnapshot(), enabled = false)).isEqualTo("Off")
        assertThat(subtitle(EmergencyCareSnapshot(), waiting = true))
            .isEqualTo("Checking device location before nearby search · Emergency 112")
    }

    @Test
    fun `hospitals are listed nearest first`() {
        val snapshot = EmergencyCareSnapshot(
            status = EmergencyCareStatus.READY,
            facilities = listOf(
                facility("Far", 6.8),
                facility("Near", 2.4),
                facility("Farthest", 18.6),
            ),
        )

        val rows = EmergencyCarePresentation.hospitals(snapshot)

        assertThat(rows.map { it.name }).containsExactly("Near", "Far", "Farthest").inOrder()
        assertThat(rows.map { it.distance.text }).containsExactly("2.4 km", "6.8 km", "19 km").inOrder()
        assertThat(rows.first().distance.number).isEqualTo("2.4")
        assertThat(rows.first().distance.unit).isEqualTo("km")
    }

    @Test
    fun `public and private are labelled and unknown ownership shows nothing`() {
        val snapshot = EmergencyCareSnapshot(
            facilities = listOf(
                facility("A", 1.0, HospitalOwnership.PUBLIC),
                facility("B", 2.0, HospitalOwnership.PRIVATE),
                facility("C", 3.0, HospitalOwnership.UNKNOWN),
            ),
        )

        assertThat(EmergencyCarePresentation.hospitals(snapshot).map { it.ownership })
            .containsExactly("Public", "Private", null).inOrder()
    }

    @Test
    fun `a row has an address detail and a stable id`() {
        val rows = EmergencyCarePresentation.hospitals(
            EmergencyCareSnapshot(
                facilities = listOf(
                    facility("With id", 1.0, placeId = "abc", address = "Calle Mayor 1"),
                    facility("Without id", 2.0),
                ),
            ),
        )

        assertThat(rows[0].id).isEqualTo("abc")
        assertThat(rows[0].detail).isEqualTo("Calle Mayor 1")
        assertThat(rows[1].id).isEqualTo("Without id@36.0,-5.6")
        assertThat(rows[1].detail).isEmpty()
    }
}
