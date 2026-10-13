package com.iloapps.nomaddashboard.core.travel.answers

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.travel.daylight.DaylightOutlook
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpItem
import com.iloapps.nomaddashboard.core.travel.health.HealthLevel
import com.iloapps.nomaddashboard.core.travel.safety.EmergencyNumbers
import java.time.Duration

// The structured values behind each spoken answer, ported from the macOS `QuickAnswers` types. Every
// answer carries its `sentence`, the plain text an assistant says, next to the values a widget or a card
// can show, so the two can never disagree.

/** The emergency numbers for the country the user is in. */
data class EmergencyNumbersAnswer(
    /** Upper-case ISO code of the country the user is in, null when it is not known yet. */
    val countryCode: String?,
    val countryName: String?,
    /** Null when the country is unknown or its numbers could not be verified. */
    val numbers: EmergencyNumbers?,
    val sentence: String,
)

/** The overall verdict plus the top heads-up. */
data class DashboardStatusAnswer(
    val level: HealthLevel,
    /** "Ready", "Caution", "Attention" or "Waiting". */
    val verdict: String,
    /** "Network and power look good", "Network: Latency 85 ms". */
    val reason: String,
    /** The most important heads-up for the selected travel profile, null when there is none. */
    val headsUp: HeadsUpItem?,
    val sentence: String,
)

/** The user's nearest own mission and consular hotline, the EU or Nordic fallback, or what to do next. */
data class NearestEmbassyAnswer(
    val state: State,
    val mission: Mission?,
    val hotline: Hotline?,
    val sentence: String,
) {
    enum class State {
        /** No citizenship chosen in settings. */
        CITIZENSHIP_NEEDED,

        /** The country the user is in is not known yet. */
        LOCATION_NEEDED,

        /** The user is in the country they hold citizenship of. */
        AT_HOME,

        /** One of the user's own missions is listed. */
        MISSION,

        /** No own mission is listed; another state's mission can help (EU or Nordic arrangement). */
        FALLBACK_MISSION,

        /** Neither an own mission nor a fallback is listed. */
        NO_MISSION,
    }

    data class Mission(
        val name: String,
        val city: String?,
        /** Upper-case code of the state that runs the mission. */
        val sendingCountryCode: String,
        /** "about 4 km" for a city-centre coordinate, "4.2 km" for a precise one; null without a device position. */
        val distanceText: String?,
    )

    data class Hotline(
        /** Upper-case citizenship code the line belongs to. */
        val citizenship: String,
        /** International format, ready to dial. */
        val phone: String,
        /** The ministry's own caveat, for example "24/7 · emergencies abroad". */
        val note: String?,
    )
}

/** Sunset and the daylight left, or when it gets light again. */
data class DaylightAnswer(
    /** Null when there is no location with coordinates yet. */
    val outlook: DaylightOutlook?,
    val phase: DaylightOutlook.Phase?,
    /** Daylight left until sunset, null outside daylight. */
    val remaining: Duration?,
    val sentence: String,
)

/** What switching the travel profile did, and what to say about it. */
data class TravelProfileAnswer(
    /** The settings to persist: the given ones, with the profile applied when it changed. */
    val settings: AppSettings,
    /** False when the requested profile was already selected, so nothing needs saving. */
    val changed: Boolean,
    val sentence: String,
)
