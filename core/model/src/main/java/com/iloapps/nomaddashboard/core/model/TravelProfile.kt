package com.iloapps.nomaddashboard.core.model

/**
 * The kind of traveller the dashboard is tuned for. It shapes the recommended card order and how
 * heads-up items are ranked; it never hides a card or feature.
 */
enum class TravelProfile(
    val title: String,
    val summary: String,
) {
    NOMAD(
        title = "Nomad",
        summary = "Puts connection quality, work time and city context first.",
    ),
    ROAD_TRIP(
        title = "Road trip & RV",
        summary = "Puts weather, fuel, emergency care, power and data allowance first.",
    ),
}
