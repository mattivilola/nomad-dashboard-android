package com.iloapps.nomaddashboard.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class IpifyResponse(
    val ip: String? = null,
)
