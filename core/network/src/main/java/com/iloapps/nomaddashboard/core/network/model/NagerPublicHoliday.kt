package com.iloapps.nomaddashboard.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class NagerPublicHoliday(
    val date: String,
    val localName: String? = null,
    val name: String? = null,
    val countryCode: String? = null,
    val global: Boolean = false,
    val counties: List<String>? = null,
    val launchYear: Int? = null,
    val types: List<String> = emptyList(),
)
