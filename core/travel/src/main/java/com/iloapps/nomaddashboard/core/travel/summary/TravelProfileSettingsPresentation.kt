package com.iloapps.nomaddashboard.core.travel.summary

import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.TravelProfile

/** Copy and affordances for the "Travel profile" settings group, kept out of the UI so they can be unit tested. */
data class TravelProfileSettingsPresentation(
    val profile: TravelProfile,
    val isUsingRecommendedOrder: Boolean,
) {
    /** The profile summary, followed by a note that a customized card order stays untouched. */
    val footer: String
        get() = if (isUsingRecommendedOrder) profile.summary else "${profile.summary} $KEPT_CUSTOM_ORDER_NOTE"

    /** Offered only when a customized order is in place, so the profile's own order can be restored in one tap. */
    val offersRecommendedOrder: Boolean
        get() = !isUsingRecommendedOrder

    /** The card order "Use recommended order" and "Reset Dashboard Layout" restore. */
    val recommendedOrder: List<DashboardCardId>
        get() = DashboardCardId.defaultOrder(profile)

    companion object {
        const val KEPT_CUSTOM_ORDER_NOTE = "Your custom card order is kept."
        const val USE_RECOMMENDED_ORDER_TITLE = "Use recommended order"
    }
}
