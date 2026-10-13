package com.iloapps.nomaddashboard.core.data.airquality

import com.iloapps.nomaddashboard.core.model.AirQualitySnapshot

/** Fetches current air quality and a 12 hour European AQI outlook for a coordinate. */
interface AirQualityProvider {
    /**
     * The coordinate is rounded to one decimal (~11 km) before it leaves the device, so the provider
     * never learns the exact location.
     *
     * @throws AirQualityProviderException when the coordinate is invalid or the response has no usable data.
     */
    suspend fun airQuality(latitude: Double, longitude: Double): AirQualitySnapshot
}

class AirQualityProviderException(message: String) : Exception(message)
