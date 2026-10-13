package com.iloapps.nomaddashboard.core.travel.safety

import com.iloapps.nomaddashboard.core.model.AppSettings
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Decides what the "Your Embassy" card shows. Pure: no Android APIs, no settings access, and the
 * mission dataset is only read (via [indexProvider]) once the user is abroad.
 */
class EmbassyOverviewBuilder(
    private val locale: Locale = Locale.getDefault(),
    private val countryNames: CountryNames = CountryNames.forLocale(locale),
    private val indexProvider: () -> DiplomaticMissionIndex = { DiplomaticMissionIndex.bundled },
) {
    fun build(
        citizenships: List<String>,
        hostCountry: String?,
        coordinate: MissionCoordinate?,
    ): EmbassyOverview {
        val codes = AppSettings.sanitizedCitizenshipCodes(citizenships)
        val host = hostCountry?.normalizedCountryCode().orEmpty()

        if (codes.isEmpty()) {
            return EmbassyOverview(
                state = EmbassyOverview.State.Off,
                subtitle = "Find your embassy and consular line abroad",
                badge = EmbassyOverview.Badge("Off", EmbassyOverview.BadgeIcon.Institution),
                hotlines = emptyList(),
                missions = emptyList(),
                fallbackNotice = null,
                fallbackMissions = emptyList(),
                noMissionMessage = null,
                atHomeMessage = null,
                links = emptyList(),
                footer = null,
                summary = "Off",
                summaryHelp = "Off",
            )
        }

        val hotlines = hotlines(codes)
        val hostName = countryNames.name(host)

        if (host.isEmpty()) {
            return EmbassyOverview(
                state = EmbassyOverview.State.LocationNeeded,
                subtitle = countryNames.list(codes),
                badge = EmbassyOverview.Badge(
                    "Location Needed",
                    EmbassyOverview.BadgeIcon.LocationOff,
                    EmbassyOverview.BadgeTone.Caution,
                ),
                hotlines = hotlines,
                missions = emptyList(),
                fallbackNotice = null,
                fallbackMissions = emptyList(),
                noMissionMessage = null,
                atHomeMessage = null,
                links = links(codes),
                footer = null,
                summary = hotlines.firstOrNull()?.phone ?: "Location needed",
                summaryHelp = listOfNotNull(LocationNeededTitle, hotlines.firstOrNull()?.phone).joinToString(" · "),
            )
        }

        if (codes.all { it == host }) {
            val summary = "Home in $hostName"
            return EmbassyOverview(
                state = EmbassyOverview.State.AtHome,
                subtitle = countryNames.list(codes),
                badge = EmbassyOverview.Badge("Home", EmbassyOverview.BadgeIcon.Home),
                hotlines = hotlines,
                missions = emptyList(),
                fallbackNotice = null,
                fallbackMissions = emptyList(),
                noMissionMessage = null,
                atHomeMessage = "You're home in $hostName — $AtHomeSuffix",
                links = emptyList(),
                footer = null,
                summary = summary,
                summaryHelp = summary,
            )
        }

        val abroad = codes.filter { it != host }
        val index = indexProvider()
        val lookup = EmbassyLookup.find(codes, host, coordinate, index = index)
        val noticeReason = lookup.fallbackReason
        val missions = lookup.ownMatches.map { row(it, lookup = null, noticeReason = null) }
        val fallbackMissions = lookup.fallbackMatches.map { row(it, lookup = lookup, noticeReason = noticeReason) }
        val fallbackNotice = noticeReason?.let { fallbackNotice(it, abroad, hostName) }
        val noMissionMessage = if (lookup.isEmpty) {
            "${noMissionSentence(abroad, hostName)}, and no EU or Nordic alternative is known. " +
                "Check the official missions directory."
        } else {
            null
        }

        val phone = (
            lookup.ownMatches.firstOrNull()
                ?.let { match -> hotlines.firstOrNull { it.id == match.mission.sendingCountry } }
                ?: hotlines.firstOrNull()
            )?.phone

        // The collapsed line is only about 22 characters wide and never repeats the badge. The consular
        // line is what a traveller in trouble needs first, so it leads and is never cut off.
        val nearest = lookup.ownMatches.firstOrNull()
        val badge: EmbassyOverview.Badge
        val summary: String
        val summaryHelp: String
        if (nearest != null) {
            badge = EmbassyOverview.Badge("Listed", EmbassyOverview.BadgeIcon.Institution)
            val distanceText = missions.firstOrNull()?.distanceText
            val place = listOfNotNull(nearest.mission.city ?: nearest.mission.name, distanceText).joinToString(" ")
            summary = phone ?: place
            summaryHelp = listOfNotNull(nearest.mission.name, distanceText, phone).joinToString(" · ")
        } else if (noticeReason != null) {
            badge = EmbassyOverview.Badge("${helpLabel(noticeReason)} help", EmbassyOverview.BadgeIcon.Institution)
            summary = phone ?: "Not listed here"
            summaryHelp = listOfNotNull(fallbackNotice, phone).joinToString(" ")
        } else {
            badge = EmbassyOverview.Badge("Not listed", EmbassyOverview.BadgeIcon.MapPinOff)
            summary = phone ?: "Not listed here"
            summaryHelp = listOfNotNull("Not listed here", phone).joinToString(" · ")
        }

        return EmbassyOverview(
            state = EmbassyOverview.State.Abroad,
            subtitle = "${countryNames.list(abroad)} in $hostName",
            badge = badge,
            hotlines = hotlines,
            missions = missions,
            fallbackNotice = fallbackNotice,
            fallbackMissions = fallbackMissions,
            noMissionMessage = noMissionMessage,
            atHomeMessage = null,
            links = links(abroad),
            footer = footer(index),
            summary = summary,
            summaryHelp = summaryHelp,
        )
    }

    // region Hotlines

    private fun hotlines(codes: List<String>): List<EmbassyOverview.Hotline> = codes.mapNotNull { code ->
        val contacts = ConsularContactsDirectory.contactsFor(code) ?: return@mapNotNull null
        val shortName = shortMinistryName(contacts.ministryName)
        val note = contacts.emergencyPhoneNote
        EmbassyOverview.Hotline(
            id = code,
            title = if (codes.size > 1) "${countryNames.name(code)} · $shortName" else shortName,
            detail = lineDescription(note),
            phone = contacts.emergencyPhone,
            help = listOfNotNull(contacts.ministryName, note).joinToString(" · "),
        )
    }

    // endregion

    // region Missions

    private fun row(
        match: DiplomaticMissionMatch,
        lookup: EmbassyLookup?,
        noticeReason: EmbassyLookup.FallbackReason?,
    ): EmbassyOverview.MissionRow {
        val mission = match.mission
        val place = mission.city?.trim()?.takeIf { it.isNotEmpty() }
        val locationText = if (lookup != null) {
            listOfNotNull(countryNames.name(mission.sendingCountry), place).joinToString(" · ")
        } else {
            place.orEmpty()
        }

        val reason = lookup?.fallbackReasonFor(mission)
        return EmbassyOverview.MissionRow(
            id = mission.id,
            name = mission.name,
            kindTitle = mission.kind.title,
            locationText = locationText,
            distanceText = distanceText(match.distanceKilometers, mission.isApproximateLocation),
            reasonCaption = reason?.takeIf { it != noticeReason }?.let(::reasonCaption),
            mapsUri = MissionMapsLink.uri(mission),
            websiteUrl = mission.website,
            address = mission.address?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    // endregion

    // region Fallback wording

    private fun noMissionSentence(citizenships: List<String>, host: String): String {
        val verb = if (citizenships.size == 1) "has" else "have"
        return "${countryNames.list(citizenships)} $verb no mission listed in $host"
    }

    private fun fallbackNotice(
        reason: EmbassyLookup.FallbackReason,
        citizenships: List<String>,
        host: String,
    ): String {
        val lead = noMissionSentence(citizenships, host)
        return when (reason) {
            EmbassyLookup.FallbackReason.EuConsularProtection ->
                "$lead. Nearest known EU missions can help under EU consular protection."
            EmbassyLookup.FallbackReason.NordicCooperation ->
                "$lead. Nearest known Nordic missions help citizens of other Nordic countries when theirs isn't represented."
        }
    }

    private fun helpLabel(reason: EmbassyLookup.FallbackReason): String = when (reason) {
        EmbassyLookup.FallbackReason.EuConsularProtection -> "EU"
        EmbassyLookup.FallbackReason.NordicCooperation -> "Nordic"
    }

    private fun reasonCaption(reason: EmbassyLookup.FallbackReason): String = when (reason) {
        EmbassyLookup.FallbackReason.EuConsularProtection -> "Under EU consular protection"
        EmbassyLookup.FallbackReason.NordicCooperation -> "Under Nordic cooperation"
    }

    // endregion

    // region Links and footer

    private fun links(codes: List<String>): List<EmbassyOverview.LinkGroup> = codes.mapNotNull { code ->
        val contacts = ConsularContactsDirectory.contactsFor(code) ?: return@mapNotNull null
        val registrationUrl = contacts.travelRegistrationUrl
        val registrationName = contacts.travelRegistrationName
        val registration = if (registrationUrl != null && registrationName != null) {
            EmbassyOverview.Link(registrationName, registrationUrl)
        } else {
            null
        }
        val directory = contacts.missionsDirectoryUrl?.let { EmbassyOverview.Link(AllMissionsTitle, it) }
        if (registration == null && directory == null) return@mapNotNull null

        EmbassyOverview.LinkGroup(
            id = code,
            countryName = countryNames.name(code),
            registration = registration,
            missionsDirectory = directory,
        )
    }

    /** "Locations: Wikidata (CC0) · updated Oct 5, 2026 · Check details on the official site before you go". */
    private fun footer(index: DiplomaticMissionIndex): String {
        val parts = mutableListOf("Locations: ${index.source ?: DiplomaticMissionDataset.WikidataSource}")
        index.generatedAt?.takeIf { it.isNotEmpty() }?.let { parts += "updated ${formattedDate(it)}" }
        parts += UnverifiedFooter
        return parts.joinToString(" · ")
    }

    /** The dataset's `yyyy-MM-dd` date in the user's format, or the raw text when it does not parse. */
    internal fun formattedDate(isoDate: String): String {
        val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return isoDate
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .format(date)
    }

    // endregion

    companion object {
        const val AllMissionsTitle = "All missions"
        const val AtHomeSuffix = "embassy details appear when you travel"
        const val LocationNeededTitle = "Location needed to find your embassy"
        const val LocationNeededMessage =
            "Allow location or turn on the public IP lookup so the card knows which country you are in."
        const val OffTitle = "Your Embassy Off"
        const val OffMessage =
            "Add your citizenship in Settings to find your embassy and your foreign ministry's emergency line."
        const val UnverifiedFooter = "Check details on the official site before you go"

        /**
         * Words the ministry's own note: "24/7 consular line" only where the note itself says 24/7,
         * otherwise the note's wording ("Emergency line · outside office hours").
         */
        fun lineDescription(note: String?): String {
            val trimmed = note?.trim()?.takeIf { it.isNotEmpty() } ?: return "Consular emergency line"
            val allDay = "24/7"
            if (!trimmed.startsWith(allDay)) return trimmed

            val rest = trimmed.removePrefix(allDay).trim(' ', '·')
            return if (rest.isEmpty()) "24/7 consular line" else "24/7 consular line · $rest"
        }

        /**
         * "Ministry for Foreign Affairs of Finland" becomes "Ministry for Foreign Affairs". The country is
         * only dropped when what remains is a complete ministry name; "U.S. Department of State" stays whole.
         */
        fun shortMinistryName(name: String): String {
            val separator = name.lastIndexOf(" of ")
            if (separator < 0) return name

            val head = name.substring(0, separator)
            val completeEndings = listOf("Affairs", "Office", "Trade", "Cooperation", "Relations")
            return if (completeEndings.any { head.endsWith(it) }) head else name
        }

        /** "~4 km" when the mission's coordinate is only the city centre, "4.2 km" when it is precise. */
        fun distanceText(kilometers: Double?, isApproximate: Boolean): String? {
            if (kilometers == null || !kilometers.isFinite() || kilometers < 0) return null
            if (isApproximate) return "~${maxOf(1, kilometers.roundToInt())} km"
            return formatKilometers(kilometers)
        }

        /** One decimal below 10 km and whole kilometres from 10 km up. */
        private fun formatKilometers(value: Double): String {
            val scale = if (value >= 10) 0 else 1
            return BigDecimal(value).setScale(scale, RoundingMode.HALF_EVEN).toPlainString() + " km"
        }
    }
}
