package com.iloapps.nomaddashboard.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpainFuelResponse(
    @SerialName("Fecha") val fetchedAt: String? = null,
    @SerialName("ListaEESSPrecio") val stations: List<SpainFuelStation> = emptyList(),
)

@Serializable
data class SpainFuelStation(
    @SerialName("IDEESS") val identifier: String? = null,
    @SerialName("Rótulo") val stationName: String? = null,
    @SerialName("Dirección") val address: String? = null,
    @SerialName("Municipio") val municipality: String? = null,
    @SerialName("Localidad") val locality: String? = null,
    @SerialName("Latitud") val latitude: String? = null,
    @SerialName("Longitud (WGS84)") val longitude: String? = null,
    @SerialName("Precio Gasoleo A") val dieselPrice: String? = null,
    @SerialName("Precio Gasoleo Premium") val premiumDieselPrice: String? = null,
    @SerialName("Precio Gasolina 95 E5") val gasoline95E5Price: String? = null,
    @SerialName("Precio Gasolina 95 E10") val gasoline95E10Price: String? = null,
    @SerialName("Precio Gasolina 98 E5") val gasoline98E5Price: String? = null,
    @SerialName("Precio Gasolina 98 E10") val gasoline98E10Price: String? = null,
)
