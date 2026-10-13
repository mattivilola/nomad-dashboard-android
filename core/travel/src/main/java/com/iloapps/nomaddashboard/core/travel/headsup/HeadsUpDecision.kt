package com.iloapps.nomaddashboard.core.travel.headsup

/** How urgent a heads-up is. Declaration order is the ranking order (later is more urgent). */
enum class HeadsUpSeverity {
    INFO,
    CAUTION,
    ATTENTION,
}

/**
 * The outcome of one heads-up rule: what to say and how loudly. The heads-up engine adds the kind,
 * icon and related card.
 */
data class HeadsUpDecision(
    val severity: HeadsUpSeverity,
    val title: String,
    val detail: String?,
    /** Compact label (about 12 characters or fewer) for a status surface such as a notification, e.g. "UV 9". */
    val statusText: String,
)

/** A [HeadsUpDecision] and the [HeadsUpKind] it is about; the engine adds the related card to make a [HeadsUpItem]. */
data class HeadsUpCandidate(
    val kind: HeadsUpKind,
    val decision: HeadsUpDecision,
)

/** Text limits shared by the heads-up rules (the same as the macOS heads-up engine). */
object HeadsUpText {
    const val STATUS_TEXT_CHARACTER_LIMIT = 12
    const val DETAIL_CHARACTER_LIMIT = 44

    fun truncated(text: String, limit: Int = DETAIL_CHARACTER_LIMIT): String =
        if (text.length > limit) text.take(limit - 1).trim() + "…" else text
}
