package com.iloapps.nomaddashboard.core.data.fuel

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.network.api.FranceFuelPriceService
import com.iloapps.nomaddashboard.core.network.api.ItalyFuelPriceService
import com.iloapps.nomaddashboard.core.network.api.SpainFuelPriceService
import com.iloapps.nomaddashboard.core.network.api.TankerkoenigService
import com.iloapps.nomaddashboard.core.network.model.FranceFuelGeometry
import com.iloapps.nomaddashboard.core.network.model.FranceFuelRecord
import com.iloapps.nomaddashboard.core.network.model.FranceFuelRecordsResponse
import com.iloapps.nomaddashboard.core.network.model.SpainFuelResponse
import com.iloapps.nomaddashboard.core.network.model.SpainFuelStation
import com.iloapps.nomaddashboard.core.network.model.TankerkoenigListResponse
import com.iloapps.nomaddashboard.core.network.model.TankerkoenigStation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

class DefaultFuelPriceProviderTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `spain provider picks cheapest nearby station and distance tie-break`() = runTest {
        val provider = provider(
            spainFuelPriceService = object : SpainFuelPriceService {
                override suspend fun stations(): SpainFuelResponse =
                    SpainFuelResponse(
                        fetchedAt = "07/04/2026 11:43:36",
                        stations = listOf(
                            SpainFuelStation(
                                identifier = "1",
                                stationName = "Alpha Fuel",
                                address = "A Street",
                                municipality = "Valencia",
                                latitude = "39,4699",
                                longitude = "-0,3763",
                                dieselPrice = "1,600",
                                gasoline95E5Price = "1,540",
                            ),
                            SpainFuelStation(
                                identifier = "2",
                                stationName = "Bravo Fuel",
                                address = "B Street",
                                municipality = "Valencia",
                                latitude = "39,4800",
                                longitude = "-0,3800",
                                dieselPrice = "1,500",
                                gasoline95E5Price = "1,560",
                            ),
                            SpainFuelStation(
                                identifier = "3",
                                stationName = "Charlie Fuel",
                                address = "C Street",
                                municipality = "Valencia",
                                latitude = "39,5000",
                                longitude = "-0,3900",
                                dieselPrice = "1,500",
                                gasoline95E10Price = "1,530",
                            ),
                        ),
                    )
            },
        )

        val snapshot = provider.prices(
            FuelSearchRequest(
                latitude = 39.4699,
                longitude = -0.3763,
                countryCode = "ES",
                countryName = "Spain",
            ),
        )

        assertThat(snapshot.status).isEqualTo(FuelPriceStatus.READY)
        assertThat(snapshot.diesel?.stationName).isEqualTo("Bravo Fuel")
        assertThat(snapshot.gasoline?.stationName).isEqualTo("Charlie Fuel")
        assertThat(snapshot.detail).isEqualTo("Cheapest prices within 50 km.")
    }

    @Test
    fun `france provider maps government records payload`() = runTest {
        val provider = provider(
            franceFuelPriceService = object : FranceFuelPriceService {
                override suspend fun records(
                    select: String,
                    where: String,
                    limit: Int,
                    offset: Int,
                ): FranceFuelRecordsResponse =
                    if (offset == 0) {
                        FranceFuelRecordsResponse(
                            totalCount = 1,
                            results = listOf(
                                FranceFuelRecord(
                                    id = 95480003,
                                    address = "AUTOROUTE A15 - AIRE DE PIERRELAYE",
                                    locality = "Pierrelaye",
                                    geometry = FranceFuelGeometry(longitude = 2.149, latitude = 49.013),
                                    pricesJson = """[{"@nom":"Gazole","@maj":"2026-04-07 09:00:00","@valeur":"1.919"},{"@nom":"SP98","@maj":"2026-04-07 09:00:00","@valeur":"2.039"}]""",
                                ),
                            ),
                        )
                    } else {
                        FranceFuelRecordsResponse()
                    }
            },
        )

        val snapshot = provider.prices(
            FuelSearchRequest(
                latitude = 48.8566,
                longitude = 2.3522,
                countryCode = "FR",
                countryName = "France",
            ),
        )

        assertThat(snapshot.status).isEqualTo(FuelPriceStatus.READY)
        assertThat(snapshot.diesel?.pricePerLiter).isEqualTo(1.919)
        assertThat(snapshot.gasoline?.pricePerLiter).isEqualTo(2.039)
        assertThat(snapshot.gasoline?.locality).isEqualTo("Pierrelaye")
    }

    @Test
    fun `italy provider merges pipe separated station and price files`() = runTest {
        val provider = provider(
            italyFuelPriceService = object : ItalyFuelPriceService {
                override suspend fun stationCatalog(): ResponseBody =
                    """
                    Estrazione del 2026-04-06
                    idImpianto|Bandiera|Nome Impianto|Indirizzo|Comune|Latitudine|Longitudine
                    10|Q8|Roma Centro|Via Roma 1|Roma|41.9028|12.4964
                    11|Esso|Roma Sud|Via Appia 4|Roma|41.8900|12.5000
                    """.trimIndent().toResponseBody()

                override suspend fun dailyPrices(): ResponseBody =
                    """
                    Estrazione del 2026-04-06
                    idImpianto|descCarburante|prezzo|isSelf|dtComu
                    10|Gasolio|1.799|1|06/04/2026 08:00:00
                    10|Super senza piombo|1.899|1|06/04/2026 08:00:00
                    11|Gasolio|1.759|0|06/04/2026 08:00:00
                    11|Benzina|1.949|0|06/04/2026 08:00:00
                    """.trimIndent().toResponseBody()
            },
        )

        val snapshot = provider.prices(
            FuelSearchRequest(
                latitude = 41.9028,
                longitude = 12.4964,
                countryCode = "IT",
                countryName = "Italy",
            ),
        )

        assertThat(snapshot.status).isEqualTo(FuelPriceStatus.READY)
        assertThat(snapshot.diesel?.stationName).isEqualTo("Esso")
        assertThat(snapshot.gasoline?.stationName).isEqualTo("Q8")
        assertThat(snapshot.note).isEqualTo("Italian prices come from the daily 8:00 update.")
    }

    @Test
    fun `italy provider accepts semicolon fallback csv`() = runTest {
        val provider = provider(
            italyFuelPriceService = object : ItalyFuelPriceService {
                override suspend fun stationCatalog(): ResponseBody =
                    """
                    Estrazione del 2026-04-06
                    idImpianto;Bandiera;Nome Impianto;Indirizzo;Comune;Latitudine;Longitudine
                    20;IP;Milano Nord;Corso Milano 1;Milano;45.4642;9.1900
                    """.trimIndent().toResponseBody()

                override suspend fun dailyPrices(): ResponseBody =
                    """
                    Estrazione del 2026-04-06
                    idImpianto;descCarburante;prezzo;isSelf;dtComu
                    20;Gasolio;1.699;1;06/04/2026 08:00:00
                    20;Benzina;1.829;1;06/04/2026 08:00:00
                    """.trimIndent().toResponseBody()
            },
        )

        val snapshot = provider.prices(
            FuelSearchRequest(
                latitude = 45.4642,
                longitude = 9.1900,
                countryCode = "IT",
                countryName = "Italy",
            ),
        )

        assertThat(snapshot.status).isEqualTo(FuelPriceStatus.READY)
        assertThat(snapshot.diesel?.stationName).isEqualTo("IP")
        assertThat(snapshot.gasoline?.stationName).isEqualTo("IP")
    }

    @Test
    fun `germany provider reports missing local Tankerkonig config`() = runTest {
        val provider = provider(localConfig = FuelProviderLocalConfig(tankerkoenigApiKey = ""))

        val snapshot = provider.prices(
            FuelSearchRequest(
                latitude = 52.52,
                longitude = 13.405,
                countryCode = "DE",
                countryName = "Germany",
            ),
        )

        assertThat(snapshot.status).isEqualTo(FuelPriceStatus.CONFIGURATION_REQUIRED)
        assertThat(snapshot.detail).contains("NOMAD_TANKERKOENIG_API_KEY")
    }

    @Test
    fun `germany provider merges duplicate stations across tile queries`() = runTest {
        val provider = provider(
            tankerkoenigService = object : TankerkoenigService {
                override suspend fun stations(
                    latitude: Double,
                    longitude: Double,
                    radiusKilometers: Int,
                    sort: String,
                    type: String,
                    apiKey: String,
                ): TankerkoenigListResponse =
                    TankerkoenigListResponse(
                        ok = true,
                        stations = listOf(
                            TankerkoenigStation(
                                identifier = "station-1",
                                brand = "Aral",
                                street = "Unter den Linden",
                                houseNumber = "1",
                                locality = "Berlin",
                                latitude = 52.521,
                                longitude = 13.404,
                                dieselPrice = if (latitude == 52.52) 1.709 else 1.699,
                                e10Price = 1.789,
                            ),
                        ),
                    )
            },
            localConfig = FuelProviderLocalConfig(tankerkoenigApiKey = "user-key-123"),
        )

        val snapshot = provider.prices(
            FuelSearchRequest(
                latitude = 52.52,
                longitude = 13.405,
                countryCode = "DE",
                countryName = "Germany",
            ),
        )

        assertThat(snapshot.status).isEqualTo(FuelPriceStatus.READY)
        assertThat(snapshot.diesel?.pricePerLiter).isEqualTo(1.699)
        assertThat(snapshot.gasoline?.stationName).isEqualTo("Aral")
    }

    private fun provider(
        spainFuelPriceService: SpainFuelPriceService = object : SpainFuelPriceService {
            override suspend fun stations(): SpainFuelResponse = SpainFuelResponse()
        },
        franceFuelPriceService: FranceFuelPriceService = object : FranceFuelPriceService {
            override suspend fun records(
                select: String,
                where: String,
                limit: Int,
                offset: Int,
            ): FranceFuelRecordsResponse = FranceFuelRecordsResponse()
        },
        italyFuelPriceService: ItalyFuelPriceService = object : ItalyFuelPriceService {
            override suspend fun stationCatalog(): ResponseBody = "".toResponseBody()
            override suspend fun dailyPrices(): ResponseBody = "".toResponseBody()
        },
        tankerkoenigService: TankerkoenigService = object : TankerkoenigService {
            override suspend fun stations(
                latitude: Double,
                longitude: Double,
                radiusKilometers: Int,
                sort: String,
                type: String,
                apiKey: String,
            ): TankerkoenigListResponse = TankerkoenigListResponse()
        },
        localConfig: FuelProviderLocalConfig = FuelProviderLocalConfig(tankerkoenigApiKey = ""),
    ): DefaultFuelPriceProvider =
        DefaultFuelPriceProvider(
            json = json,
            spainFuelPriceService = spainFuelPriceService,
            franceFuelPriceService = franceFuelPriceService,
            italyFuelPriceService = italyFuelPriceService,
            tankerkoenigService = tankerkoenigService,
            localConfig = localConfig,
        )
}
