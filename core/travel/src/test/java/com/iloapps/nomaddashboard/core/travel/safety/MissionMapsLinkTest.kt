package com.iloapps.nomaddashboard.core.travel.safety

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionKind.Embassy
import java.net.URLDecoder
import org.junit.Test

class MissionMapsLinkTest {
    private fun mission(
        name: String,
        city: String? = "Rabat",
        latitude: Double = 34.02,
        longitude: Double = -6.84,
        approximate: Boolean,
    ) = DiplomaticMission(
        id = "X",
        sendingCountry = "FI",
        hostCountry = "MA",
        kind = Embassy,
        name = name,
        city = city,
        latitude = latitude,
        longitude = longitude,
        isApproximateLocation = approximate,
    )

    private fun rawQuery(uri: String): String = uri.substringAfter("?q=")

    private fun decodedQuery(uri: String): String = URLDecoder.decode(rawQuery(uri), "UTF-8")

    @Test
    fun aPreciseLocationIsPinnedWithItsNameAsLabel() {
        val uri = MissionMapsLink.uri(mission("Embassy of Finland", latitude = 34.05, approximate = false))

        assertThat(uri).isEqualTo("geo:34.050000,-6.840000?q=34.050000,-6.840000(Embassy%20of%20Finland)")
    }

    @Test
    fun anApproximateLocationIsSearchedByNameAndCityInsteadOfPinned() {
        val uri = MissionMapsLink.uri(mission("Embassy of Finland", approximate = true))

        assertThat(uri).isEqualTo("geo:34.020000,-6.840000?q=Embassy%20of%20Finland,%20Rabat")
        assertThat(decodedQuery(uri)).isEqualTo("Embassy of Finland, Rabat")
    }

    @Test
    fun anApproximateLocationWithoutACityIsSearchedByNameOnly() {
        val uri = MissionMapsLink.uri(mission("Embassy of Finland", city = " ", approximate = true))

        assertThat(decodedQuery(uri)).isEqualTo("Embassy of Finland")
    }

    @Test
    fun queryEscapesCharactersThatWouldSplitIt() {
        val uri = MissionMapsLink.uri(
            mission("Embassy of Bosnia & Herzegovina #1 (a=b+c) Ünal", city = "Rabat; MA", approximate = true),
        )
        val raw = rawQuery(uri)

        for (forbidden in listOf("&", "#", ";", "+", "=", "(", ")", " ", "Ü")) {
            assertThat(raw).doesNotContain(forbidden)
        }
        assertThat(decodedQuery(uri)).isEqualTo("Embassy of Bosnia & Herzegovina #1 (a=b+c) Ünal, Rabat; MA")
    }

    @Test
    fun aPinnedLabelCannotCloseItsOwnParentheses() {
        val uri = MissionMapsLink.uri(mission("Embassy (Annex)", latitude = 34.05, approximate = false))

        assertThat(uri).isEqualTo("geo:34.050000,-6.840000?q=34.050000,-6.840000(Embassy%20%28Annex%29)")
    }

    @Test
    fun invalidCoordinatesFallBackToANameOnlySearch() {
        val uri = MissionMapsLink.uri(mission("Embassy of Finland", latitude = 999.0, longitude = 0.0, approximate = false))

        assertThat(uri).isEqualTo("geo:0,0?q=Embassy%20of%20Finland")
        assertThat(MissionMapsLink.uri(mission("Embassy of Finland", latitude = Double.NaN, approximate = true)))
            .isEqualTo("geo:0,0?q=Embassy%20of%20Finland,%20Rabat")
    }
}
