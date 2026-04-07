package com.iloapps.nomaddashboard.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReliefWebReportsRequest(
    val limit: Int = 50,
    val sort: List<String> = listOf("date.created:desc"),
    val fields: ReliefWebFields = ReliefWebFields(),
    val query: ReliefWebQuery = ReliefWebQuery(),
    val filter: ReliefWebFilter,
)

@Serializable
data class ReliefWebFields(
    val include: List<String> = listOf(
        "title",
        "date.created",
        "primary_country.shortname",
        "source.shortname",
        "url_alias",
    ),
)

@Serializable
data class ReliefWebQuery(
    val value: String = "security conflict violence protest unrest armed attack shelling airstrike",
    val operator: String = "OR",
)

@Serializable
data class ReliefWebFilter(
    val operator: String = "AND",
    val conditions: List<ReliefWebCondition>,
)

@Serializable
data class ReliefWebCondition(
    val field: String,
    val value: List<String>,
    val operator: String = "OR",
)
