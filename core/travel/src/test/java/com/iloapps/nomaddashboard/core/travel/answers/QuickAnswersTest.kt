package com.iloapps.nomaddashboard.core.travel.answers

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot
import com.iloapps.nomaddashboard.core.model.TravelProfile
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.casablancaZone
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.UTC
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.consecutiveDays
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.engine
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.helsinki
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.helsinkiMidday
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.helsinkiZone
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.instant
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.morocco
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.now
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.osloZone
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.place
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.snapshot
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.tromso
import com.iloapps.nomaddashboard.core.travel.answers.QuickAnswersFixtures.utcToday
import com.iloapps.nomaddashboard.core.travel.daylight.ClockTimeFormatter
import com.iloapps.nomaddashboard.core.travel.daylight.DaylightOutlook
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpFormats
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpItem
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpKind
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpSeverity
import com.iloapps.nomaddashboard.core.travel.health.HealthLevel
import com.iloapps.nomaddashboard.core.travel.safety.ConsularContactsDirectory
import com.iloapps.nomaddashboard.core.travel.safety.EmergencyNumberDirectory
import java.time.Duration
import java.time.Instant
import java.util.Locale
import org.junit.Test

private val answers = QuickAnswersFixtures.answers()
private val usClock = ClockTimeFormatter.localized(Locale.US)

class QuickAnswersEmergencyNumbersTest {
    @Test
    fun `morocco names the emergency number then the others`() {
        val answer = answers.emergencyNumbers(morocco())

        assertThat(answer.countryCode).isEqualTo("MA")
        assertThat(answer.countryName).isEqualTo("Morocco")
        assertThat(answer.numbers).isEqualTo(EmergencyNumberDirectory.numbersFor("MA"))
        assertThat(answer.sentence)
            .isEqualTo("The emergency number in Morocco is 19. Ambulance or fire is 15 and the Gendarmerie is 177.")
    }

    @Test
    fun `france lists police ambulance and fire`() {
        val answer = answers.emergencyNumbers(snapshot(device = place(countryCode = "FR")))

        assertThat(answer.sentence)
            .isEqualTo("The emergency number in France is 112. Police is 17, ambulance is 15, and fire is 18.")
    }

    @Test
    fun `a single number country has nothing to add`() {
        val us = answers.emergencyNumbers(snapshot(device = place(countryCode = "US")))
        val germany = answers.emergencyNumbers(snapshot(device = place(countryCode = "DE")))

        assertThat(us.sentence).isEqualTo("The emergency number in the United States is 911.")
        assertThat(germany.sentence).isEqualTo("The emergency number in Germany is 112. Police is 110.")
    }

    @Test
    fun `the article is only added in English`() {
        val german = QuickAnswersFixtures.answers(Locale.GERMANY)
        val answer = german.emergencyNumbers(snapshot(device = place(countryCode = "US")))

        assertThat(answer.sentence).startsWith("The emergency number in Vereinigte Staaten is 911.")
    }

    @Test
    fun `an unknown country is said clearly and invents nothing`() {
        val answer = answers.emergencyNumbers(snapshot())

        assertThat(answer.countryCode).isNull()
        assertThat(answer.numbers).isNull()
        assertThat(answer.sentence).contains("can't tell which country you're in")
        assertThat(answer.sentence).contains("Open Nomad Dashboard")
    }

    @Test
    fun `a country without verified numbers is not guessed`() {
        val answer = answers.emergencyNumbers(snapshot(device = place(countryCode = "LA")))

        assertThat(answer.countryCode).isEqualTo("LA")
        assertThat(answer.numbers).isNull()
        assertThat(answer.sentence).isEqualTo("Nomad Dashboard doesn't have verified emergency numbers for Laos yet.")
    }

    @Test
    fun `the country comes from device then IP then local info`() {
        val ipOnly = snapshot(ip = place(countryCode = "ES"))
        assertThat(answers.emergencyNumbers(ipOnly).countryCode).isEqualTo("ES")

        val both = snapshot(device = place(countryCode = "FR"), ip = place(countryCode = "ES"))
        assertThat(answers.emergencyNumbers(both).countryCode).isEqualTo("FR")

        val localInfoOnly = snapshot(localInfo = LocalInfoSnapshot(countryCode = "pt"))
        assertThat(answers.emergencyNumbers(localInfoOnly).countryCode).isEqualTo("PT")
    }

    @Test
    fun `every bundled country answers with its first number and no police label on it`() {
        for (code in EmergencyNumberDirectory.countryCodes) {
            val numbers = EmergencyNumberDirectory.numbersFor(code)!!
            val sentence = answers.emergencyNumbers(snapshot(device = place(countryCode = code))).sentence

            assertThat(sentence).contains(" is ${numbers.general}.")
            numbers.other.forEach { service ->
                assertThat(sentence).contains("${service.name} is ${service.number}")
            }
        }
    }

    @Test
    fun `three or more extra numbers are joined with an Oxford comma`() {
        assertThat(QuickAnswers.englishList(listOf("a"))).isEqualTo("a")
        assertThat(QuickAnswers.englishList(listOf("a", "b"))).isEqualTo("a and b")
        assertThat(QuickAnswers.englishList(listOf("a", "b", "c"))).isEqualTo("a, b, and c")
    }
}

class QuickAnswersDashboardStatusTest {
    private fun headsUp(title: String) = HeadsUpItem(
        kind = HeadsUpKind.STRONG_WIND,
        severity = HeadsUpSeverity.ATTENTION,
        title = title,
        statusText = "Wind 65",
        detail = null,
        relatedCard = DashboardCardId.WEATHER,
    )

    @Test
    fun `a healthy dashboard is ready with no heads-ups`() {
        val answer = answers.dashboardStatus(snapshot(), headsUp = emptyList())

        assertThat(answer.level).isEqualTo(HealthLevel.READY)
        assertThat(answer.verdict).isEqualTo("Ready")
        assertThat(answer.headsUp).isNull()
        assertThat(answer.sentence).isEqualTo("Ready. Network and power look good. No heads-ups right now.")
    }

    @Test
    fun `the verdict leads and the top heads-up follows`() {
        val top = headsUp("Wind up to 65 km/h")
        val answer = answers.dashboardStatus(snapshot(), headsUp = listOf(top, headsUp("Second")))

        assertThat(answer.headsUp).isEqualTo(top)
        assertThat(answer.sentence)
            .isEqualTo("Ready. Network and power look good. Top heads-up: Wind up to 65 km/h.")
    }

    @Test
    fun `an offline phone is attention with the reason`() {
        val answer = answers.dashboardStatus(snapshot(offline = true), headsUp = emptyList())

        assertThat(answer.level).isEqualTo(HealthLevel.ATTENTION)
        assertThat(answer.verdict).isEqualTo("Attention")
        assertThat(answer.reason).isEqualTo("Network: No network route")
        assertThat(answer.sentence).startsWith("Attention. Network: No network route.")
    }

    @Test
    fun `a stay allowance running out is the top heads-up`() {
        val answer = answers.dashboardStatus(
            snapshot = morocco(),
            settings = AppSettings(stayAllowances = mapOf("MA" to 90)),
            countryDays = consecutiveDays(85, endingOn = utcToday, code = "MA"),
            headsUpEngine = engine,
            now = now,
            zone = UTC,
        )

        assertThat(answer.headsUp?.kind).isEqualTo(HeadsUpKind.STAY_ALLOWANCE)
        assertThat(answer.sentence).isEqualTo("Ready. Network and power look good. Top heads-up: Morocco: 5 days left.")
    }

    @Test
    fun `the Schengen limit is spoken for a third country citizen`() {
        val lisbon = snapshot(device = place(city = "Lisbon", country = "Portugal", countryCode = "PT"))
        val answer = answers.dashboardStatus(
            snapshot = lisbon,
            settings = AppSettings(citizenshipCountryCodes = listOf("US")),
            countryDays = consecutiveDays(80, endingOn = utcToday, code = "PT"),
            headsUpEngine = engine,
            now = now,
            zone = UTC,
        )

        assertThat(answer.headsUp?.kind).isEqualTo(HeadsUpKind.SCHENGEN_LIMIT)
        assertThat(answer.sentence).contains("Top heads-up: Schengen: 10 of 90 days left.")
    }

    @Test
    fun `without recorded days there are no stay heads-ups`() {
        val answer = answers.dashboardStatus(
            snapshot = morocco(),
            settings = AppSettings(stayAllowances = mapOf("MA" to 90)),
            countryDays = emptyList(),
            headsUpEngine = engine,
            now = now,
            zone = UTC,
        )

        assertThat(answer.headsUp).isNull()
    }

    @Test
    fun `time separators are spoken as commas`() {
        // Road trip profile, 45 minutes before sunset in Helsinki: "Sunset 21:30 · 45 min of daylight".
        val snapshot = helsinki()
        val outlook = SnapshotDaylight.outlook(snapshot, helsinkiMidday, helsinkiZone)!!
        val at = outlook.sunset!!.minus(Duration.ofMinutes(45))
        val items = engine.evaluate(
            snapshot = snapshot,
            settings = AppSettings(travelProfile = TravelProfile.ROAD_TRIP),
            countryDays = emptyList(),
            now = at,
            zone = helsinkiZone,
            formats = HeadsUpFormats(locale = Locale.UK),
            limit = 1,
        )
        val answer = answers.dashboardStatus(snapshot, items)

        assertThat(answer.headsUp?.kind).isEqualTo(HeadsUpKind.DAYLIGHT)
        assertThat(answer.sentence).doesNotContain(" · ")
        assertThat(answer.sentence).contains(", 45 min of daylight")
    }
}

class QuickAnswersNearestEmbassyTest {
    private fun answer(snapshot: DashboardSnapshot, citizenships: List<String>) =
        answers.nearestEmbassy(snapshot, AppSettings(citizenshipCountryCodes = citizenships))

    @Test
    fun `without citizenship it says where to set it`() {
        val result = answer(morocco(), emptyList())

        assertThat(result.state).isEqualTo(NearestEmbassyAnswer.State.CITIZENSHIP_NEEDED)
        assertThat(result.mission).isNull()
        assertThat(result.hotline).isNull()
        assertThat(result.sentence).isEqualTo("Set your citizenship in Nomad Dashboard settings to find your embassy.")
    }

    @Test
    fun `without a country it asks for location but still gives the hotline`() {
        val result = answer(snapshot(), listOf("FI"))
        val phone = ConsularContactsDirectory.contactsFor("FI")!!.emergencyPhone

        assertThat(result.state).isEqualTo(NearestEmbassyAnswer.State.LOCATION_NEEDED)
        assertThat(result.hotline?.phone).isEqualTo(phone)
        assertThat(result.sentence)
            .startsWith("I can't tell which country you're in yet, so I can't find your embassy.")
        assertThat(result.sentence).contains("consular emergency line is $phone")
    }

    @Test
    fun `at home there is no embassy to find`() {
        val result = answer(helsinki(), listOf("FI"))

        assertThat(result.state).isEqualTo(NearestEmbassyAnswer.State.AT_HOME)
        assertThat(result.mission).isNull()
        assertThat(result.sentence).isEqualTo("You're home in Finland, so there's no embassy to look up.")
    }

    @Test
    fun `abroad it names the nearest own mission with distance and the hotline`() {
        val result = answer(morocco(), listOf("FI"))
        val contacts = ConsularContactsDirectory.contactsFor("FI")!!

        assertThat(result.state).isEqualTo(NearestEmbassyAnswer.State.MISSION)
        assertThat(result.mission?.name).isEqualTo("Embassy of Finland in Rabat")
        assertThat(result.mission?.sendingCountryCode).isEqualTo("FI")
        assertThat(result.mission?.distanceText).endsWith("km")
        assertThat(result.hotline?.phone).isEqualTo(contacts.emergencyPhone)
        assertThat(result.sentence).startsWith("Your nearest mission is the Embassy of Finland in Rabat, ")
        assertThat(result.sentence)
            .contains(" km away. Your foreign ministry's consular emergency line is ${contacts.emergencyPhone}")
    }

    @Test
    fun `a city centre coordinate is called about and the city is added when the name lacks it`() {
        val snapshot = snapshot(device = place(countryCode = "MA", latitude = 34.0, longitude = -6.9))
        val result = answer(snapshot, listOf("SE"))

        assertThat(result.mission?.name).isEqualTo("Embassy of Sweden")
        assertThat(result.mission?.distanceText).startsWith("about ")
        assertThat(result.sentence).startsWith("Your nearest mission is the Embassy of Sweden in Rabat, about ")
    }

    @Test
    fun `without an own mission a fellow EU or Nordic mission helps`() {
        val result = answer(snapshot(device = place(countryCode = "ES")), listOf("FI"))

        assertThat(result.state).isEqualTo(NearestEmbassyAnswer.State.FALLBACK_MISSION)
        assertThat(result.mission?.sendingCountryCode).isEqualTo("SE")
        assertThat(result.sentence).startsWith(
            "Finland has no mission listed in Spain. Under Nordic consular cooperation, " +
                "the nearest mission that can help is the Embassy of Sweden in Madrid.",
        )
    }

    @Test
    fun `with nothing listed it says so and keeps the hotline`() {
        val result = answer(snapshot(device = place(countryCode = "ES")), listOf("AU"))
        val phone = ConsularContactsDirectory.contactsFor("AU")!!.emergencyPhone

        assertThat(result.state).isEqualTo(NearestEmbassyAnswer.State.NO_MISSION)
        assertThat(result.mission).isNull()
        assertThat(result.sentence).startsWith(
            "Australia has no mission listed in Spain, and no EU or Nordic alternative is known. " +
                "Check your government's official list of missions.",
        )
        assertThat(result.sentence).contains(phone)
    }

    @Test
    fun `several citizenships name the country of the hotline`() {
        // Standing at the Finnish embassy: it is the nearest one even though Sweden is listed first.
        val atFinnishEmbassy = snapshot(device = place(countryCode = "MA", latitude = 34.05, longitude = -6.84))
        val result = answer(atFinnishEmbassy, listOf("SE", "FI"))

        assertThat(result.state).isEqualTo(NearestEmbassyAnswer.State.MISSION)
        assertThat(result.mission?.sendingCountryCode).isEqualTo("FI")
        assertThat(result.hotline?.citizenship).isEqualTo("FI")
        assertThat(result.sentence).contains("Finland's foreign ministry's consular emergency line is ")
    }

    @Test
    fun `the ministry's note is spoken with commas`() {
        val result = answer(morocco(), listOf("AT"))
        val note = ConsularContactsDirectory.contactsFor("AT")!!.emergencyPhoneNote

        assertThat(note).contains("·")
        assertThat(result.sentence).doesNotContain("·")
        assertThat(result.sentence).contains("(24/7, emergencies abroad)")
    }

    @Test
    fun `the mission dataset is only read once the user is abroad`() {
        var reads = 0
        val lazy = QuickAnswers(locale = Locale.US, indexProvider = { reads++; QuickAnswersFixtures.missionIndex })

        lazy.nearestEmbassy(morocco(), AppSettings())
        lazy.nearestEmbassy(snapshot(), AppSettings(citizenshipCountryCodes = listOf("FI")))
        lazy.nearestEmbassy(helsinki(), AppSettings(citizenshipCountryCodes = listOf("FI")))
        assertThat(reads).isEqualTo(0)

        lazy.nearestEmbassy(morocco(), AppSettings(citizenshipCountryCodes = listOf("FI")))
        assertThat(reads).isEqualTo(1)
    }
}

class QuickAnswersDaylightTest {
    private val helsinki = helsinki()
    private val outlook = SnapshotDaylight.outlook(helsinki, helsinkiMidday, helsinkiZone)!!

    private fun daylight(at: Instant) = answers.daylight(helsinki, at, helsinkiZone)

    @Test
    fun `in the day sunset and the daylight left are given`() {
        val sunset = outlook.sunset!!
        val left = Duration.ofHours(2).plusMinutes(10)
        val answer = daylight(sunset.minus(left))

        assertThat(answer.phase).isEqualTo(DaylightOutlook.Phase.Daylight(sunset))
        assertThat(answer.remaining).isEqualTo(left)
        assertThat(answer.sentence).isEqualTo(
            "Sunset is at ${outlook.clockTime(sunset, usClock)}. You have 2 h 10 min of daylight left.",
        )
    }

    @Test
    fun `less than a minute left is rounded up to one minute`() {
        val answer = daylight(outlook.sunset!!.minusSeconds(10))

        assertThat(answer.sentence).endsWith("You have 1 min of daylight left.")
    }

    @Test
    fun `hours and minutes are written the compact way`() {
        val sunset = outlook.sunset!!

        assertThat(daylight(sunset.minus(Duration.ofMinutes(90))).sentence).endsWith("You have 1 h 30 min of daylight left.")
        assertThat(daylight(sunset.minus(Duration.ofHours(3))).sentence).endsWith("You have 3 h of daylight left.")
        assertThat(daylight(sunset.minus(Duration.ofMinutes(45))).sentence).endsWith("You have 45 min of daylight left.")
    }

    @Test
    fun `after sunset it is dark until tomorrow's sunrise`() {
        val nextSunrise = outlook.nextSunrise!!
        val answer = daylight(outlook.sunset!!.plusSeconds(60))

        assertThat(answer.phase).isEqualTo(DaylightOutlook.Phase.Dark(nextSunrise))
        assertThat(answer.remaining).isNull()
        assertThat(answer.sentence)
            .isEqualTo("The sun has set. It's dark until ${outlook.clockTime(nextSunrise, usClock)} tomorrow.")
    }

    @Test
    fun `before sunrise it is dark until today's sunrise`() {
        val sunrise = outlook.sunrise!!
        val answer = daylight(sunrise.minusSeconds(3_600))

        assertThat(answer.phase).isEqualTo(DaylightOutlook.Phase.Dark(sunrise))
        assertThat(answer.sentence).isEqualTo("It's dark until ${outlook.clockTime(sunrise, usClock)}.")
    }

    @Test
    fun `the sun never sets in the midnight sun`() {
        val answer = answers.daylight(tromso(), instant(2026, 6, 21, 23, 0, osloZone), osloZone)

        assertThat(answer.phase).isEqualTo(DaylightOutlook.Phase.MidnightSun)
        assertThat(answer.sentence).isEqualTo("The sun doesn't set today. It's midnight sun.")
    }

    @Test
    fun `the sun never rises in the polar night`() {
        val answer = answers.daylight(tromso(), instant(2026, 12, 21, 12, 0, osloZone), osloZone)

        assertThat(answer.phase).isEqualTo(DaylightOutlook.Phase.PolarNight)
        assertThat(answer.sentence).isEqualTo("The sun doesn't rise today. It's polar night.")
    }

    @Test
    fun `without a location it says so`() {
        val answer = answers.daylight(snapshot(), helsinkiMidday, helsinkiZone)

        assertThat(answer.outlook).isNull()
        assertThat(answer.phase).isNull()
        assertThat(answer.sentence).isEqualTo("I can't tell when the sun sets because I don't have a location yet.")
    }

    @Test
    fun `an IP location brings its own time zone`() {
        val ipOnly = snapshot(
            ip = place(countryCode = "FI", latitude = 60.1699, longitude = 24.9384, timeZoneId = "Europe/Helsinki"),
        )

        val outlook = SnapshotDaylight.outlook(ipOnly, helsinkiMidday, casablancaZone)

        assertThat(outlook?.zone).isEqualTo(helsinkiZone)
    }
}

class QuickAnswersPlaceTest {
    @Test
    fun `the city and country are joined`() {
        assertThat(answers.placeName(morocco())).isEqualTo("Rabat, Morocco")
    }

    @Test
    fun `the city falls back to the IP city then the local info locality`() {
        val ip = snapshot(ip = place(city = "  Madrid ", countryCode = "ES"))
        val localInfo = snapshot(localInfo = LocalInfoSnapshot(locality = "Porto", countryCode = "PT"))

        assertThat(answers.placeName(ip)).isEqualTo("Madrid, Spain")
        assertThat(answers.placeName(localInfo)).isEqualTo("Porto, Portugal")
    }

    @Test
    fun `the country alone is enough`() {
        assertThat(answers.placeName(snapshot(ip = place(countryCode = "ES")))).isEqualTo("Spain")
    }

    @Test
    fun `an unknown place is null`() {
        assertThat(answers.placeName(snapshot())).isNull()
    }

    @Test
    fun `a code the system cannot name falls back to the provider name then the code`() {
        val named = snapshot(device = place(country = "Xandria", countryCode = "ZZ"))
        val unnamed = snapshot(device = place(countryCode = "ZZ"))

        assertThat(answers.countryName("ZZ", named)).isEqualTo("Xandria")
        assertThat(answers.countryName("ZZ", unnamed)).isEqualTo("ZZ")
        assertThat(answers.countryName("zz", null)).isEqualTo("ZZ")
    }
}

class QuickAnswersTravelProfileTest {
    @Test
    fun `switching profile applies it and says what it does`() {
        val result = answers.switchTravelProfile(AppSettings(), TravelProfile.ROAD_TRIP)

        assertThat(result.changed).isTrue()
        assertThat(result.settings.travelProfile).isEqualTo(TravelProfile.ROAD_TRIP)
        assertThat(result.sentence).isEqualTo(
            "Switched Nomad Dashboard to the Road trip & RV profile. " +
                "Puts weather, fuel, emergency care, power and data allowance first.",
        )
    }

    @Test
    fun `the profile already selected changes nothing`() {
        val settings = AppSettings(travelProfile = TravelProfile.NOMAD)
        val result = answers.switchTravelProfile(settings, TravelProfile.NOMAD)

        assertThat(result.changed).isFalse()
        assertThat(result.settings).isEqualTo(settings)
        assertThat(result.sentence).isEqualTo("Nomad Dashboard is already on the Nomad profile.")
    }
}
