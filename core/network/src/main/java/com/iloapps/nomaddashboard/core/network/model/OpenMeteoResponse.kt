package com.iloapps.nomaddashboard.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoResponse(
    @SerialName("current") val current: OpenMeteoCurrent? = null,
    @SerialName("daily") val daily: OpenMeteoDaily? = null,
)

@Serializable
data class OpenMeteoCurrent(
    @SerialName("temperature_2m") val temperatureCelsius: Double? = null,
    @SerialName("apparent_temperature") val apparentTemperatureCelsius: Double? = null,
    @SerialName("precipitation_probability") val precipitationProbability: Int? = null,
    @SerialName("wind_speed_10m") val windSpeedKph: Double? = null,
    @SerialName("wind_direction_10m") val windDirectionDegrees: Double? = null,
)

@Serializable
data class OpenMeteoDaily(
    @SerialName("time") val dates: List<String> = emptyList(),
    @SerialName("temperature_2m_min") val minTemperatures: List<Double?> = emptyList(),
    @SerialName("temperature_2m_max") val maxTemperatures: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max") val rainChance: List<Int?> = emptyList(),
    @SerialName("weather_code") val weatherCodes: List<Int?> = emptyList(),
)

