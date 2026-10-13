package com.iloapps.nomaddashboard.core.travel.cards

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import org.junit.Test

class TravelContextCardPresentationTest {
    private val connectivity = ConnectivitySnapshot(
        wifiName = "Nomad Hub",
        wifiSignalDbm = -56,
        wifiLinkSpeedMbps = 720,
        wifiFrequencyMhz = 5180,
        timeZoneId = "Europe/Madrid",
    )

    private val network = TravelContextSnapshot(
        publicIp = "203.0.113.42",
        city = "Valencia",
        region = "Valencia",
        country = "Spain",
        countryCode = "ES",
        timeZoneId = "Europe/Madrid",
    )

    private fun of(
        connectivity: ConnectivitySnapshot = this.connectivity,
        context: TravelContextSnapshot = network,
        publicIpEnabled: Boolean = true,
        permission: Boolean = true,
        checking: Boolean = false,
    ) = TravelContextCardPresentation.of(connectivity, context, publicIpEnabled, permission, checking)

    @Test
    fun `the four rows read like the macOS card`() {
        val card = of()

        assertThat(card.publicIp).isEqualTo("203.0.113.42")
        assertThat(card.wifi).isEqualTo("Nomad Hub · −56 dBm · 720 Mbps")
        assertThat(card.vpn).isEqualTo("Inactive")
        assertThat(card.location).isEqualTo("Valencia, Spain · Europe/Madrid")
    }

    @Test
    fun `the location row prefers the device's own place`() {
        val card = of(
            context = network.copy(
                deviceCity = "Alicante",
                deviceCountry = "Spain",
                deviceCountryCode = "ES",
            ),
        )

        assertThat(card.location).isEqualTo("Alicante, Spain · Europe/Madrid")
    }

    @Test
    fun `a device place with coordinates only still names a location`() {
        val card = of(context = network.copy(deviceLatitude = 38.3452, deviceLongitude = -0.481))

        assertThat(card.location).isEqualTo("38.345, -0.481 · Europe/Madrid")
    }

    @Test
    fun `a switched off public ip lookup says so instead of refreshing forever`() {
        assertThat(of(context = TravelContextSnapshot(), publicIpEnabled = false).publicIp).isEqualTo("Off in Settings")
        assertThat(of(context = TravelContextSnapshot(), publicIpEnabled = true).publicIp).isEqualTo("Refreshing…")
        assertThat(of(publicIpEnabled = false).publicIp).isEqualTo("203.0.113.42")
    }

    @Test
    fun `alignment compares the device place with the network place`() {
        val device = network.copy(deviceCity = "Valencia", deviceCountry = "Spain", deviceCountryCode = "ES")

        assertThat(TravelContextCardPresentation.alignment(device)).isEqualTo(LocationAlignment.ALIGNED)
        assertThat(TravelContextCardPresentation.alignment(device.copy(deviceCity = "Madrid"))).isEqualTo(LocationAlignment.NEARBY)
        assertThat(TravelContextCardPresentation.alignment(device.copy(deviceCountryCode = "PT", deviceCountry = "Portugal")))
            .isEqualTo(LocationAlignment.MISMATCH)
        assertThat(TravelContextCardPresentation.alignment(device.copy(deviceCity = null))).isEqualTo(LocationAlignment.ALIGNED)
    }

    @Test
    fun `alignment needs both places`() {
        assertThat(TravelContextCardPresentation.alignment(network)).isNull()
        assertThat(TravelContextCardPresentation.alignment(TravelContextSnapshot(deviceCountry = "Spain"))).isNull()
    }

    @Test
    fun `a mismatch is a caution and an alignment is good`() {
        assertThat(LocationAlignment.MISMATCH.tone).isEqualTo(CardTone.CAUTION)
        assertThat(LocationAlignment.ALIGNED.tone).isEqualTo(CardTone.GOOD)
        assertThat(LocationAlignment.NEARBY.label).isEqualTo("Nearby")
    }

    @Test
    fun `the comparison keeps the device and network places side by side`() {
        val card = of(
            context = network.copy(deviceCity = "Lisbon", deviceCountry = "Portugal", deviceCountryCode = "PT", deviceRegion = "Lisboa"),
        )

        val rows = card.comparison.associateBy { it.label }
        assertThat(rows["Device"]?.value).isEqualTo("Lisbon, Portugal")
        assertThat(rows["Device"]?.status).isEqualTo("Ready")
        assertThat(rows["Device"]?.supporting).isEqualTo("Lisboa · Portugal")
        assertThat(rows["Public IP"]?.value).isEqualTo("Valencia, Spain")
        assertThat(rows["Public IP"]?.status).isEqualTo("Live")
        assertThat(rows["Time zone"]?.value).isEqualTo("Europe/Madrid")
        assertThat(rows["Signal"]?.value).isEqualTo("RSSI -56 · 720 Mbps · 5 GHz")
        assertThat(rows["VPN"]?.value).isEqualTo("Inactive")
    }

    @Test
    fun `the device row asks for permission when it is missing`() {
        val device = of(context = TravelContextSnapshot(), permission = false).comparison.first { it.label == "Device" }

        assertThat(device.value).isEqualTo("Location unavailable")
        assertThat(device.status).isEqualTo("Permission")
        assertThat(device.statusTone).isEqualTo(CardTone.CAUTION)
    }

    @Test
    fun `the device row says checking while the startup lookup runs`() {
        val device = of(context = TravelContextSnapshot(), checking = true).comparison.first { it.label == "Device" }

        assertThat(device.status).isEqualTo("Checking")
        assertThat(device.statusTone).isEqualTo(CardTone.NEUTRAL)
    }

    @Test
    fun `different time zones are both named`() {
        val rows = of(context = network.copy(timeZoneId = "Atlantic/Canary")).comparison.associateBy { it.label }

        assertThat(rows["Time zone"]?.value).isEqualTo("Device Europe/Madrid · IP Atlantic/Canary")
    }

    @Test
    fun `the signal row is left out without wifi readings`() {
        val labels = of(connectivity = ConnectivitySnapshot()).comparison.map { it.label }

        assertThat(labels).doesNotContain("Signal")
    }

    @Test
    fun `an active vpn explains why the ip place may differ`() {
        val card = of(connectivity = connectivity.copy(vpnActive = true))

        assertThat(card.vpn).isEqualTo("Active")
        assertThat(card.comparison.first { it.label == "VPN" }.supporting)
            .isEqualTo("Public IP location may differ from your physical device location.")
    }

    @Test
    fun `wifi bands follow the channel frequency`() {
        assertThat(TravelContextCardPresentation.wifiBand(2437)).isEqualTo("2.4 GHz")
        assertThat(TravelContextCardPresentation.wifiBand(5180)).isEqualTo("5 GHz")
        assertThat(TravelContextCardPresentation.wifiBand(6115)).isEqualTo("6 GHz")
        assertThat(TravelContextCardPresentation.wifiBand(900)).isEqualTo("900 MHz")
    }
}
