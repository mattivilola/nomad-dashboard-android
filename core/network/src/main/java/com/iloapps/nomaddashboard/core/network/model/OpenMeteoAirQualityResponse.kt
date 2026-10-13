package com.iloapps.nomaddashboard.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoAirQualityResponse(
    @SerialName("timezone") val timezone: String? = null,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int? = null,
    @SerialName("current") val current: OpenMeteoAirQualityCurrent? = null,
    @SerialName("hourly") val hourly: OpenMeteoAirQualityHourly? = null,
)

@Serializable
data class OpenMeteoAirQualityCurrent(
    @SerialName("european_aqi") val europeanAqi: Double? = null,
    @SerialName("pm2_5") val pm2_5: Double? = null,
    @SerialName("pm10") val pm10: Double? = null,
    @SerialName("ozone") val ozone: Double? = null,
    @SerialName("nitrogen_dioxide") val nitrogenDioxide: Double? = null,
    @SerialName("european_aqi_pm2_5") val europeanAqiPm2_5: Double? = null,
    @SerialName("european_aqi_pm10") val europeanAqiPm10: Double? = null,
    @SerialName("european_aqi_ozone") val europeanAqiOzone: Double? = null,
    @SerialName("european_aqi_nitrogen_dioxide") val europeanAqiNitrogenDioxide: Double? = null,
    @SerialName("european_aqi_sulphur_dioxide") val europeanAqiSulphurDioxide: Double? = null,
)

@Serializable
data class OpenMeteoAirQualityHourly(
    @SerialName("time") val times: List<String> = emptyList(),
    @SerialName("european_aqi") val europeanAqi: List<Double?> = emptyList(),
)
