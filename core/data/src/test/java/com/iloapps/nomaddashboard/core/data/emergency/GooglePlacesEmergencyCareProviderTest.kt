package com.iloapps.nomaddashboard.core.data.emergency

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.EmergencyCareLocationSource
import com.iloapps.nomaddashboard.core.model.EmergencyCareStatus
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GooglePlacesEmergencyCareProviderTest {
    @Test
    fun `maps nearby hospital result into ready snapshot`() = runTest {
        val provider = GooglePlacesEmergencyCareProvider(
            searchClient = FakePlacesNearbySearchClient(
                PlacesNearbySearchOutcome.Success(
                    places = listOf(
                        PlacesNearbySearchPlace(
                            placeId = "far-hospital",
                            name = "Far Hospital",
                            address = "Far Road",
                            latitude = 36.25,
                            longitude = -5.62,
                            primaryType = "hospital",
                        ),
                        PlacesNearbySearchPlace(
                            placeId = "near-hospital",
                            name = "Near Hospital",
                            address = "Near Road",
                            latitude = 36.02,
                            longitude = -5.61,
                            primaryType = "hospital",
                        ),
                    ),
                ),
            ),
        )

        val snapshot = provider.nearbyHospital(
            EmergencyCareSearchRequest(
                latitude = 36.0132,
                longitude = -5.6069,
                countryCode = "ES",
                countryName = "Spain",
                locationSource = EmergencyCareLocationSource.DEVICE,
            ),
        )

        assertThat(snapshot.status).isEqualTo(EmergencyCareStatus.READY)
        assertThat(snapshot.facility?.name).isEqualTo("Near Hospital")
        assertThat(snapshot.note).isEqualTo("Using device location.")
    }

    @Test
    fun `maps configuration outcome into configuration required snapshot`() = runTest {
        val provider = GooglePlacesEmergencyCareProvider(
            searchClient = FakePlacesNearbySearchClient(
                PlacesNearbySearchOutcome.ConfigurationRequired(
                    "Add a local Android Maps/Places key before emergency-care lookups can run.",
                ),
            ),
        )

        val snapshot = provider.nearbyHospital(
            EmergencyCareSearchRequest(
                latitude = 60.1699,
                longitude = 24.9384,
                countryCode = "FI",
                countryName = "Finland",
                locationSource = EmergencyCareLocationSource.IP_GEOLOCATION,
            ),
        )

        assertThat(snapshot.status).isEqualTo(EmergencyCareStatus.CONFIGURATION_REQUIRED)
        assertThat(snapshot.detail).isEqualTo("Emergency care is unavailable in this build right now.")
    }

    @Test
    fun `maps empty nearby results into unavailable snapshot`() = runTest {
        val provider = GooglePlacesEmergencyCareProvider(
            searchClient = FakePlacesNearbySearchClient(
                PlacesNearbySearchOutcome.Success(places = emptyList()),
            ),
        )

        val snapshot = provider.nearbyHospital(
            EmergencyCareSearchRequest(
                latitude = 60.1699,
                longitude = 24.9384,
                countryCode = "FI",
                countryName = "Finland",
                locationSource = EmergencyCareLocationSource.IP_GEOLOCATION,
            ),
        )

        assertThat(snapshot.status).isEqualTo(EmergencyCareStatus.UNAVAILABLE)
        assertThat(snapshot.detail).contains("No nearby hospitals")
    }
}

private class FakePlacesNearbySearchClient(
    private val outcome: PlacesNearbySearchOutcome,
) : PlacesNearbySearchClient {
    override suspend fun searchNearbyHospitals(request: PlacesNearbySearchRequest): PlacesNearbySearchOutcome = outcome
}
