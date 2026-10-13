package com.iloapps.nomaddashboard.core.travel.answers

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.TravelProfile
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.travel.daylight.ClockTimeFormatter
import com.iloapps.nomaddashboard.core.travel.daylight.DaylightOutlook
import com.iloapps.nomaddashboard.core.travel.format.DurationText
import com.iloapps.nomaddashboard.core.travel.format.TravelFormatters
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpEngine
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpFormats
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpItem
import com.iloapps.nomaddashboard.core.travel.health.DashboardHealthEvaluator
import com.iloapps.nomaddashboard.core.travel.health.SectionHealth
import com.iloapps.nomaddashboard.core.travel.safety.ConsularContactsDirectory
import com.iloapps.nomaddashboard.core.travel.safety.CountryNames
import com.iloapps.nomaddashboard.core.travel.safety.CurrentCountry
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionIndex
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionMatch
import com.iloapps.nomaddashboard.core.travel.safety.EmbassyLookup
import com.iloapps.nomaddashboard.core.travel.safety.EmergencyNumberDirectory
import com.iloapps.nomaddashboard.core.travel.safety.EmergencyNumbers
import com.iloapps.nomaddashboard.core.travel.safety.MissionCoordinate
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Short, natural sentences for assistants and automations, plus the structured values behind them.
 * Ported from the macOS `QuickAnswers`, with the same wording and fallbacks.
 *
 * Everything here is a pure function of a dashboard snapshot, the settings and a clock: no network, no
 * storage. The same lookups feed the home screen widgets, so a widget and a spoken answer can never
 * disagree about the emergency number or the nearest embassy.
 *
 * Safety-critical wording: an answer never states more than the bundled data does. The directory's
 * `general` number is "the number to call first", which for some countries is the police number rather
 * than an all-purpose line, so the sentences call it "the emergency number" and never label it as
 * police or as covering every service.
 *
 * @param locale picks the country names and decides whether English article rules apply ("in the
 *   United States").
 * @param clockTime formats sunrise and sunset in the place's own time zone.
 * @param listFormat joins list items into one phrase; the default is English ("a, b, and c").
 * @param indexProvider the mission dataset, only read once the user is abroad with a citizenship.
 */
class QuickAnswers(
    val locale: Locale = Locale.getDefault(),
    private val countryNames: CountryNames = CountryNames.forLocale(locale),
    private val clockTime: ClockTimeFormatter = ClockTimeFormatter.localized(locale),
    private val listFormat: (List<String>) -> String = ::englishList,
    private val indexProvider: () -> DiplomaticMissionIndex = { DiplomaticMissionIndex.bundled },
) {
    // region Emergency numbers

    /** The emergency numbers for the country the user is in. */
    fun emergencyNumbers(snapshot: DashboardSnapshot): EmergencyNumbersAnswer {
        val code = currentCountryCode(snapshot)
            ?: return EmergencyNumbersAnswer(
                countryCode = null,
                countryName = null,
                numbers = null,
                sentence = "I can't tell which country you're in yet, so I don't have emergency numbers for you. " +
                    "Open Nomad Dashboard to update your location.",
            )

        val name = countryName(code, snapshot)
        val spokenName = spokenCountryName(name, code)
        val numbers = EmergencyNumberDirectory.numbersFor(code)
            ?: return EmergencyNumbersAnswer(
                countryCode = code,
                countryName = name,
                numbers = null,
                sentence = "Nomad Dashboard doesn't have verified emergency numbers for $spokenName yet.",
            )

        var sentence = "The emergency number in $spokenName is ${numbers.general}."
        otherNumbersSentence(numbers)?.let { sentence += " $it" }
        return EmergencyNumbersAnswer(code, name, numbers, sentence)
    }

    /** "Police is 17, ambulance is 15, and fire is 18." / "Ambulance or fire is 15 and the Gendarmerie is 177." */
    private fun otherNumbersSentence(numbers: EmergencyNumbers): String? {
        val parts = mutableListOf<String>()
        numbers.police?.let { parts += "police is $it" }

        val ambulance = numbers.ambulance
        val fire = numbers.fire
        if (ambulance != null && fire != null && ambulance == fire) {
            parts += "ambulance or fire is $ambulance"
        } else {
            ambulance?.let { parts += "ambulance is $it" }
            fire?.let { parts += "fire is $it" }
        }

        numbers.other.forEach { parts += "the ${it.name} is ${it.number}" }

        if (parts.isEmpty()) return null
        return capitalizingFirstLetter(listFormat(parts)) + "."
    }

    // endregion

    // region Dashboard status

    /**
     * The overall verdict plus the top heads-up.
     *
     * @param headsUp the ranked heads-up items for the selected travel profile, best first; only the
     *   first one is spoken.
     */
    fun dashboardStatus(snapshot: DashboardSnapshot, headsUp: List<HeadsUpItem>): DashboardStatusAnswer {
        val overall = overallHealth(snapshot)
        val top = headsUp.firstOrNull()

        var sentence = "${overall.label}. ${spoken(overall.reason)}."
        sentence += if (top != null) " Top heads-up: ${spoken(top.title)}." else " No heads-ups right now."

        return DashboardStatusAnswer(
            level = overall.level,
            verdict = overall.label,
            reason = overall.reason,
            headsUp = top,
            sentence = sentence,
        )
    }

    /**
     * The overall verdict plus the top heads-up for the selected travel profile, including the stay,
     * Schengen and tax-residence items when [countryDays] has the recorded days.
     *
     * @param zone the device's time zone: it decides today's date for the stay items.
     */
    fun dashboardStatus(
        snapshot: DashboardSnapshot,
        settings: AppSettings,
        countryDays: List<VisitedCountryDay>,
        headsUpEngine: HeadsUpEngine,
        now: Instant,
        zone: ZoneId,
        formats: HeadsUpFormats = HeadsUpFormats(locale = locale),
    ): DashboardStatusAnswer = dashboardStatus(
        snapshot,
        headsUpEngine.evaluate(snapshot, settings, countryDays, now, zone, formats, limit = 1),
    )

    // endregion

    // region Nearest embassy

    /** The user's nearest own mission and consular hotline, the EU or Nordic fallback, or what to do next. */
    fun nearestEmbassy(snapshot: DashboardSnapshot, settings: AppSettings): NearestEmbassyAnswer {
        val codes = AppSettings.sanitizedCitizenshipCodes(settings.citizenshipCountryCodes)
        if (codes.isEmpty()) {
            return NearestEmbassyAnswer(
                state = NearestEmbassyAnswer.State.CITIZENSHIP_NEEDED,
                mission = null,
                hotline = null,
                sentence = "Set your citizenship in Nomad Dashboard settings to find your embassy.",
            )
        }

        val host = currentCountryCode(snapshot)
        if (host == null) {
            val hotline = consularHotline(codes, preferring = null)
            return NearestEmbassyAnswer(
                state = NearestEmbassyAnswer.State.LOCATION_NEEDED,
                mission = null,
                hotline = hotline,
                sentence = appendingHotline(
                    "I can't tell which country you're in yet, so I can't find your embassy.",
                    hotline,
                    codes.size,
                ),
            )
        }

        val hostName = countryName(host, snapshot)
        val spokenHost = spokenCountryName(hostName, host)
        if (codes.all { it == host }) {
            return NearestEmbassyAnswer(
                state = NearestEmbassyAnswer.State.AT_HOME,
                mission = null,
                hotline = consularHotline(codes, preferring = null),
                sentence = "You're home in $hostName, so there's no embassy to look up.",
            )
        }

        val context = snapshot.travelContext
        val latitude = context.deviceLatitude
        val longitude = context.deviceLongitude
        val coordinate = if (latitude != null && longitude != null) MissionCoordinate(latitude, longitude) else null
        val lookup = EmbassyLookup.find(codes, host, coordinate, index = indexProvider())

        lookup.ownMatches.firstOrNull()?.let { match ->
            val mission = makeMission(match)
            val hotline = consularHotline(codes, preferring = match.mission.sendingCountry)
            val sentence = "Your nearest mission is the ${missionDescription(mission)}."
            return NearestEmbassyAnswer(
                state = NearestEmbassyAnswer.State.MISSION,
                mission = mission,
                hotline = hotline,
                sentence = appendingHotline(sentence, hotline, codes.size),
            )
        }

        val abroad = codes.filter { it != host }
        val abroadNames = capitalizingFirstLetter(
            listFormat(abroad.map { spokenCountryName(countryName(it, snapshot = null), it) }),
        )
        val verb = if (abroad.size == 1) "has" else "have"
        val lead = "$abroadNames $verb no mission listed in $spokenHost"

        val fallback = lookup.fallbackMatches.firstOrNull()
        val reason = lookup.fallbackReason
        if (fallback != null && reason != null) {
            val mission = makeMission(fallback)
            val hotline = consularHotline(codes, preferring = null)
            val arrangement = when (reason) {
                EmbassyLookup.FallbackReason.EuConsularProtection -> "Under EU consular protection"
                EmbassyLookup.FallbackReason.NordicCooperation -> "Under Nordic consular cooperation"
            }
            val sentence = "$lead. $arrangement, the nearest mission that can help is the ${missionDescription(mission)}."
            return NearestEmbassyAnswer(
                state = NearestEmbassyAnswer.State.FALLBACK_MISSION,
                mission = mission,
                hotline = hotline,
                sentence = appendingHotline(sentence, hotline, codes.size),
            )
        }

        val hotline = consularHotline(codes, preferring = null)
        val sentence = "$lead, and no EU or Nordic alternative is known. " +
            "Check your government's official list of missions."
        return NearestEmbassyAnswer(
            state = NearestEmbassyAnswer.State.NO_MISSION,
            mission = null,
            hotline = hotline,
            sentence = appendingHotline(sentence, hotline, codes.size),
        )
    }

    private fun makeMission(match: DiplomaticMissionMatch): NearestEmbassyAnswer.Mission =
        NearestEmbassyAnswer.Mission(
            name = match.mission.name,
            city = match.mission.city.trimmedNonEmpty(),
            sendingCountryCode = match.mission.sendingCountry.uppercase(),
            distanceText = distanceText(match.distanceKilometers, match.mission.isApproximateLocation),
        )

    private fun distanceText(kilometers: Double?, isApproximate: Boolean): String? {
        if (kilometers == null || !kilometers.isFinite() || kilometers < 0) return null
        return if (isApproximate) {
            "about ${maxOf(1L, Math.round(kilometers))} km"
        } else {
            TravelFormatters.kilometers(kilometers)
        }
    }

    /** "Embassy of Finland in Rabat, about 4 km away": the city is only added when the name lacks it. */
    private fun missionDescription(mission: NearestEmbassyAnswer.Mission): String {
        var text = mission.name
        val city = mission.city
        if (city != null && !text.foldedForSearch().contains(city.foldedForSearch())) {
            text += " in $city"
        }
        mission.distanceText?.let { text += ", $it away" }
        return text
    }

    /** The consular line of the citizenship that runs the nearest mission, else the first one with a bundled line. */
    private fun consularHotline(codes: List<String>, preferring: String?): NearestEmbassyAnswer.Hotline? {
        val preferred = preferring?.uppercase()?.takeIf { it in codes }
        for (code in listOfNotNull(preferred) + codes) {
            val contacts = ConsularContactsDirectory.contactsFor(code) ?: continue
            return NearestEmbassyAnswer.Hotline(
                citizenship = code,
                phone = contacts.emergencyPhone,
                note = contacts.emergencyPhoneNote.trimmedNonEmpty(),
            )
        }
        return null
    }

    /**
     * "Your foreign ministry's consular emergency line is +358 9 1605 5555 (24/7, consular emergencies
     * only, no visa enquiries)." With several citizenships the line is named after its country.
     */
    private fun appendingHotline(
        sentence: String,
        hotline: NearestEmbassyAnswer.Hotline?,
        citizenshipCount: Int,
    ): String {
        if (hotline == null) return sentence

        val owner = if (citizenshipCount > 1) {
            "${countryName(hotline.citizenship, snapshot = null)}'s foreign ministry's"
        } else {
            "Your foreign ministry's"
        }
        val note = hotline.note?.let { " (${spoken(it)})" }.orEmpty()
        return "$sentence $owner consular emergency line is ${hotline.phone}$note."
    }

    // endregion

    // region Daylight

    /**
     * Sunset and the daylight left, or when it gets light again.
     *
     * @param zone the device's time zone, for a device location that has none of its own.
     */
    fun daylight(snapshot: DashboardSnapshot, now: Instant, zone: ZoneId): DaylightAnswer {
        val outlook = SnapshotDaylight.outlook(snapshot, now, zone)
            ?: return DaylightAnswer(
                outlook = null,
                phase = null,
                remaining = null,
                sentence = "I can't tell when the sun sets because I don't have a location yet.",
            )

        val phase = outlook.phaseAt(now)
        var remaining: Duration? = null
        val sentence = when (phase) {
            is DaylightOutlook.Phase.Daylight -> {
                val left = Duration.between(now, phase.sunset).coerceAtLeast(Duration.ZERO)
                remaining = left
                "Sunset is at ${outlook.clockTime(phase.sunset, clockTime)}. " +
                    "You have ${DurationText.hoursAndMinutes(left)} of daylight left."
            }

            is DaylightOutlook.Phase.Dark -> {
                val sunrise = phase.sunrise
                when {
                    sunrise == null -> "The sun has set."
                    outlook.isSameDay(sunrise, now) -> "It's dark until ${outlook.clockTime(sunrise, clockTime)}."
                    else -> "The sun has set. It's dark until ${outlook.clockTime(sunrise, clockTime)} tomorrow."
                }
            }

            DaylightOutlook.Phase.MidnightSun -> "The sun doesn't set today. It's midnight sun."
            DaylightOutlook.Phase.PolarNight -> "The sun doesn't rise today. It's polar night."
            null -> "I can't work out the sunset for your location today."
        }

        return DaylightAnswer(outlook = outlook, phase = phase, remaining = remaining, sentence = sentence)
    }

    // endregion

    // region Travel profile

    /** Switches the travel profile and says what happened. Persisting [TravelProfileAnswer.settings] is up to the caller. */
    fun switchTravelProfile(settings: AppSettings, profile: TravelProfile): TravelProfileAnswer {
        if (settings.travelProfile == profile) {
            return TravelProfileAnswer(
                settings = settings,
                changed = false,
                sentence = "Nomad Dashboard is already on the ${profile.title} profile.",
            )
        }
        return TravelProfileAnswer(
            settings = settings.applyTravelProfile(profile),
            changed = true,
            sentence = "Switched Nomad Dashboard to the ${profile.title} profile. ${profile.summary}",
        )
    }

    // endregion

    // region Shared wording

    /** "Rabat, Morocco", "Morocco" or null when the place is not known. */
    fun placeName(snapshot: DashboardSnapshot): String? {
        val context = snapshot.travelContext
        val city = listOf(context.deviceCity, context.city, snapshot.localInfo.locality)
            .firstNotNullOfOrNull { it.trimmedNonEmpty() }
        val country = currentCountryCode(snapshot)?.let { countryName(it, snapshot) }
        return listOfNotNull(city, country).takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    /**
     * The system's name for a region in the user's language, else the name the snapshot's providers
     * gave, else the code itself.
     */
    fun countryName(code: String, snapshot: DashboardSnapshot?): String {
        val normalized = code.trim().uppercase()
        val name = countryNames.name(normalized)
        if (name != normalized) return name

        val context = snapshot?.travelContext
        return listOf(context?.deviceCountry, context?.country, snapshot?.localInfo?.countryName)
            .firstNotNullOfOrNull { it.trimmedNonEmpty() }
            ?: normalized
    }

    private fun spokenCountryName(name: String, code: String): String =
        if (locale.language == "en" && code.uppercase() in countriesNeedingArticle) "the $name" else name

    // endregion

    companion object {
        /** English country names that read naturally only with an article: "in the United States". */
        private val countriesNeedingArticle = setOf(
            "US", "GB", "NL", "AE", "PH", "BS", "GM", "MV", "SC", "KM", "MH", "SB", "CD", "CG", "CF",
        )

        /** "a", "a and b", "a, b, and c". */
        fun englishList(items: List<String>): String =
            when (items.size) {
                0 -> ""
                1 -> items[0]
                2 -> "${items[0]} and ${items[1]}"
                else -> items.dropLast(1).joinToString(", ") + ", and " + items.last()
            }
    }
}

/** The overall dashboard health the verdict and the widgets are built from. */
internal fun overallHealth(snapshot: DashboardSnapshot): SectionHealth =
    DashboardHealthEvaluator.evaluate(snapshot.connectivity, snapshot.power).overall

/** Upper-case ISO code of the country the user is in: device location, then IP, then the local info card. */
internal fun currentCountryCode(snapshot: DashboardSnapshot): String? =
    CurrentCountry.code(
        deviceCountryCode = snapshot.travelContext.deviceCountryCode,
        ipCountryCode = snapshot.travelContext.countryCode,
        localInfoCountryCode = snapshot.localInfo.countryCode,
    )

/** Speech reads the middle dot as a pause only sometimes, so a comma says it reliably. */
internal fun spoken(text: String): String =
    text.replace(" \u00B7 ", ", ").trim { it == '.' || it.isWhitespace() }

internal fun String?.trimmedNonEmpty(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun capitalizingFirstLetter(text: String): String = text.take(1).uppercase() + text.drop(1)

/** Lower-case without accents, so a city is found in a mission name that spells it without them. */
private fun String.foldedForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        .lowercase()
