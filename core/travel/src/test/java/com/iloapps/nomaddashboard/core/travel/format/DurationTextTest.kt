package com.iloapps.nomaddashboard.core.travel.format

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Test

class DurationTextTest {
    private fun text(minutes: Long, seconds: Long = 0) = DurationText.hoursAndMinutes(Duration.ofMinutes(minutes).plusSeconds(seconds))

    @Test
    fun `durations are rounded to the nearest minute and never below one`() {
        assertThat(text(0, 10)).isEqualTo("1 min")
        assertThat(text(0, 0)).isEqualTo("1 min")
        assertThat(text(45)).isEqualTo("45 min")
        assertThat(text(1, 29)).isEqualTo("1 min")
        assertThat(text(1, 30)).isEqualTo("2 min")
        assertThat(text(60)).isEqualTo("1 h")
        assertThat(text(131)).isEqualTo("2 h 11 min")
        assertThat(text(191)).isEqualTo("3 h 11 min")
    }
}
