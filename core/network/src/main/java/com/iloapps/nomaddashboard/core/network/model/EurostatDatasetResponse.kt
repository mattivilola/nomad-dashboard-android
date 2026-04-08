package com.iloapps.nomaddashboard.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class EurostatDatasetResponse(
    val value: Map<String, Double> = emptyMap(),
    val dimension: EurostatDatasetDimension = EurostatDatasetDimension(),
)

@Serializable
data class EurostatDatasetDimension(
    val time: EurostatDatasetCategoryContainer = EurostatDatasetCategoryContainer(),
)

@Serializable
data class EurostatDatasetCategoryContainer(
    val category: EurostatDatasetCategory = EurostatDatasetCategory(),
)

@Serializable
data class EurostatDatasetCategory(
    val index: Map<String, Int> = emptyMap(),
)
