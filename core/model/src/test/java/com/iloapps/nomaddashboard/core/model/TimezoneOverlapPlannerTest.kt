package com.iloapps.nomaddashboard.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class TimezoneOverlapPlannerTest {
    @Test
    fun `finds IANA overlap across spring DST boundary`() {
        val planner = TimezonePlanner(
            id = "client",
            label = "Helsinki and New York",
            homeZoneId = "Europe/Helsinki",
            clientZoneId = "America/New_York",
            homeStartMinute = 9 * 60,
            homeEndMinute = 17 * 60,
            clientStartMinute = 9 * 60,
            clientEndMinute = 17 * 60,
            updatedAtEpochMillis = 0,
        )

        val suggestion = TimezoneOverlapPlanner.nextOverlap(
            planner = planner,
            from = Instant.parse("2026-03-29T00:00:00Z"),
        )

        assertThat(suggestion).isNotNull()
        assertThat(suggestion!!.start).isAtLeast(Instant.parse("2026-03-29T12:00:00Z"))
    }

    @Test
    fun `rejects invalid zone without guessing`() {
        val planner = TimezonePlanner("x", "x", "Mars/Olympus", "UTC", updatedAtEpochMillis = 0)
        assertThat(TimezoneOverlapPlanner.nextOverlap(planner, Instant.EPOCH)).isNull()
    }

    @Test
    fun `rounds forward so a suggestion never starts in the past`() {
        val from = Instant.parse("2026-01-12T12:07:31Z")
        val planner = TimezonePlanner("x", "UTC", "UTC", "UTC", 0, 1_439, 0, 1_439, 0)
        assertThat(TimezoneOverlapPlanner.nextOverlap(planner, from)!!.start).isAtLeast(from)
    }
}
