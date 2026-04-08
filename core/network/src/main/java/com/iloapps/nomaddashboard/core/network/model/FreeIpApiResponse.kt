package com.iloapps.nomaddashboard.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonArray

@Serializable
data class FreeIpApiResponse(
    @SerialName("ipAddress") val ipAddress: String? = null,
    @SerialName("cityName") val cityName: String? = null,
    @SerialName("regionName") val regionName: String? = null,
    @SerialName("countryName") val countryName: String? = null,
    @SerialName("countryCode") val countryCode: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("timeZone") val timeZone: String? = null,
    @Serializable(with = StringListOrStringSerializer::class)
    @SerialName("timeZones") val timeZones: List<String> = emptyList(),
)

object StringListOrStringSerializer : JsonTransformingSerializer<List<String>>(ListSerializer(String.serializer())) {
    override fun transformDeserialize(element: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement =
        when (element) {
            JsonNull -> JsonArray(emptyList())
            is JsonPrimitive -> buildJsonArray { add(element) }
            else -> element
        }
}
