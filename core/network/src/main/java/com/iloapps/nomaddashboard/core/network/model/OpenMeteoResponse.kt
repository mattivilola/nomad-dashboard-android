package com.iloapps.nomaddashboard.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoResponse(
    @SerialName("timezone") val timezone: String? = null,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int? = null,
    @SerialName("current") val current: OpenMeteoCurrent? = null,
    @SerialName("hourly") val hourly: OpenMeteoHourly? = null,
    @SerialName("daily") val daily: OpenMeteoDaily? = null,
)

@Serializable
data class OpenMeteoCurrent(
    @SerialName("temperature_2m") val temperatureCelsius: Double? = null,
    @SerialName("apparent_temperature") val apparentTemperatureCelsius: Double? = null,
    @SerialName("precipitation_probability") val precipitationProbability: Int? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("wind_speed_10m") val windSpeedKph: Double? = null,
    @SerialName("wind_gusts_10m") val windGustKph: Double? = null,
    @SerialName("wind_direction_10m") val windDirectionDegrees: Double? = null,
)

@Serializable
data class OpenMeteoHourly(
    @SerialName("time") val times: List<String> = emptyList(),
    @SerialName("temperature_2m") val temperatures: List<Double?> = emptyList(),
    @SerialName("precipitation_probability") val rainChance: List<Int?> = emptyList(),
    @SerialName("weather_code") val weatherCodes: List<Int?> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeedsKph: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m") val windDirectionDegrees: List<Double?> = emptyList(),
)

@Serializable
data class OpenMeteoDaily(
    @SerialName("time") val dates: List<String> = emptyList(),
    @SerialName("temperature_2m_min") val minTemperatures: List<Double?> = emptyList(),
    @SerialName("temperature_2m_max") val maxTemperatures: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max") val rainChance: List<Int?> = emptyList(),
    @SerialName("weather_code") val weatherCodes: List<Int?> = emptyList(),
    @SerialName("wind_speed_10m_max") val windSpeedsKph: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m_dominant") val windDirectionDegrees: List<Double?> = emptyList(),
)
