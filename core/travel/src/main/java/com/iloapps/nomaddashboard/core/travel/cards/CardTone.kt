package com.iloapps.nomaddashboard.core.travel.cards

import com.iloapps.nomaddashboard.core.travel.health.HealthLevel

/**
 * The status colour a card value or badge takes. Colour means status only: [GOOD] is teal, [CAUTION]
 * sand, [ATTENTION] coral and [NEUTRAL] plain text. The UI layer maps each one to its design-system tone.
 */
enum class CardTone {
    NEUTRAL,
    GOOD,
    CAUTION,
    ATTENTION,
    ;

    companion object {
        fun of(level: HealthLevel): CardTone =
            when (level) {
                HealthLevel.READY -> GOOD
                HealthLevel.CAUTION -> CAUTION
                HealthLevel.ATTENTION -> ATTENTION
                HealthLevel.UNAVAILABLE -> NEUTRAL
            }
    }
}
