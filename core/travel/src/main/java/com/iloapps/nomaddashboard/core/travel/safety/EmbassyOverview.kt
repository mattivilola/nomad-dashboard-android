package com.iloapps.nomaddashboard.core.travel.safety

/**
 * Everything the "Your Embassy" card shows, decided from the user's citizenships, the country they
 * are in and (optionally) their device coordinate. Built by [EmbassyOverviewBuilder]; the UI only renders it.
 *
 * Citizenship is sensitive: it is only compared with the bundled offline dataset and never leaves
 * the device. The dataset is incomplete, so the wording says "listed" and "nearest known".
 */
data class EmbassyOverview(
    val state: State,
    val subtitle: String,
    val badge: Badge,
    val hotlines: List<Hotline>,
    /** Missions of the user's own state(s), nearest first, at most three. */
    val missions: List<MissionRow>,
    /** Why [fallbackMissions] are shown, null when there are none. */
    val fallbackNotice: String?,
    val fallbackMissions: List<MissionRow>,
    /** Shown when the dataset lists neither an own mission nor a fallback. */
    val noMissionMessage: String?,
    /** One line replacing the missions while the user is home. */
    val atHomeMessage: String?,
    val links: List<LinkGroup>,
    val footer: String?,
    /** One line for the collapsed card. */
    val summary: String,
    val summaryHelp: String,
) {
    enum class State {
        /** No citizenship chosen, so the card is switched off. */
        Off,

        /** The user is in the country they hold citizenship of. */
        AtHome,

        /** The country they are in is not known yet. */
        LocationNeeded,

        /** Abroad: show missions, or the fallback. */
        Abroad,
    }

    enum class BadgeIcon { Institution, LocationOff, Home, MapPinOff }

    /** Color means status only: [Caution] is the one status the card raises, everything else is neutral. */
    enum class BadgeTone { Neutral, Caution }

    data class Badge(val title: String, val icon: BadgeIcon, val tone: BadgeTone = BadgeTone.Neutral)

    /** The foreign ministry's consular emergency line for one citizenship. */
    data class Hotline(
        /** Citizenship code. */
        val id: String,
        /** Short ministry name, prefixed with the country when the user has several citizenships. */
        val title: String,
        /** "24/7 consular line · Crisis and Support Centre", worded from the ministry's own note. */
        val detail: String,
        val phone: String,
        /** Accessibility text with the ministry's full name and note. */
        val help: String,
    )

    data class MissionRow(
        val id: String,
        val name: String,
        val kindTitle: String,
        /** "Rabat", or "Sweden · Rabat" for a mission of another state. */
        val locationText: String,
        /** "~4 km" for a city-centre coordinate, "4.2 km" for a precise one, null without a device position. */
        val distanceText: String?,
        /** Only set on a fallback row whose arrangement differs from the one in the card's notice. */
        val reasonCaption: String?,
        /** A `geo:` URI that opens the user's maps app. */
        val mapsUri: String,
        val websiteUrl: String?,
        val address: String?,
    ) {
        val accessibilityLabel: String
            get() = listOfNotNull(name, kindTitle, locationText, distanceText)
                .filter { it.isNotEmpty() }
                .joinToString(", ")
    }

    data class Link(val title: String, val url: String)

    /** The official links of one citizenship. */
    data class LinkGroup(
        /** Citizenship code. */
        val id: String,
        val countryName: String,
        val registration: Link?,
        val missionsDirectory: Link?,
    )

    /** What the Saved essentials sheet shows about the user's embassy; null when there is nothing to show. */
    fun essentials(): EmbassyEssentials? {
        val nearestMission = missions.firstOrNull()?.let { row ->
            listOf(row.name, row.locationText).filter { it.isNotEmpty() }.joinToString(", ")
        }
        val essentials = EmbassyEssentials(
            nearestMission = nearestMission,
            hotlines = hotlines.map { EmbassyEssentials.Hotline(title = it.title, phone = it.phone) },
        )
        return essentials.takeUnless { it.isEmpty }
    }
}

data class EmbassyEssentials(
    /** "Embassy of Finland, Rabat". */
    val nearestMission: String?,
    val hotlines: List<Hotline>,
) {
    data class Hotline(
        /** Short ministry name, prefixed with the country when the user has several citizenships. */
        val title: String,
        val phone: String,
    )

    val isEmpty: Boolean
        get() = nearestMission == null && hotlines.isEmpty()
}
