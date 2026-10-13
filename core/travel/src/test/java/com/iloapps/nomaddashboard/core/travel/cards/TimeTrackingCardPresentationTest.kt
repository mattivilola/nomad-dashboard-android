package com.iloapps.nomaddashboard.core.travel.cards

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.TimeTrackingDayReport
import com.iloapps.nomaddashboard.core.model.TimeTrackingProject
import com.iloapps.nomaddashboard.core.model.TimeTrackingProjectReport
import com.iloapps.nomaddashboard.core.model.TimeTrackingReportSnapshot
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import org.junit.Test

class TimeTrackingCardPresentationTest {
    private val today = LocalDate.of(2026, 10, 13)

    private fun bucket(title: String, minutes: Long, interruptions: Int = 0) = AllocatedBucket(
        id = title.lowercase(),
        title = title,
        duration = Duration.ofMinutes(minutes),
        interruptionCount = interruptions,
        focusAdjustedDuration = Duration.ofMinutes(minutes),
    )

    private fun report(vararg projects: Pair<String, Long>, focusLoss: Long = 0, interruptions: Int = 0): TimeTrackingReportSnapshot {
        val projectReports = projects.map { (name, minutes) ->
            TimeTrackingProjectReport(
                project = TimeTrackingProject(id = UUID.nameUUIDFromBytes(name.toByteArray()), name = name),
                reportedDuration = Duration.ofMinutes(minutes),
                estimatedFocusTime = Duration.ofMinutes(minutes),
            )
        }
        val allocated = projectReports.fold(Duration.ZERO) { total, item -> total + item.reportedDuration }
        return TimeTrackingReportSnapshot(
            interruptionsToday = interruptions,
            todaysEstimatedFocusLoss = Duration.ofMinutes(focusLoss),
            todaysAllocatedDuration = allocated,
            dayReports = listOf(TimeTrackingDayReport(date = today, allocatedDuration = allocated, projectReports = projectReports)),
        )
    }

    @Test
    fun `durations are whole minutes without zero padding`() {
        assertThat(TimeTrackingDurationFormatting.text(Duration.ofMinutes(42))).isEqualTo("42m")
        assertThat(TimeTrackingDurationFormatting.text(Duration.ofHours(3))).isEqualTo("3h")
        assertThat(TimeTrackingDurationFormatting.text(Duration.ofMinutes(418))).isEqualTo("6h 58m")
        assertThat(TimeTrackingDurationFormatting.text(Duration.ofSeconds(89))).isEqualTo("1m")
        assertThat(TimeTrackingDurationFormatting.text(Duration.ofSeconds(-5))).isEqualTo("0m")
    }

    @Test
    fun `today focus and pending add up from the allocation and the buffer`() {
        val card = TimeTrackingCardPresentation.of(
            report = report("Atlas" to 120L, "Other" to 30L, focusLoss = 46, interruptions = 2),
            pending = Duration.ofMinutes(60),
            today = today,
        )

        assertThat(card.today).isEqualTo("3h 30m")
        assertThat(card.focus).isEqualTo("2h 44m")
        assertThat(card.pending).isEqualTo("1h")
    }

    @Test
    fun `focus never goes below zero`() {
        val card = TimeTrackingCardPresentation.of(report(focusLoss = 90), pending = Duration.ofMinutes(10), today = today)

        assertThat(card.focus).isEqualTo("0m")
    }

    @Test
    fun `only today's day report feeds the allocation`() {
        val old = TimeTrackingDayReport(
            date = today.minusDays(1),
            projectReports = listOf(
                TimeTrackingProjectReport(TimeTrackingProject(name = "Yesterday"), reportedDuration = Duration.ofHours(8)),
            ),
        )
        val card = TimeTrackingCardPresentation.of(
            report = report("Atlas" to 60L).let { it.copy(dayReports = it.dayReports + old) },
            pending = Duration.ZERO,
            today = today,
        )

        assertThat(card.allocation.legend.map { it.title }).containsExactly("Atlas")
    }

    @Test
    fun `a day without a report is empty`() {
        val card = TimeTrackingCardPresentation.of(TimeTrackingReportSnapshot(), Duration.ZERO, today)

        assertThat(card.today).isEqualTo("0m")
        assertThat(card.allocation.segments).isEmpty()
        assertThat(card.allocation.legend).isEmpty()
        assertThat(card.allocation.focusLossCaption).isNull()
    }

    @Test
    fun `the interruption help states the cost`() {
        assertThat(TimeTrackingCardPresentation.of(report(), Duration.ZERO, today).interruptionHelp)
            .isEqualTo("Report an interruption. Focus cooldown assumes 23 minutes per interruption.")
        assertThat(TimeTrackingCardPresentation.of(report(focusLoss = 46, interruptions = 2), Duration.ZERO, today).interruptionHelp)
            .isEqualTo("2 interruptions today. Estimated focus loss 46m.")
        assertThat(TimeTrackingCardPresentation.of(report(focusLoss = 23, interruptions = 1), Duration.ZERO, today).interruptionHelp)
            .isEqualTo("1 interruption today. Estimated focus loss 23m.")
    }

    // region Allocation bar

    @Test
    fun `buckets are ordered largest first and the pending remainder comes last`() {
        val allocation = TimeTrackingAllocationPresentation.of(
            allocated = listOf(bucket("Small", 30), bucket("Large", 120), bucket("Medium", 60)),
            pending = Duration.ofMinutes(90),
            estimatedFocusLoss = Duration.ZERO,
        )

        assertThat(allocation.segments.map { it.title }).containsExactly("Large", "Medium", "Small", "Pending").inOrder()
        assertThat(allocation.legend.map { it.title }).containsExactly("Large", "Medium", "Small", "Pending").inOrder()
        assertThat(allocation.segments.sumOf { it.fraction }).isWithin(1e-9).of(1.0)
        assertThat(allocation.segments.first().fraction).isWithin(1e-9).of(120.0 / 300.0)
        assertThat(allocation.segments.last().kind).isEqualTo(TimeTrackingAllocationPresentation.SegmentKind.Pending)
    }

    @Test
    fun `allocated segments take increasing tone indexes`() {
        val allocation = TimeTrackingAllocationPresentation.of(
            allocated = listOf(bucket("A", 60), bucket("B", 30)),
            pending = Duration.ZERO,
            estimatedFocusLoss = Duration.ZERO,
        )

        assertThat(allocation.segments.map { it.kind }).containsExactly(
            TimeTrackingAllocationPresentation.SegmentKind.Allocated(0),
            TimeTrackingAllocationPresentation.SegmentKind.Allocated(1),
        ).inOrder()
    }

    @Test
    fun `the legend holds four buckets then says how many more`() {
        val buckets = (1..6).map { bucket("Project $it", 100L - it) }

        val allocation = TimeTrackingAllocationPresentation.of(buckets, Duration.ofMinutes(10), Duration.ZERO)

        assertThat(allocation.legend.map { it.title })
            .containsExactly("Project 1", "Project 2", "Project 3", "Project 4", "Pending").inOrder()
        assertThat(allocation.hiddenLegendCount).isEqualTo(2)
        assertThat(allocation.hiddenLegendText).isEqualTo("+2 more")
        assertThat(allocation.segments).hasSize(7)
    }

    @Test
    fun `four buckets or fewer have no more line`() {
        val allocation = TimeTrackingAllocationPresentation.of(listOf(bucket("A", 5)), Duration.ZERO, Duration.ZERO)

        assertThat(allocation.hiddenLegendText).isNull()
    }

    @Test
    fun `empty buckets stay in the legend but draw no segment`() {
        val allocation = TimeTrackingAllocationPresentation.of(
            allocated = listOf(bucket("Atlas", 60), bucket("Idle", 0)),
            pending = Duration.ZERO,
            estimatedFocusLoss = Duration.ZERO,
        )

        assertThat(allocation.segments.map { it.title }).containsExactly("Atlas")
        assertThat(allocation.legend.map { it.title }).containsExactly("Atlas", "Idle").inOrder()
    }

    @Test
    fun `help texts include interruptions and the focus adjusted time`() {
        val allocation = TimeTrackingAllocationPresentation.of(
            allocated = listOf(bucket("Atlas", 120, interruptions = 2).copy(focusAdjustedDuration = Duration.ofMinutes(74))),
            pending = Duration.ofMinutes(30),
            estimatedFocusLoss = Duration.ofMinutes(15),
        )

        assertThat(allocation.legend.first().helpText).isEqualTo("Atlas: 2h · 2 interruptions · focus 1h 14m")
        assertThat(allocation.legend.last().helpText).isEqualTo("Pending: 30m still to allocate")
        assertThat(allocation.barHelpText).isEqualTo("Allocated 2h of 2h 30m tracked today · 30m pending")
    }

    @Test
    fun `the focus loss caption appears only with a loss`() {
        assertThat(
            TimeTrackingAllocationPresentation.of(emptyList(), Duration.ZERO, Duration.ofMinutes(15)).focusLossCaption,
        ).isEqualTo("≈15m focus lost")
        assertThat(
            TimeTrackingAllocationPresentation.of(emptyList(), Duration.ZERO, Duration.ofMinutes(83)).focusLossCaption,
        ).isEqualTo("≈1h 23m focus lost")
        assertThat(TimeTrackingAllocationPresentation.of(emptyList(), Duration.ZERO, Duration.ZERO).focusLossCaption).isNull()
    }

    // endregion
}
