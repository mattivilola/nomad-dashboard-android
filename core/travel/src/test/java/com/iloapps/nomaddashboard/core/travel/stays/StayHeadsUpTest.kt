package com.iloapps.nomaddashboard.core.travel.stays

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpCandidate
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpKind
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpSeverity
import org.junit.Test

class StayHeadsUpTest {
    private fun headsUps(
        days: List<VisitedCountryDay>,
        current: String?,
        citizenships: List<String> = emptyList(),
        stayAllowances: Map<String, Int> = emptyMap(),
        schengenCounterEnabled: Boolean = true,
        taxResidenceDaysEnabled: Boolean = true,
        taxResidenceThresholdDays: Int = 183,
    ): List<HeadsUpCandidate> = StayHeadsUps.evaluate(
        StayHeadsUpInput(
            countryDays = days,
            today = Today,
            currentCountryCode = current,
            citizenships = citizenships,
            stayAllowances = stayAllowances,
            schengenCounterEnabled = schengenCounterEnabled,
            taxResidenceDaysEnabled = taxResidenceDaysEnabled,
            taxResidenceThresholdDays = taxResidenceThresholdDays,
        ),
    )

    // region Stay allowance

    // Reference: MA 2026-07-27 ... 2026-10-12 is 5 + 31 + 30 + 12 = 78 days of 90: 12 left, last day 24 Oct.
    @Test
    fun `allowance with two weeks or less left is a caution`() {
        val headsUp = headsUps(
            days = run(2026, 7, 27, through(2026, 10, 12), "MA"),
            current = "MA",
            stayAllowances = mapOf("MA" to 90),
        ).single()

        assertThat(headsUp.kind).isEqualTo(HeadsUpKind.STAY_ALLOWANCE)
        assertThat(headsUp.decision.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(headsUp.decision.title).isEqualTo("Morocco: 12 days left")
        assertThat(headsUp.decision.detail).isEqualTo("Allowance 90 days · leave by 24 Oct")
        assertThat(headsUp.decision.statusText).isEqualTo("12 days left")
    }

    @Test
    fun `allowance severity and wording follow the days left`() {
        fun headsUp(usedDays: Int) = headsUps(
            days = trailingDays(usedDays, "MA"),
            current = "MA",
            stayAllowances = mapOf("MA" to 90),
        ).firstOrNull()

        assertThat(headsUp(75)).isNull() // 15 left
        assertThat(headsUp(76)?.decision?.severity).isEqualTo(HeadsUpSeverity.CAUTION) // 14 left
        assertThat(headsUp(86)?.decision?.severity).isEqualTo(HeadsUpSeverity.CAUTION) // 4 left
        assertThat(headsUp(87)?.decision?.severity).isEqualTo(HeadsUpSeverity.ATTENTION) // 3 left
        assertThat(headsUp(89)!!.decision.title).isEqualTo("Morocco: 1 day left")
        assertThat(headsUp(90)!!.decision.title).isEqualTo("Morocco: last day")
        assertThat(headsUp(90)!!.decision.statusText).isEqualTo("Last day")
        val over = headsUp(92)!!
        assertThat(over.decision.severity).isEqualTo(HeadsUpSeverity.ATTENTION)
        assertThat(over.decision.title).isEqualTo("Morocco: 2 days over")
        assertThat(over.decision.statusText).isEqualTo("2 days over")
        assertThat(over.decision.detail).isEqualTo("Allowance 90 days · ended 10 Oct")
    }

    @Test
    fun `allowance needs an allowance for the current country`() {
        val days = trailingDays(80, "MA")

        assertThat(headsUps(days, "MA", stayAllowances = mapOf("ES" to 90))).isEmpty()
        assertThat(headsUps(days, "MA")).isEmpty()
        assertThat(headsUps(days, null, stayAllowances = mapOf("MA" to 90))).isEmpty()
        assertThat(headsUps(days, "ES", stayAllowances = mapOf("MA" to 90))).isEmpty()
    }

    @Test
    fun `the live country covers a today that is not recorded yet`() {
        val throughYesterday = trailingDays(80, "MA").filter { it.date != Today }
        val headsUp = headsUps(throughYesterday, "ma", stayAllowances = mapOf("MA" to 90)).single()

        assertThat(headsUp.decision.title).isEqualTo("Morocco: 10 days left")
    }

    // endregion

    // region Schengen

    // Reference: FR for the last 80 days up to 2026-10-12: used 80, 10 left, last day 22 Oct.
    @Test
    fun `schengen with two weeks or less left is a caution`() {
        val headsUp = headsUps(trailingDays(80, "FR"), "FR", citizenships = listOf("US")).single()

        assertThat(headsUp.kind).isEqualTo(HeadsUpKind.SCHENGEN_LIMIT)
        assertThat(headsUp.decision.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(headsUp.decision.title).isEqualTo("Schengen: 10 of 90 days left")
        assertThat(headsUp.decision.detail).isEqualTo("Leave by 22 Oct")
        assertThat(headsUp.decision.statusText).isEqualTo("Schengen 10d")
    }

    @Test
    fun `schengen severity follows the days left`() {
        fun headsUp(usedDays: Int) = headsUps(trailingDays(usedDays, "FR"), "FR", citizenships = listOf("US")).firstOrNull()

        assertThat(headsUp(75)).isNull() // 15 left
        assertThat(headsUp(76)?.decision?.severity).isEqualTo(HeadsUpSeverity.CAUTION) // 14 left
        assertThat(headsUp(87)?.decision?.severity).isEqualTo(HeadsUpSeverity.ATTENTION) // 3 left
        assertThat(headsUp(90)?.decision?.title).isEqualTo("Schengen: 0 of 90 days left")
        assertThat(headsUp(93)?.decision?.detail).isEqualTo("Over the limit by 3 days")
        assertThat(headsUp(93)?.decision?.severity).isEqualTo(HeadsUpSeverity.ATTENTION)
    }

    @Test
    fun `schengen stays quiet when it does not apply or is switched off`() {
        val days = trailingDays(85, "FR")

        assertThat(headsUps(days, "FR", citizenships = listOf("FI"))).isEmpty()
        assertThat(headsUps(days, "FR", citizenships = listOf("US", "DE"))).isEmpty()
        // No citizenship: the card says it may not apply, the heads-up stays silent.
        assertThat(headsUps(days, "FR")).isEmpty()
        assertThat(headsUps(days, "FR", citizenships = listOf("US"), schengenCounterEnabled = false)).isEmpty()
        // Not in Schengen today: there is nothing to leave.
        val abroad = trailingDays(85, "FR").filter { it.date != Today } + visit(Today, "GB")
        assertThat(headsUps(abroad, "GB", citizenships = listOf("US"))).isEmpty()
    }

    // endregion

    // region Tax residence days

    // Reference: ES on 160 days of 2026 (159 recorded 01-01 ... 06-08, plus today from the live location).
    @Test
    fun `tax residence days appear a month before the threshold`() {
        val headsUp = headsUps(yearStart(159, "ES"), "ES").single()

        assertThat(headsUp.kind).isEqualTo(HeadsUpKind.TAX_RESIDENCE_DAYS)
        assertThat(headsUp.decision.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(headsUp.decision.title).isEqualTo("Spain: 160 days this year")
        assertThat(headsUp.decision.detail).isEqualTo("183-day tax residence threshold · not tax advice")
        assertThat(headsUp.decision.statusText).isEqualTo("160 days")
    }

    @Test
    fun `tax residence severity follows the threshold`() {
        fun headsUp(days: Int, threshold: Int = 183) =
            headsUps(yearStart(days - 1, "ES"), "ES", taxResidenceThresholdDays = threshold).firstOrNull()

        assertThat(headsUp(152)).isNull()
        assertThat(headsUp(153)?.decision?.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(headsUp(175)?.decision?.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(headsUp(176)?.decision?.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(headsUp(190)?.decision?.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(headsUp(70, 100)?.decision?.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(headsUp(69, 100)).isNull()
        assertThat(headsUp(93, 100)?.decision?.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(headsUp(70, 100)?.decision?.detail).isEqualTo("100-day tax residence threshold · not tax advice")
    }

    @Test
    fun `tax residence is about the current country this year only`() {
        val spainLastYear = run(2025, 1, 1, through(2025, 12, 31), "ES")
        val spainEarlier = yearStart(170, "ES")

        assertThat(headsUps(spainLastYear, "ES")).isEmpty()
        assertThat(headsUps(spainEarlier, "PT")).isEmpty()
        assertThat(headsUps(spainEarlier, null)).isEmpty()
        assertThat(headsUps(spainEarlier, "ES", taxResidenceDaysEnabled = false)).isEmpty()
    }

    // endregion

    // region Combined

    @Test
    fun `no stay input means no stay heads-ups`() {
        assertThat(headsUps(emptyList(), null)).isEmpty()
    }

    @Test
    fun `evaluate returns allowance then schengen then tax days`() {
        // Schengen has 2 days left (attention) while the allowance has 12 (caution) and tax days are info.
        val result = headsUps(
            days = trailingDays(88, "FR"),
            current = "FR",
            citizenships = listOf("US"),
            stayAllowances = mapOf("FR" to 100),
        )

        assertThat(result.map { it.kind }).containsExactly(
            HeadsUpKind.STAY_ALLOWANCE,
            HeadsUpKind.SCHENGEN_LIMIT,
        ).inOrder()
        assertThat(result.map { it.decision.severity }).containsExactly(
            HeadsUpSeverity.CAUTION,
            HeadsUpSeverity.ATTENTION,
        ).inOrder()
    }

    @Test
    fun `invalid current country codes are ignored`() {
        val days = trailingDays(80, "MA")

        assertThat(headsUps(days, "Morocco", stayAllowances = mapOf("MA" to 90))).isEmpty()
        assertThat(headsUps(days, "", stayAllowances = mapOf("MA" to 90))).isEmpty()
    }

    @Test
    fun `country names and dates use the injected format`() {
        val format = StayHeadsUpFormat(countryName = { "<$it>" }, day = { "D${it.dayOfMonth}" })
        val result = StayHeadsUps.evaluate(
            StayHeadsUpInput(
                countryDays = run(2026, 7, 27, through(2026, 10, 12), "MA"),
                today = Today,
                currentCountryCode = "MA",
                stayAllowances = mapOf("MA" to 90),
            ),
            format,
        ).single()

        assertThat(result.decision.title).isEqualTo("<MA>: 12 days left")
        assertThat(result.decision.detail).isEqualTo("Allowance 90 days · leave by D24")
    }

    // endregion
}
