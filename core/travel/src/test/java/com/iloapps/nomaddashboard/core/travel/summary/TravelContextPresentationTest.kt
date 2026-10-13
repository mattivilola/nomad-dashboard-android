package com.iloapps.nomaddashboard.core.travel.summary

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.FuelStationPrice
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import java.util.Locale
import org.junit.Test

class TravelContextPresentationTest {
    private val place = TravelContextSnapshot(
        publicIp = "203.0.113.42",
        city = "Valencia",
        country = "Spain",
        timeZoneId = "Europe/Madrid",
    )

    private fun of(
        connectivity: ConnectivitySnapshot = ConnectivitySnapshot(),
        context: TravelContextSnapshot = place,
        locationEnabled: Boolean = true,
    ) = TravelContextPresentation.of(connectivity, context, locationEnabled)

    @Test
    fun `wifi name signal and rate share one row`() {
        val presentation = of(ConnectivitySnapshot(wifiName = "Nomad Hub", wifiSignalDbm = -56, wifiLinkSpeedMbps = 720))

        assertThat(presentation.wifi).isEqualTo("Nomad Hub · −56 dBm · 720 Mbps")
        assertThat(presentation.wifiHelp).isEqualTo("Signal −56 dBm · Transmit rate 720 Mbps")
    }

    @Test
    fun `wifi without a name or a connection stays honest`() {
        assertThat(of(ConnectivitySnapshot(wifiSignalDbm = -60)).wifi).isEqualTo("Connected · −60 dBm")

        val none = of()
        assertThat(none.wifi).isEqualTo("Not connected")
        assertThat(none.wifiHelp).isNull()
    }

    @Test
    fun `the vpn row says active or inactive`() {
        assertThat(of(ConnectivitySnapshot(vpnActive = true)).vpn).isEqualTo("Active")
        assertThat(of(ConnectivitySnapshot(vpnActive = false)).vpn).isEqualTo("Inactive")
    }

    @Test
    fun `location carries the time zone`() {
        assertThat(of().location).isEqualTo("Valencia, Spain · Europe/Madrid")
        assertThat(of(context = TravelContextSnapshot(timeZoneId = "Europe/Madrid"), locationEnabled = false).location)
            .isEqualTo("Location off · Europe/Madrid")
        assertThat(of(context = TravelContextSnapshot(timeZoneId = "Europe/Madrid")).location)
            .isEqualTo("Refreshing… · Europe/Madrid")
    }

    @Test
    fun `public ip falls back to status text`() {
        assertThat(of().publicIp).isEqualTo("203.0.113.42")
        assertThat(of().hasPublicIp).isTrue()

        val missing = of(context = TravelContextSnapshot())
        assertThat(missing.publicIp).isEqualTo("Refreshing…")
        assertThat(missing.hasPublicIp).isFalse()
    }

    @Test
    fun `decibel text uses a typographic minus`() {
        assertThat(TravelContextPresentation.decibelText(-56)).isEqualTo("−56 dBm")
        assertThat(TravelContextPresentation.decibelText(0)).isEqualTo("0 dBm")
    }

    // region Saved essentials

    @Test
    fun `weather summary uses the shared temperature format`() {
        assertThat(
            OfflineEssentialsPresentation.weatherSummary(
                WeatherSnapshot(currentTemperatureCelsius = 18.6, conditionDescription = "Sunny"),
            ),
        ).isEqualTo("19° · Sunny")
    }

    @Test
    fun `weather summary falls back to the condition without a temperature`() {
        assertThat(OfflineEssentialsPresentation.weatherSummary(WeatherSnapshot(conditionDescription = "Fog")))
            .isEqualTo("Fog")
    }

    @Test
    fun `fuel line uses the pump price format with a per liter suffix`() {
        val station = FuelStationPrice(
            fuelType = FuelType.DIESEL,
            stationName = "Repsol",
            pricePerLiter = 1.4294,
            currencyCode = "EUR",
            distanceKilometers = 2.0,
            latitude = 40.0,
            longitude = -3.0,
        )

        val line = OfflineEssentialsPresentation.fuelLine(station, Locale.US)

        assertThat(line).isEqualTo("Repsol · €1.429/L")
        assertThat(line).contains("1.429")
    }

    // endregion
}
