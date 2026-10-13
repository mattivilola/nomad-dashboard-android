package com.iloapps.nomaddashboard.core.travel.arrival

import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpFormats
import com.iloapps.nomaddashboard.core.travel.reference.CountryElectricity
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmRow
import com.iloapps.nomaddashboard.core.travel.safety.EmbassyLookup
import com.iloapps.nomaddashboard.core.travel.safety.EmbassyOverviewBuilder
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId

/**
 * The "Welcome to Morocco" card as compact rows. Only sections the briefing has data for appear, in
 * reading order. Ported from the macOS `ArrivalBriefingPresentation`; the Data Guard row is left out
 * because Android has no Data Guard.
 */
data class ArrivalBriefingPresentation(
    /** The country the card briefs: acknowledging it hides the card until the next border. */
    val countryCode: String,
    val title: String,
    /** "Arrived from Spain"; null when the previous country is the same or unknown. */
    val subtitle: String?,
    val rows: List<Row>,
    /** What the collapsed card says after the title: "Emergency 112 · Drive on the left". */
    val collapsedSummary: String,
) {
    enum class Kind { TIME, EMERGENCY, EMBASSY, ROAD, POWER, MONEY, HOLIDAY, ADVISORY, RHYTHM }

    /** Sand for something that changed or needs care, coral for a serious advisory; neutral otherwise. */
    enum class Tone { NEUTRAL, CAUTION, ATTENTION }

    data class Row(
        val id: String,
        val kind: Kind,
        val title: String,
        val text: String,
        val detail: String?,
        val tone: Tone,
        /** "Changed" when plugs, currency or driving side differ from the previous country; a rhythm row's timing. */
        val flag: String?,
        /** Whether the text can be selected and copied (phone numbers). */
        val isSelectable: Boolean,
    )

    companion object {
        const val ACKNOWLEDGE_TITLE = "Got it"
        const val CHANGED_FLAG = "Changed"

        /**
         * @param now with [zone], decides the local clock, the rhythm timing and "today" or "tomorrow".
         * @param formats the device's locale, clock and date style, and the country names.
         */
        fun of(
            briefing: ArrivalBriefing,
            previousCountryCode: String?,
            now: Instant,
            zone: ZoneId,
            formats: HeadsUpFormats,
        ): ArrivalBriefingPresentation {
            val subtitle = previousCountryCode?.trim()?.takeIf { it.isNotEmpty() }
                ?.let(formats.countryName)
                ?.takeIf { it != briefing.countryName }
                ?.let { "Arrived from $it" }

            val rows = buildList {
                briefing.time?.let { add(timeRow(it, now, formats)) }
                briefing.emergency?.let {
                    add(
                        Row(
                            id = "emergency",
                            kind = Kind.EMERGENCY,
                            title = "Emergency",
                            text = it.general,
                            detail = it.othersText,
                            tone = Tone.NEUTRAL,
                            flag = null,
                            isSelectable = true,
                        ),
                    )
                }
                briefing.embassy?.let { embassyRow(it)?.let(::add) }
                briefing.road?.let {
                    add(
                        Row(
                            id = "road",
                            kind = Kind.ROAD,
                            title = "Driving",
                            text = it.text,
                            detail = null,
                            tone = if (it.changesFromPreviousCountry) Tone.CAUTION else Tone.NEUTRAL,
                            flag = changedFlag(it.changesFromPreviousCountry),
                            isSelectable = false,
                        ),
                    )
                }
                briefing.power?.let {
                    add(
                        Row(
                            id = "power",
                            kind = Kind.POWER,
                            title = "Power",
                            text = powerText(it.electricity),
                            detail = null,
                            tone = Tone.NEUTRAL,
                            flag = changedFlag(it.differsFromPreviousCountry),
                            isSelectable = false,
                        ),
                    )
                }
                briefing.money?.let {
                    add(
                        Row(
                            id = "money",
                            kind = Kind.MONEY,
                            title = "Currency",
                            text = it.currencyName?.let { name -> "$name (${it.currencyCode})" } ?: it.currencyCode,
                            detail = null,
                            tone = Tone.NEUTRAL,
                            flag = changedFlag(it.changesFromPreviousCountry),
                            isSelectable = false,
                        ),
                    )
                }
                briefing.holiday?.let {
                    add(
                        Row(
                            id = "holiday",
                            kind = Kind.HOLIDAY,
                            title = "Public holiday",
                            text = "${it.name} ${if (it.daysFromToday == 0) "today" else "tomorrow"}",
                            detail = "Shops and offices may be closed",
                            tone = Tone.NEUTRAL,
                            flag = null,
                            isSelectable = false,
                        ),
                    )
                }
                briefing.advisory?.let {
                    add(
                        Row(
                            id = "advisory",
                            kind = Kind.ADVISORY,
                            title = "Travel advisory",
                            text = it.summary,
                            detail = it.sourceName,
                            tone = advisoryTone(it.severity),
                            flag = null,
                            isSelectable = false,
                        ),
                    )
                }
                briefing.rhythm.forEach { period ->
                    val rhythm = LocalRhythmRow.of(period, now, zone, formats.dayMonth)
                    add(
                        Row(
                            id = "rhythm-${rhythm.id}",
                            kind = Kind.RHYTHM,
                            title = rhythm.name,
                            text = rhythm.dates,
                            detail = rhythm.guidance,
                            tone = Tone.NEUTRAL,
                            flag = rhythm.timing,
                            isSelectable = false,
                        ),
                    )
                }
            }

            val collapsedSummary = listOfNotNull(
                briefing.emergency?.headline,
                briefing.road?.takeIf { it.changesFromPreviousCountry }?.text,
            ).joinToString(" · ")

            return ArrivalBriefingPresentation(
                countryCode = briefing.countryCode,
                title = briefing.title,
                subtitle = subtitle,
                rows = rows,
                collapsedSummary = collapsedSummary,
            )
        }

        private fun changedFlag(changed: Boolean): String? = CHANGED_FLAG.takeIf { changed }

        /** "Plug C, E · 230 V · 50 Hz". */
        fun powerText(electricity: CountryElectricity): String =
            listOfNotNull(
                electricity.plugTypes.takeIf { it.isNotEmpty() }?.let { "Plug ${it.joinToString(", ")}" },
                "${electricity.voltage} V",
                "${electricity.frequencyHertz} Hz",
            ).joinToString(" · ")

        /** "Local 10:42 (UTC+0) · Home (Helsinki) is 2 h ahead". */
        private fun timeRow(time: ArrivalBriefing.Time, now: Instant, formats: HeadsUpFormats): Row {
            val zone = try {
                ZoneId.of(time.timeZoneId)
            } catch (_: DateTimeException) {
                null
            }
            val clock = zone?.let { formats.clockTime.format(now.atZone(it)) }
            val local = "${clock?.let { "Local $it" } ?: "Local time"} (${time.utcOffsetText})"
            return Row(
                id = "time",
                kind = Kind.TIME,
                title = "Time",
                text = listOfNotNull(local, time.home?.differenceText).joinToString(" · "),
                detail = null,
                tone = Tone.NEUTRAL,
                flag = null,
                isSelectable = false,
            )
        }

        /** The nearest mission and the ministry hotline; null when neither is known. */
        private fun embassyRow(embassy: ArrivalBriefing.Embassy): Row? {
            val mission = embassy.nearestMission?.mission
            val missionText = mission?.let {
                listOfNotNull(it.name, it.city?.takeUnless { city -> it.name.contains(city) }).joinToString(" · ")
            }
            val hotline = embassy.consularContacts?.let {
                "${EmbassyOverviewBuilder.shortMinistryName(it.ministryName)} ${it.emergencyPhone}"
            }
            val reason = when (embassy.fallbackReason) {
                EmbassyLookup.FallbackReason.EuConsularProtection -> "Under EU consular protection"
                EmbassyLookup.FallbackReason.NordicCooperation -> "Under Nordic cooperation"
                null -> null
            }

            val text = missionText ?: hotline ?: return null
            return Row(
                id = "embassy",
                kind = Kind.EMBASSY,
                title = "Your embassy",
                text = text,
                detail = listOfNotNull(hotline.takeIf { missionText != null }, reason)
                    .joinToString(" · ")
                    .ifEmpty { null },
                tone = Tone.NEUTRAL,
                flag = null,
                isSelectable = true,
            )
        }

        private fun advisoryTone(severity: TravelAlertSeverity): Tone =
            when (severity) {
                TravelAlertSeverity.CAUTION -> Tone.CAUTION
                TravelAlertSeverity.WARNING, TravelAlertSeverity.CRITICAL -> Tone.ATTENTION
                TravelAlertSeverity.CLEAR, TravelAlertSeverity.INFO -> Tone.NEUTRAL
            }
    }
}
