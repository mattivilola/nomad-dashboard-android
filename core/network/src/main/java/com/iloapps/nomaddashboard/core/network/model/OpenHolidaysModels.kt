package com.iloapps.nomaddashboard.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class OpenHolidaysLocalizedText(
    val language: String? = null,
    val text: String? = null,
)

@Serializable
data class OpenHolidaysSubdivisionGroup(
    val code: String,
    val shortName: String? = null,
)

@Serializable
data class OpenHolidaysSubdivisionReference(
    val code: String,
    val shortName: String? = null,
)

@Serializable
data class OpenHolidaysSubdivision(
    val code: String,
    val isoCode: String? = null,
    val shortName: String? = null,
    val category: List<OpenHolidaysLocalizedText> = emptyList(),
    val name: List<OpenHolidaysLocalizedText> = emptyList(),
    val officialLanguages: List<String> = emptyList(),
    val children: List<OpenHolidaysSubdivision> = emptyList(),
    val groups: List<OpenHolidaysSubdivisionGroup> = emptyList(),
)

@Serializable
data class OpenHolidaysHoliday(
    val id: String? = null,
    val startDate: String,
    val endDate: String,
    val type: String? = null,
    val name: List<OpenHolidaysLocalizedText> = emptyList(),
    val regionalScope: String? = null,
    val temporalScope: String? = null,
    val nationwide: Boolean = false,
    val subdivisions: List<OpenHolidaysSubdivisionReference> = emptyList(),
)
