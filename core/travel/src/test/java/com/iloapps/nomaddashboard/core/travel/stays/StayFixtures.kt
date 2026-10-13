package com.iloapps.nomaddashboard.core.travel.stays

import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedCountryDayProvenance
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Today in the reference tests: Monday 2026-10-12. */
internal val Today: LocalDate = LocalDate.of(2026, 10, 12)

internal val Paris: ZoneId = ZoneId.of("Europe/Paris")

internal fun date(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)

/** The calendar day an instant falls on in Paris, as the app derives "today" from the clock. */
internal fun parisDay(year: Int, month: Int, day: Int, hour: Int, minute: Int): LocalDate =
    date(year, month, day).atTime(hour, minute).atZone(Paris).toInstant().let(::parisDay)

internal fun parisDay(instant: Instant): LocalDate = instant.atZone(Paris).toLocalDate()

internal fun visit(
    year: Int,
    month: Int,
    day: Int,
    code: String,
    inferred: Boolean = false,
    manual: Boolean = false,
): VisitedCountryDay = visit(date(year, month, day), code, inferred, manual)

internal fun visit(date: LocalDate, code: String, inferred: Boolean = false, manual: Boolean = false) =
    VisitedCountryDay(
        date = date,
        country = code,
        countryCode = code,
        source = VisitedPlaceSource.DEVICE_LOCATION,
        isInferred = inferred,
        provenance = when {
            manual -> VisitedCountryDayProvenance.MANUAL
            inferred -> VisitedCountryDayProvenance.INFERRED
            else -> VisitedCountryDayProvenance.OBSERVED
        },
    )

/** One record per day from the start day through the end day, both included. */
internal fun run(start: LocalDate, end: LocalDate, code: String): List<VisitedCountryDay> =
    generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { visit(it, code) }.toList()

internal fun run(
    year: Int,
    month: Int,
    day: Int,
    through: Triple<Int, Int, Int>,
    code: String,
): List<VisitedCountryDay> =
    run(date(year, month, day), date(through.first, through.second, through.third), code)

internal fun through(year: Int, month: Int, day: Int) = Triple(year, month, day)

/** One record per day, [count] days ending on [Today]. */
internal fun trailingDays(count: Int, code: String): List<VisitedCountryDay> =
    run(Today.minusDays(count - 1L), Today, code)

/** The first [count] days of 2026. */
internal fun yearStart(count: Int, code: String): List<VisitedCountryDay> =
    run(date(2026, 1, 1), date(2026, 1, 1).plusDays(count - 1L), code)
