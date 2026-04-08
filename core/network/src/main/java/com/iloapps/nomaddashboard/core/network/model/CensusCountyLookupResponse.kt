package com.iloapps.nomaddashboard.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CensusCountyLookupResponse(
    val result: CensusCountyLookupResult = CensusCountyLookupResult(),
)

@Serializable
data class CensusCountyLookupResult(
    val geographies: CensusCountyLookupGeographies = CensusCountyLookupGeographies(),
)

@Serializable
data class CensusCountyLookupGeographies(
    @SerialName("Counties") val counties: List<CensusCounty> = emptyList(),
)

@Serializable
data class CensusCounty(
    @SerialName("GEOID") val geoid: String,
    @SerialName("NAME") val name: String,
)
