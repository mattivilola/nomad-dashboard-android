package com.iloapps.nomaddashboard.core.data.emergency

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.EmergencyCareFacility
import com.iloapps.nomaddashboard.core.model.EmergencyCareLocationSource
import com.iloapps.nomaddashboard.core.model.EmergencyCareStatus
import com.iloapps.nomaddashboard.core.model.HospitalOwnership
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
                            latitude = 36.15,
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
        assertThat(snapshot.facilities.map { it.name }).containsExactly("Near Hospital", "Far Hospital").inOrder()
        assertThat(snapshot.searchRadiusKilometers).isEqualTo(25.0)
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
        // Nothing was found at any radius, so the snapshot reports the widest one searched.
        assertThat(snapshot.searchRadiusKilometers).isEqualTo(100.0)
        assertThat(snapshot.detail).contains("100 km")
    }

    @Test
    fun `searches 25 km first for more hospitals than are displayed`() = runTest {
        val client = FakePlacesNearbySearchClient(PlacesNearbySearchOutcome.Success(places = emptyList()))
        val provider = GooglePlacesEmergencyCareProvider(searchClient = client)

        provider.nearbyHospital(helsinkiRequest())

        val searched = client.requests.first()
        assertThat(searched.radiusMeters).isEqualTo(25_000.0)
        assertThat(searched.maxResultCount).isEqualTo(20)
    }

    @Test
    fun `lists a 2_4 km hospital before a 6_8 km one even when the far one is the nearest public`() = runTest {
        val originLatitude = 36.0132
        val provider = GooglePlacesEmergencyCareProvider(
            searchClient = FakePlacesNearbySearchClient(
                PlacesNearbySearchOutcome.Success(
                    places = listOf(
                        place("far-public", "Hospital Publico Lejano", originLatitude + kilometersToDegrees(6.8)),
                        place("near-private", "Clinica Privada Cercana", originLatitude + kilometersToDegrees(2.4)),
                        place("middle", "Hospital Central", originLatitude + kilometersToDegrees(4.0)),
                    ),
                ),
            ),
        )

        val snapshot = provider.nearbyHospital(
            EmergencyCareSearchRequest(
                latitude = originLatitude,
                longitude = -5.6069,
                countryCode = "ES",
                countryName = "Spain",
                locationSource = EmergencyCareLocationSource.DEVICE,
            ),
        )

        assertThat(snapshot.facilities.map { it.placeId })
            .containsExactly("near-private", "middle", "far-public")
            .inOrder()
        assertThat(snapshot.facilities.first().distanceKilometers).isWithin(0.05).of(2.4)
        assertThat(snapshot.facilities.last().distanceKilometers).isWithin(0.05).of(6.8)
        assertThat(snapshot.facilities.map { it.ownership })
            .containsExactly(HospitalOwnership.PRIVATE, HospitalOwnership.UNKNOWN, HospitalOwnership.PUBLIC)
            .inOrder()
    }

    @Test
    fun `selection guarantees the nearest public and private hospital and lists nearest first`() {
        val hospitals = listOf(
            facility("a", 1.0, HospitalOwnership.UNKNOWN),
            facility("b", 2.0, HospitalOwnership.UNKNOWN),
            facility("c", 3.0, HospitalOwnership.UNKNOWN),
            facility("public", 8.0, HospitalOwnership.PUBLIC),
            facility("private", 9.0, HospitalOwnership.PRIVATE),
            facility("later-public", 10.0, HospitalOwnership.PUBLIC),
        )

        val selected = selectEmergencyHospitals(hospitals.shuffled(java.util.Random(7)), maximumResults = 3)

        assertThat(selected.map { it.placeId }).containsExactly("a", "public", "private").inOrder()
    }

    @Test
    fun `selection without both ownerships is simply the nearest hospitals`() {
        val onlyPublic = listOf(
            facility("a", 5.0, HospitalOwnership.UNKNOWN),
            facility("b", 1.0, HospitalOwnership.PUBLIC),
            facility("c", 3.0, HospitalOwnership.PUBLIC),
            facility("d", 2.0, HospitalOwnership.UNKNOWN),
        )

        assertThat(selectEmergencyHospitals(onlyPublic, maximumResults = 3).map { it.placeId })
            .containsExactly("b", "d", "c")
            .inOrder()
        assertThat(selectEmergencyHospitals(onlyPublic, maximumResults = 0)).isEmpty()
    }

    @Test
    fun `selection drops duplicates and hospitals outside the radius`() {
        val selected = selectEmergencyHospitals(
            facilities = listOf(
                facility("one", 2.0, HospitalOwnership.UNKNOWN, name = "Hospital Norte"),
                facility("two", 2.0, HospitalOwnership.UNKNOWN, name = "HOSPITAL  norte"),
                facility("far", 30.0, HospitalOwnership.PUBLIC),
            ),
            maximumResults = 3,
            radiusKilometers = 25.0,
        )

        assertThat(selected.map { it.placeId }).containsExactly("one")
    }

    @Test
    fun `ownership is classified from name and type hint ignoring case and accents`() {
        assertThat(classifyHospitalOwnership("Hospital Público de Algeciras", null)).isEqualTo(HospitalOwnership.PUBLIC)
        assertThat(classifyHospitalOwnership("Hôpital PUBLIQUE", null)).isEqualTo(HospitalOwnership.PUBLIC)
        assertThat(classifyHospitalOwnership("Clínica Privada Sur", null)).isEqualTo(HospitalOwnership.PRIVATE)
        assertThat(classifyHospitalOwnership("Private Hospital", "hospital")).isEqualTo(HospitalOwnership.PRIVATE)
        assertThat(classifyHospitalOwnership("Ospedale Privato", null)).isEqualTo(HospitalOwnership.PRIVATE)
        assertThat(classifyHospitalOwnership("Hospital General", "hospital")).isEqualTo(HospitalOwnership.UNKNOWN)
        assertThat(classifyHospitalOwnership("Hospital General", "public_hospital")).isEqualTo(HospitalOwnership.PUBLIC)
    }

    // Ported from the macOS EmergencyCareProviderTests, around Torrevieja (Spain). Unlike macOS the
    // text queries run once at 100 km, and only when the nearby searches found no hospital at all.

    @Test
    fun `no valid coordinates yields no hospitals`() = runTest {
        val client = ScriptedPlacesClient(nearby = {
            PlacesNearbySearchOutcome.Success(listOf(torrevieja("Broken Hospital", 190.0, -500.0)))
        })

        val snapshot = GooglePlacesEmergencyCareProvider(client).nearbyHospital(torreviejaRequest())

        assertThat(snapshot.status).isEqualTo(EmergencyCareStatus.UNAVAILABLE)
        assertThat(snapshot.facilities).isEmpty()
    }

    @Test
    fun `reports the last expanded radius when every search returns no hospitals`() = runTest {
        val client = ScriptedPlacesClient()

        val snapshot = GooglePlacesEmergencyCareProvider(client).nearbyHospital(torreviejaRequest())

        assertThat(snapshot.status).isEqualTo(EmergencyCareStatus.UNAVAILABLE)
        assertThat(snapshot.facilities).isEmpty()
        assertThat(snapshot.searchRadiusKilometers).isEqualTo(100.0)
        assertThat(client.nearbyRadiiKilometers).containsExactly(25, 50, 100).inOrder()
        // One round of the five macOS queries, at the widest radius only.
        assertThat(client.textCalls.map { it.first }.toSet()).containsExactly(100)
        assertThat(client.textCalls.map { it.second })
            .containsExactly("hospital", "emergency room", "urgent care", "urgencias", "emergencias")
    }

    @Test
    fun `the worst case is eight requests and the usual case is one`() = runTest {
        val empty = ScriptedPlacesClient()
        GooglePlacesEmergencyCareProvider(empty).nearbyHospital(torreviejaRequest())
        assertThat(empty.nearbyRadiiKilometers.size + empty.textCalls.size).isEqualTo(8)

        val full = ScriptedPlacesClient(nearby = {
            PlacesNearbySearchOutcome.Success(
                listOf(
                    torrevieja("Hospital A", 37.9820, -0.6750),
                    torrevieja("Hospital B", 37.9900, -0.6800),
                    torrevieja("Hospital C", 38.0000, -0.6900),
                ),
            )
        })
        GooglePlacesEmergencyCareProvider(full).nearbyHospital(torreviejaRequest())
        assertThat(full.nearbyRadiiKilometers).containsExactly(25)
        assertThat(full.textCalls).isEmpty()
    }

    @Test
    fun `expands the search radius until it finds the preferred three hospitals`() = runTest {
        val quiron = torrevieja("Hospital Quironsalud Torrevieja", 37.9820, -0.6750)
        val vegaBaja = torrevieja("Hospital Vega Baja", 38.0850, -0.9440, locality = "Orihuela")
        val alicante = torrevieja("Hospital General Universitario de Alicante", 38.3615, -0.4818, locality = "Alicante")
        val client = ScriptedPlacesClient(nearby = { radius ->
            PlacesNearbySearchOutcome.Success(
                when (radius) {
                    25 -> listOf(quiron)
                    50 -> listOf(quiron, vegaBaja)
                    else -> listOf(quiron, vegaBaja, alicante)
                },
            )
        })

        val snapshot = GooglePlacesEmergencyCareProvider(client).nearbyHospital(torreviejaRequest())

        assertThat(snapshot.status).isEqualTo(EmergencyCareStatus.READY)
        assertThat(snapshot.facilities).hasSize(3)
        assertThat(snapshot.searchRadiusKilometers).isEqualTo(100.0)
        assertThat(snapshot.detail).isEqualTo("Nearby emergency hospitals within 100 km.")
        assertThat(client.nearbyRadiiKilometers).containsExactly(25, 50, 100).inOrder()
        assertThat(client.textCalls).isEmpty()
        assertThat(snapshot.facilities.map { it.distanceKilometers }).isInOrder()
    }

    @Test
    fun `falls back to the text search when the nearby searches find nothing`() = runTest {
        val quiron = torrevieja("Hospital Quironsalud Torrevieja", 37.9820, -0.6750)
        val vegaBaja = torrevieja("Hospital Vega Baja", 38.0850, -0.9440, locality = "Orihuela")
        val alicante = torrevieja("Hospital General Universitario de Alicante", 38.3615, -0.4818, locality = "Alicante")
        val client = ScriptedPlacesClient(
            text = { radius, query ->
                PlacesNearbySearchOutcome.Success(
                    if (radius == 100 && query == "hospital") listOf(quiron, vegaBaja, alicante) else emptyList(),
                )
            },
        )

        val snapshot = GooglePlacesEmergencyCareProvider(client).nearbyHospital(torreviejaRequest())

        assertThat(snapshot.facilities).hasSize(3)
        assertThat(snapshot.searchRadiusKilometers).isEqualTo(100.0)
        assertThat(snapshot.facilities.map { it.name }).containsAtLeast(
            "Hospital Vega Baja",
            "Hospital General Universitario de Alicante",
        )
    }

    @Test
    fun `does not run the text search when a nearby search found any hospital`() = runTest {
        val client = ScriptedPlacesClient(nearby = {
            PlacesNearbySearchOutcome.Success(listOf(torrevieja("Hospital Quironsalud Torrevieja", 37.9820, -0.6750)))
        })

        val snapshot = GooglePlacesEmergencyCareProvider(client).nearbyHospital(torreviejaRequest())

        assertThat(snapshot.facilities).hasSize(1)
        assertThat(snapshot.searchRadiusKilometers).isEqualTo(25.0)
        assertThat(client.nearbyRadiiKilometers).containsExactly(25, 50, 100).inOrder()
        assertThat(client.textCalls).isEmpty()
    }

    @Test
    fun `stops expanding after fifty kilometers when the preferred count is reached`() = runTest {
        val quiron = torrevieja("Hospital Quironsalud Torrevieja", 37.9820, -0.6750)
        val vegaBaja = torrevieja("Hospital Vega Baja", 38.0850, -0.9440, locality = "Orihuela")
        val elche = torrevieja("Hospital General Universitario de Elche", 38.2681, -0.6990, locality = "Elche")
        val client = ScriptedPlacesClient(nearby = { radius ->
            PlacesNearbySearchOutcome.Success(if (radius == 25) listOf(quiron) else listOf(quiron, vegaBaja, elche))
        })

        val snapshot = GooglePlacesEmergencyCareProvider(client).nearbyHospital(torreviejaRequest())

        assertThat(snapshot.facilities).hasSize(3)
        assertThat(snapshot.searchRadiusKilometers).isEqualTo(50.0)
        assertThat(client.nearbyRadiiKilometers).containsExactly(25, 50).inOrder()
        assertThat(client.textCalls).isEmpty()
    }

    @Test
    fun `deduplicates the same hospital returned by several text queries`() = runTest {
        val client = ScriptedPlacesClient(
            text = { _, query ->
                PlacesNearbySearchOutcome.Success(
                    when (query) {
                        "hospital" -> listOf(
                            torrevieja("Hospital Quironsalud Torrevieja", 37.9820, -0.6750, address = null),
                            torrevieja("Hospital Vega Baja", 38.0150, -0.7350, locality = "Orihuela"),
                        )
                        "emergency room" -> listOf(
                            torrevieja("Hospital Quironsalud Torrevieja", 37.9820, -0.6750, address = "Partida de La Loma"),
                            torrevieja("Hospital Universitario del Vinalopo", 38.1150, -0.6905, locality = "Elche"),
                        )
                        else -> emptyList()
                    },
                )
            },
        )

        val snapshot = GooglePlacesEmergencyCareProvider(client).nearbyHospital(torreviejaRequest())

        assertThat(snapshot.facilities).hasSize(3)
        assertThat(snapshot.facilities.map { it.name }.toSet()).hasSize(3)
        assertThat(snapshot.facilities.single { it.name == "Hospital Quironsalud Torrevieja" }.address)
            .isEqualTo("Partida de La Loma")
    }

    @Test
    fun `rejects veterinary and unrelated text search results`() = runTest {
        val client = ScriptedPlacesClient(
            text = { _, query ->
                PlacesNearbySearchOutcome.Success(
                    if (query == "hospital") {
                        listOf(
                            torrevieja("Hospital Quironsalud Torrevieja", 37.9820, -0.6750),
                            torrevieja("Hospital Veterinario de Torrevieja", 37.9950, -0.6880),
                            torrevieja("Clinica Animal Sur", 37.9960, -0.6890, type = "veterinary_care"),
                            torrevieja("Farmacia Central", 37.9900, -0.6800, type = "pharmacy"),
                            torrevieja("Hospital Vega Baja", 38.0150, -0.7350, locality = "Orihuela"),
                            torrevieja("Urgencias Torrevieja", 37.9830, -0.6810),
                        )
                    } else {
                        emptyList()
                    },
                )
            },
        )

        val snapshot = GooglePlacesEmergencyCareProvider(client).nearbyHospital(torreviejaRequest())

        assertThat(snapshot.facilities).hasSize(3)
        assertThat(snapshot.facilities.map { it.name }).containsNoneOf(
            "Hospital Veterinario de Torrevieja", "Clinica Animal Sur", "Farmacia Central",
        )
    }

    @Test
    fun `text hits beyond the widest radius do not count`() = runTest {
        val client = ScriptedPlacesClient(
            text = { _, query ->
                PlacesNearbySearchOutcome.Success(
                    if (query == "hospital") {
                        // Valencia, about 150 km away.
                        listOf(torrevieja("Hospital La Fe", 39.4440, -0.3760, locality = "Valencia"))
                    } else {
                        emptyList()
                    },
                )
            },
        )

        val snapshot = GooglePlacesEmergencyCareProvider(client).nearbyHospital(torreviejaRequest())

        assertThat(snapshot.status).isEqualTo(EmergencyCareStatus.UNAVAILABLE)
        assertThat(snapshot.facilities).isEmpty()
    }

    @Test
    fun `prefers the more specific alias when two results describe the same hospital`() = runTest {
        val client = ScriptedPlacesClient(
            text = { _, query ->
                PlacesNearbySearchOutcome.Success(
                    when (query) {
                        "hospital" -> listOf(
                            torrevieja("Hospital Quirón", 37.9820, -0.6750, address = null),
                            torrevieja("Hospital Vega Baja", 38.0150, -0.7350, locality = "Orihuela"),
                            torrevieja("Urgencias Torrevieja", 37.9830, -0.6810),
                        )
                        "emergency room" -> listOf(
                            torrevieja("Hospital Quirónsalud Torrevieja", 37.9823, -0.6752, address = "Partida de La Loma"),
                        )
                        else -> emptyList()
                    },
                )
            },
        )

        val snapshot = GooglePlacesEmergencyCareProvider(client).nearbyHospital(torreviejaRequest())

        assertThat(snapshot.facilities).hasSize(3)
        assertThat(snapshot.facilities.map { it.name }).doesNotContain("Hospital Quirón")
        assertThat(snapshot.facilities.single { it.name == "Hospital Quirónsalud Torrevieja" }.address)
            .isEqualTo("Partida de La Loma")
    }

    @Test
    fun `unknown ownership results from the text search do not break the public and private preference`() = runTest {
        val client = ScriptedPlacesClient(
            text = { _, query ->
                PlacesNearbySearchOutcome.Success(
                    when (query) {
                        "urgencias" -> listOf(torrevieja("Urgencias Torrevieja", 37.9830, -0.6810))
                        "hospital" -> listOf(
                            torrevieja("Hospital Publico Vega Baja", 38.0120, -0.7200, locality = "Orihuela"),
                            torrevieja("Hospital Privado de Torrevieja", 38.0020, -0.6900),
                        )
                        else -> emptyList()
                    },
                )
            },
        )

        val snapshot = GooglePlacesEmergencyCareProvider(client).nearbyHospital(torreviejaRequest())

        assertThat(snapshot.facilities.map { it.name }).containsExactly(
            "Urgencias Torrevieja", "Hospital Privado de Torrevieja", "Hospital Publico Vega Baja",
        ).inOrder()
        assertThat(snapshot.facilities.map { it.ownership }).containsExactly(
            HospitalOwnership.UNKNOWN, HospitalOwnership.PRIVATE, HospitalOwnership.PUBLIC,
        ).inOrder()
        assertThat(snapshot.searchRadiusKilometers).isEqualTo(100.0)
    }

    @Test
    fun `keeps the best successful results when a later expanded search fails`() = runTest {
        val quiron = torrevieja("Hospital Quironsalud Torrevieja", 37.9820, -0.6750)
        val vegaBaja = torrevieja("Hospital Vega Baja", 38.0850, -0.9440, locality = "Orihuela")
        val client = ScriptedPlacesClient(nearby = { radius ->
            when (radius) {
                25 -> PlacesNearbySearchOutcome.Success(listOf(quiron))
                50 -> PlacesNearbySearchOutcome.Success(listOf(quiron, vegaBaja))
                else -> PlacesNearbySearchOutcome.Error("network unavailable")
            }
        })

        val snapshot = GooglePlacesEmergencyCareProvider(client).nearbyHospital(torreviejaRequest())

        assertThat(snapshot.status).isEqualTo(EmergencyCareStatus.READY)
        assertThat(snapshot.facilities).hasSize(2)
        assertThat(snapshot.searchRadiusKilometers).isEqualTo(50.0)
        assertThat(snapshot.detail).isEqualTo("Nearby emergency hospitals within 50 km.")
        assertThat(client.nearbyRadiiKilometers).containsExactly(25, 50, 100).inOrder()
        assertThat(client.textCalls).isEmpty()
    }

    @Test
    fun `reports an error when every search fails`() = runTest {
        val client = ScriptedPlacesClient(
            nearby = { PlacesNearbySearchOutcome.Error("offline") },
            text = { _, _ -> PlacesNearbySearchOutcome.Error("offline") },
        )

        val snapshot = GooglePlacesEmergencyCareProvider(client).nearbyHospital(torreviejaRequest())

        assertThat(snapshot.status).isEqualTo(EmergencyCareStatus.ERROR)
        assertThat(snapshot.note).isEqualTo("offline")
        assertThat(snapshot.facilities).isEmpty()
    }

    @Test
    fun `a missing configuration stops after the first request`() = runTest {
        val client = ScriptedPlacesClient(nearby = { PlacesNearbySearchOutcome.ConfigurationRequired("no key") })

        val snapshot = GooglePlacesEmergencyCareProvider(client).nearbyHospital(torreviejaRequest())

        assertThat(snapshot.status).isEqualTo(EmergencyCareStatus.CONFIGURATION_REQUIRED)
        assertThat(client.nearbyRadiiKilometers).hasSize(1)
        assertThat(client.textCalls).isEmpty()
    }
}

private fun helsinkiRequest() = EmergencyCareSearchRequest(
    latitude = 60.1699,
    longitude = 24.9384,
    countryCode = "FI",
    countryName = "Finland",
    locationSource = EmergencyCareLocationSource.DEVICE,
)

private fun torreviejaRequest() = EmergencyCareSearchRequest(
    latitude = 37.9780,
    longitude = -0.6820,
    countryCode = "ES",
    countryName = "Spain",
    locationSource = EmergencyCareLocationSource.DEVICE,
)

private fun torrevieja(
    name: String,
    latitude: Double,
    longitude: Double,
    address: String? = "Calle 1",
    locality: String? = "Torrevieja",
    type: String = "hospital",
) = PlacesNearbySearchPlace(
    placeId = "$name|$latitude|$longitude",
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude,
    primaryType = type,
    locality = locality,
)

private fun kilometersToDegrees(kilometers: Double): Double = kilometers / 111.195

private fun place(id: String, name: String, latitude: Double) = PlacesNearbySearchPlace(
    placeId = id,
    name = name,
    address = null,
    latitude = latitude,
    longitude = -5.6069,
    primaryType = "hospital",
)

private fun facility(
    id: String,
    distanceKilometers: Double,
    ownership: HospitalOwnership,
    name: String = "Hospital $id",
) = EmergencyCareFacility(
    placeId = id,
    name = name,
    address = null,
    distanceKilometers = distanceKilometers,
    latitude = 36.0 + distanceKilometers / 111.0,
    longitude = -5.6,
    ownership = ownership,
)

private class FakePlacesNearbySearchClient(
    private val outcome: PlacesNearbySearchOutcome,
) : PlacesNearbySearchClient {
    val requests = mutableListOf<PlacesNearbySearchRequest>()

    override suspend fun searchNearbyHospitals(request: PlacesNearbySearchRequest): PlacesNearbySearchOutcome {
        requests += request
        return outcome
    }

    override suspend fun searchHospitalsByText(request: PlacesTextSearchRequest): PlacesNearbySearchOutcome =
        PlacesNearbySearchOutcome.Success(emptyList())
}

/** Answers per radius (and per query for text search) like the real client, and records every call. */
private class ScriptedPlacesClient(
    private val nearby: (radiusKilometers: Int) -> PlacesNearbySearchOutcome = { PlacesNearbySearchOutcome.Success(emptyList()) },
    private val text: (radiusKilometers: Int, query: String) -> PlacesNearbySearchOutcome =
        { _, _ -> PlacesNearbySearchOutcome.Success(emptyList()) },
) : PlacesNearbySearchClient {
    val nearbyRadiiKilometers = java.util.Collections.synchronizedList(mutableListOf<Int>())
    val textCalls = java.util.Collections.synchronizedList(mutableListOf<Pair<Int, String>>())

    override suspend fun searchNearbyHospitals(request: PlacesNearbySearchRequest): PlacesNearbySearchOutcome {
        val radius = (request.radiusMeters / 1_000).toInt()
        nearbyRadiiKilometers += radius
        return nearby(radius)
    }

    override suspend fun searchHospitalsByText(request: PlacesTextSearchRequest): PlacesNearbySearchOutcome {
        val radius = (request.radiusMeters / 1_000).toInt()
        textCalls += radius to request.query
        return text(radius, request.query)
    }
}
