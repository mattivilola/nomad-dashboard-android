package com.iloapps.nomaddashboard.core.travel.stays

/**
 * When a stay, Schengen or tax-residence count starts to warrant a warning. Shared by the heads-up items
 * and the Stays & Visas card so both turn sand and coral together.
 */
object StayWarningThresholds {
    /** Days left at or below which a visa allowance or the Schengen limit is a caution (sand). */
    const val CAUTION_DAYS = 14

    /** Days left at or below which it is an attention (coral). Overstays are always attention. */
    const val ATTENTION_DAYS = 3

    /** A tax-residence count is informational from this many days before the threshold. */
    const val TAX_RESIDENCE_INFO_LEAD_DAYS = 30

    /** A tax-residence count is a caution (sand) from this many days before the threshold. */
    const val TAX_RESIDENCE_CAUTION_LEAD_DAYS = 7
}
