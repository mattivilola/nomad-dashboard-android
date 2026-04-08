package com.iloapps.nomaddashboard.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoMarineResponse(
    @SerialName("timezone") val timezone: String? = null,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int? = null,
    @SerialName("hourly") val hourly: OpenMeteoMarineHourly? = null,
)

@Serializable
data class OpenMeteoMarineHourly(
    @SerialName("time") val times: List<String> = emptyList(),
    @SerialName("wave_height") val waveHeightMeters: List<Double?> = emptyList(),
    @SerialName("wave_period") val wavePeriodSeconds: List<Double?> = emptyList(),
    @SerialName("swell_wave_height") val swellWaveHeightMeters: List<Double?> = emptyList(),
    @SerialName("swell_wave_period") val swellWavePeriodSeconds: List<Double?> = emptyList(),
    @SerialName("swell_wave_direction") val swellWaveDirectionDegrees: List<Double?> = emptyList(),
    @SerialName("sea_surface_temperature") val seaSurfaceTemperatureCelsius: List<Double?> = emptyList(),
)
