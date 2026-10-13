package com.iloapps.nomaddashboard.core.travel.cardcontent

import com.iloapps.nomaddashboard.core.travel.cards.CardTone
import com.iloapps.nomaddashboard.core.model.HolidayPeriod
import com.iloapps.nomaddashboard.core.model.LocalHolidayPhase
import com.iloapps.nomaddashboard.core.model.LocalHolidayStatus
import com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot
import com.iloapps.nomaddashboard.core.model.LocalInfoStatus
import com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorKind
import com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorRow
import com.iloapps.nomaddashboard.core.model.LocalPriceLevelSnapshot
import com.iloapps.nomaddashboard.core.model.LocalPriceSummaryBand
import com.iloapps.nomaddashboard.core.travel.safety.EmergencyNumberDirectory
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** A status chip on a Local Info row. It replaces the row's value while it is shown. */
data class LocalInfoBadge(
    val text: String,
    val tone: CardTone,
)

/**
 * One row of the Local Info card.
 *
 * @property value The answer the row exists to give ("24 Oct · in 12 days", "Moderate").
 * @property detail What the answer refers to ("Labour Day", "Meals −4% · Groceries −4%").
 * @property isValueSelectable Whether the value can be selected and copied (phone numbers).
 */
data class LocalInfoRow(
    val id: String,
    val title: String,
    val value: String,
    val detail: String,
    val badge: LocalInfoBadge? = null,
    val isValueSelectable: Boolean = false,
)

/**
 * The rows of the Local Info card, in the order they are shown: the emergency number, the public
 * holiday, the school break and the price level. Ported from the macOS `LocalInfoSectionPresentation`.
 */
object LocalInfoRows {
    const val EMERGENCY_ID = "emergency-numbers"
    const val PUBLIC_HOLIDAY_ID = "public-holiday"
    const val SCHOOL_HOLIDAY_ID = "school-holiday"
    const val PRICES_VS_EU_ID = "prices-vs-eu"

    /**
     * @param countryCode the current country (device, then IP, then Local Info), which decides the
     * emergency number; the number is bundled and shown without a signal.
     */
    fun of(
        snapshot: LocalInfoSnapshot,
        countryCode: String?,
        time: CardTime,
        now: Instant,
    ): List<LocalInfoRow> {
        if (snapshot.status == LocalInfoStatus.LOCATION_REQUIRED) return emptyList()

        return listOfNotNull(
            emergencyRow(countryCode),
            snapshot.publicHoliday?.let { publicHolidayRow(it, time, now) },
            snapshot.schoolHoliday?.let { schoolHolidayRow(it, time, now) },
        ) + priceRows(snapshot.localPriceLevel)
    }

    /**
     * Where the data applies: "Valencia · Valencian Community, Spain · Europe/Madrid", or what the card
     * is waiting for or missing while there is no place yet.
     */
    fun subtitle(enabled: Boolean, waitingForLocation: Boolean, snapshot: LocalInfoSnapshot): String {
        if (!enabled) return "Location context, holidays, and price signals"
        if (waitingForLocation && snapshot.status == LocalInfoStatus.OFF) {
            return "Checking device location before local context"
        }

        return when (snapshot.status) {
            LocalInfoStatus.OFF -> "Off"
            LocalInfoStatus.CHECKING -> "Looking up local context and holiday calendar"
            LocalInfoStatus.LOCATION_REQUIRED -> "Location required"
            LocalInfoStatus.UNSUPPORTED,
            LocalInfoStatus.UNAVAILABLE,
            LocalInfoStatus.PARTIAL,
            LocalInfoStatus.READY,
            -> place(snapshot) ?: snapshot.countryName ?: "Current place"
        }
    }

    private fun place(snapshot: LocalInfoSnapshot): String? {
        val area = listOfNotNull(
            snapshot.region?.takeIf { it.isNotBlank() && it != snapshot.locality },
            snapshot.countryName?.takeIf(String::isNotBlank),
        ).joinToString(", ").takeIf(String::isNotEmpty)

        return listOfNotNull(snapshot.locality?.takeIf(String::isNotBlank), area, snapshot.timezone?.takeIf(String::isNotBlank))
            .joinToString(" · ")
            .takeIf(String::isNotEmpty)
    }

    /** "Emergency 112" with the other numbers worth knowing; null when the country is not in the directory. */
    fun emergencyRow(countryCode: String?): LocalInfoRow? =
        EmergencyNumberDirectory.numbersFor(countryCode)?.let { numbers ->
            LocalInfoRow(
                id = EMERGENCY_ID,
                title = "Emergency",
                value = numbers.general,
                detail = numbers.othersText.orEmpty(),
                isValueSelectable = true,
            )
        }

    private fun publicHolidayRow(status: LocalHolidayStatus, time: CardTime, now: Instant): LocalInfoRow =
        when (status.phase) {
            LocalHolidayPhase.TODAY,
            LocalHolidayPhase.ON_BREAK,
            -> LocalInfoRow(
                id = PUBLIC_HOLIDAY_ID,
                title = "Public Holiday",
                value = "Today",
                detail = status.period.name,
                badge = LocalInfoBadge("Busy Today", CardTone.CAUTION),
            )

            LocalHolidayPhase.TOMORROW,
            LocalHolidayPhase.NEXT,
            -> LocalInfoRow(
                id = PUBLIC_HOLIDAY_ID,
                title = "Public Holiday",
                value = "${time.dayMonth(status.period.startDate)} · ${relativeDay(status, time, now)}",
                detail = status.period.name,
            )
        }

    private fun schoolHolidayRow(status: LocalHolidayStatus, time: CardTime, now: Instant): LocalInfoRow =
        when (status.phase) {
            LocalHolidayPhase.TODAY,
            LocalHolidayPhase.ON_BREAK,
            -> LocalInfoRow(
                id = SCHOOL_HOLIDAY_ID,
                title = "School Break",
                value = "On break",
                detail = "${status.period.name} · ${range(status.period, time)}",
                badge = LocalInfoBadge("Busy Period", CardTone.CAUTION),
            )

            LocalHolidayPhase.TOMORROW,
            LocalHolidayPhase.NEXT,
            -> LocalInfoRow(
                id = SCHOOL_HOLIDAY_ID,
                title = "School Break",
                value = range(status.period, time),
                detail = status.period.name,
            )
        }

    /** "tomorrow" or "in 12 days"; a holiday the provider calls tomorrow's is tomorrow's. */
    private fun relativeDay(status: LocalHolidayStatus, time: CardTime, now: Instant): String {
        if (status.phase == LocalHolidayPhase.TOMORROW) return "tomorrow"
        return when (val days = ChronoUnit.DAYS.between(time.localDate(now), status.period.startDate)) {
            in Long.MIN_VALUE..0L -> "today"
            1L -> "tomorrow"
            else -> "in $days days"
        }
    }

    /** "1 Apr – 10 Apr", or one day when the break is a single day. */
    private fun range(period: HolidayPeriod, time: CardTime): String {
        val start = time.dayMonth(period.startDate)
        val end = time.dayMonth(period.endDate)
        return if (start == end) start else "$start – $end"
    }

    // Prices

    private val comparisons = listOf(
        "Meals" to LocalPriceIndicatorKind.MEAL_OUT,
        "Groceries" to LocalPriceIndicatorKind.GROCERIES,
        "Overall" to LocalPriceIndicatorKind.OVERALL,
    )

    /**
     * The Eurostat indicators (meals, groceries, overall) collapse into one "Prices vs EU" row: the
     * overall level word as the answer and the three differences as the detail. Any other indicator (the
     * US 1-bedroom rent benchmark) keeps its own value/detail row.
     */
    fun priceRows(level: LocalPriceLevelSnapshot): List<LocalInfoRow> {
        val comparisonKinds = comparisons.map { it.second }.toSet()
        val comparisonRows = comparisons.mapNotNull { (label, kind) ->
            level.rows.firstOrNull { it.kind == kind }?.let { label to it }
        }

        val pricesVsEu = comparisonRows.firstOrNull()?.let { (_, first) ->
            val overall = comparisonRows.firstOrNull { it.second.kind == LocalPriceIndicatorKind.OVERALL }?.second
            LocalInfoRow(
                id = PRICES_VS_EU_ID,
                title = "Prices vs EU",
                value = overall?.value ?: level.summaryBand?.levelWord() ?: first.value,
                detail = comparisonRows.joinToString(" · ") { (label, row) -> "$label ${relativeDelta(row)}" },
            )
        }

        val others = level.rows
            .filter { it.kind !in comparisonKinds }
            .map { row ->
                LocalInfoRow(
                    id = "price-${row.kind.name.lowercase()}",
                    title = row.kind.title(),
                    value = row.value,
                    detail = row.detail,
                )
            }
        return listOfNotNull(pricesVsEu) + others
    }

    /** "−4%" below the EU average, "+3%" above, "≈ EU" within one percent; the level word without a difference. */
    private fun relativeDelta(row: LocalPriceIndicatorRow): String {
        val percent = EuPriceDifference.percent(row) ?: return row.value
        return when {
            abs(percent) <= 1 -> "≈ EU"
            percent > 0 -> "+$percent%"
            else -> "−${abs(percent)}%"
        }
    }

    private fun LocalPriceSummaryBand.levelWord(): String = when (this) {
        LocalPriceSummaryBand.LOW -> "Low"
        LocalPriceSummaryBand.MEDIUM -> "Medium"
        LocalPriceSummaryBand.HIGH -> "High"
        LocalPriceSummaryBand.LIMITED -> "Limited"
    }

    private fun LocalPriceIndicatorKind.title(): String = when (this) {
        LocalPriceIndicatorKind.MEAL_OUT -> "Meal Out"
        LocalPriceIndicatorKind.GROCERIES -> "Groceries"
        LocalPriceIndicatorKind.RENT_ONE_BEDROOM -> "1BR Rent"
        LocalPriceIndicatorKind.OVERALL -> "Overall"
    }
}

/**
 * How far a Eurostat price row is from the EU average, in whole percent (negative below).
 *
 * The row only carries that difference inside its detail text ("16% below EU average · Country
 * fallback · 2024", written by the local price provider), so it is read back from there. The cached
 * rows of earlier versions have the same wording.
 */
internal object EuPriceDifference {
    private val pattern = Regex("""^(\d+)% (below|above) EU average""")

    fun percent(row: LocalPriceIndicatorRow): Int? {
        val match = pattern.find(row.detail.trim()) ?: return null
        val magnitude = match.groupValues[1].toIntOrNull() ?: return null
        return if (match.groupValues[2] == "below") -magnitude else magnitude
    }
}
