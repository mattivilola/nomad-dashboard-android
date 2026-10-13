package com.iloapps.nomaddashboard.core.travel.cards

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.TravelAlertKind
import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalSnapshot
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalState
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalStatus
import com.iloapps.nomaddashboard.core.model.TravelAlertUnavailableReason
import com.iloapps.nomaddashboard.core.model.TravelAlertsSnapshot
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Test

class TravelAlertsCardPresentationTest {
    private val updatedAt = Instant.parse("2026-10-12T10:00:00Z")

    private fun ready(
        kind: TravelAlertKind,
        severity: TravelAlertSeverity,
        summary: String = "Summary",
        detail: String? = null,
        url: String? = "https://example.org/source",
    ) = TravelAlertSignalState(
        kind = kind,
        status = TravelAlertSignalStatus.READY,
        signal = TravelAlertSignalSnapshot(
            kind = kind,
            severity = severity,
            title = "Title",
            summary = summary,
            detailSummary = detail,
            sourceName = "Smartraveller",
            sourceUrl = url,
            updatedAt = updatedAt,
        ),
        sourceName = "Smartraveller",
        lastAttemptedAt = updatedAt,
        lastSuccessAt = updatedAt,
    )

    private fun stale(kind: TravelAlertKind, severity: TravelAlertSeverity, lastSuccess: Instant?) =
        ready(kind, severity, summary = "Old summary").copy(status = TravelAlertSignalStatus.STALE, lastSuccessAt = lastSuccess)

    private fun unavailable(kind: TravelAlertKind) = TravelAlertSignalState(
        kind = kind,
        status = TravelAlertSignalStatus.UNAVAILABLE,
        reason = TravelAlertUnavailableReason.SOURCE_CONFIGURATION_REQUIRED,
        sourceName = "ReliefWeb",
        diagnosticSummary = "ReliefWeb app name approval required.",
    )

    private fun snapshot(vararg states: TravelAlertSignalState, kinds: List<TravelAlertKind> = states.map { it.kind }) =
        TravelAlertsSnapshot(enabledKinds = kinds, states = states.toList(), primaryCountryCode = "ES", primaryCountryName = "Spain")

    private fun of(snapshot: TravelAlertsSnapshot) = TravelAlertsCardPresentation.of(snapshot, ZoneOffset.UTC)

    @Test
    fun `the badge is the highest level across the signals`() {
        val card = of(
            snapshot(
                ready(TravelAlertKind.ADVISORY, TravelAlertSeverity.CAUTION),
                ready(TravelAlertKind.SECURITY, TravelAlertSeverity.WARNING),
            ),
        )

        assertThat(card.badge).isEqualTo(TravelAlertsBadge("Warning", CardTone.ATTENTION))
    }

    @Test
    fun `badge tones follow the status colours`() {
        fun tone(severity: TravelAlertSeverity) =
            of(snapshot(ready(TravelAlertKind.ADVISORY, severity))).badge.tone

        assertThat(tone(TravelAlertSeverity.CLEAR)).isEqualTo(CardTone.GOOD)
        assertThat(tone(TravelAlertSeverity.INFO)).isEqualTo(CardTone.NEUTRAL)
        assertThat(tone(TravelAlertSeverity.CAUTION)).isEqualTo(CardTone.CAUTION)
        assertThat(tone(TravelAlertSeverity.WARNING)).isEqualTo(CardTone.ATTENTION)
        assertThat(tone(TravelAlertSeverity.CRITICAL)).isEqualTo(CardTone.ATTENTION)
    }

    @Test
    fun `a stale or unavailable signal takes the badge over only when no level is known`() {
        val onlyStale = of(snapshot(stale(TravelAlertKind.ADVISORY, TravelAlertSeverity.CAUTION, updatedAt).copy(signal = null)))
        assertThat(onlyStale.badge.text).isEqualTo("Stale")

        val onlyUnavailable = of(snapshot(unavailable(TravelAlertKind.SECURITY)))
        assertThat(onlyUnavailable.badge).isEqualTo(TravelAlertsBadge("Limited", CardTone.CAUTION))

        val withLevel = of(snapshot(ready(TravelAlertKind.ADVISORY, TravelAlertSeverity.CLEAR), unavailable(TravelAlertKind.SECURITY)))
        assertThat(withLevel.badge.text).isEqualTo("Clear")
        assertThat(withLevel.limitedNotice?.text).isEqualTo("Some sources unavailable")
    }

    @Test
    fun `checking signals read as checking`() {
        val card = of(TravelAlertsSnapshot(primaryCountryCode = "ES", primaryCountryName = "Spain"))

        assertThat(card.badge).isEqualTo(TravelAlertsBadge("Checking", CardTone.NEUTRAL))
        assertThat(card.rows.map { it.summary }).containsExactly("Checking alerts...", "Checking alerts...")
        assertThat(card.rows.map { it.title }).containsExactly("Travel Advisory", "Regional Security").inOrder()
    }

    @Test
    fun `staleness is a caption with the oldest good refresh`() {
        val card = of(
            snapshot(
                stale(TravelAlertKind.ADVISORY, TravelAlertSeverity.CAUTION, Instant.parse("2026-10-12T08:00:00Z")),
                stale(TravelAlertKind.SECURITY, TravelAlertSeverity.INFO, Instant.parse("2026-10-09T08:00:00Z")),
            ),
        )

        assertThat(card.staleNotice?.text).isEqualTo("Updated 9 Oct · may be outdated")
        assertThat(card.notices).containsExactly(card.staleNotice)
        assertThat(card.badge.text).isEqualTo("Caution")
    }

    @Test
    fun `staleness without a known refresh still warns`() {
        val card = of(snapshot(stale(TravelAlertKind.ADVISORY, TravelAlertSeverity.CAUTION, null)))

        assertThat(card.staleNotice?.text).isEqualTo("May be outdated")
    }

    @Test
    fun `disabled kinds do not count as stale`() {
        val card = of(
            snapshot(
                ready(TravelAlertKind.ADVISORY, TravelAlertSeverity.CLEAR),
                stale(TravelAlertKind.SECURITY, TravelAlertSeverity.INFO, updatedAt),
                kinds = listOf(TravelAlertKind.ADVISORY),
            ),
        )

        assertThat(card.staleNotice).isNull()
        assertThat(card.rows.map { it.kind }).containsExactly(TravelAlertKind.ADVISORY)
    }

    @Test
    fun `a row repeats its level only when the badge does not say it`() {
        val card = of(
            snapshot(
                ready(TravelAlertKind.ADVISORY, TravelAlertSeverity.CAUTION),
                ready(TravelAlertKind.SECURITY, TravelAlertSeverity.CLEAR),
            ),
        )

        val (advisory, security) = card.rows
        assertThat(card.showsChip(advisory)).isFalse()
        assertThat(card.showsChip(security)).isTrue()
        assertThat(advisory.impactText).isEqualTo("Exercise caution")
    }

    @Test
    fun `stale and unavailable rows have no chip because the notices and summaries say it`() {
        val card = of(
            snapshot(
                stale(TravelAlertKind.ADVISORY, TravelAlertSeverity.CLEAR, updatedAt),
                unavailable(TravelAlertKind.SECURITY),
            ),
        )

        assertThat(card.rows.none { card.showsChip(it) }).isTrue()
    }

    @Test
    fun `a ready row carries summary detail source and freshness`() {
        val card = of(
            snapshot(
                ready(
                    TravelAlertKind.ADVISORY,
                    TravelAlertSeverity.CAUTION,
                    summary = "France is at Level 2 nearby.",
                    detail = "Exercise a high degree of caution.",
                ),
            ),
        )

        val row = card.rows.single()
        assertThat(row.summary).isEqualTo("France is at Level 2 nearby.")
        assertThat(row.detailSummary).isEqualTo("Exercise a high degree of caution.")
        assertThat(row.sourceUrl).isEqualTo("https://example.org/source")
        assertThat(row.metadataText).isEqualTo("Smartraveller · Updated 12 Oct")
        assertThat(row.tone).isEqualTo(CardTone.CAUTION)
    }

    @Test
    fun `a detail that repeats the summary is dropped`() {
        val card = of(snapshot(ready(TravelAlertKind.ADVISORY, TravelAlertSeverity.CAUTION, summary = "Be careful.", detail = "be careful.")))

        assertThat(card.rows.single().detailSummary).isNull()
    }

    @Test
    fun `a stale row says last known and keeps the source link`() {
        val row = of(snapshot(stale(TravelAlertKind.ADVISORY, TravelAlertSeverity.CAUTION, updatedAt))).rows.single()

        assertThat(row.summary).isEqualTo("Last known: Old summary")
        assertThat(row.statusLabel).isEqualTo("Stale")
        assertThat(row.metadataText).isEqualTo("Smartraveller · Last good refresh 12 Oct")
        assertThat(row.sourceUrl).isNotNull()
    }

    @Test
    fun `an unavailable row shows the diagnostic and no source link`() {
        val row = of(snapshot(unavailable(TravelAlertKind.SECURITY))).rows.single()

        assertThat(row.summary).isEqualTo("ReliefWeb app name approval required.")
        assertThat(row.statusLabel).isEqualTo("Unavailable")
        assertThat(row.sourceUrl).isNull()
        assertThat(row.tone).isEqualTo(CardTone.CAUTION)
        assertThat(row.metadataText).isEqualTo("ReliefWeb · Check attempted")
    }

    @Test
    fun `all clear shows the clear badge and keeps every signal's row`() {
        val card = of(
            snapshot(
                ready(TravelAlertKind.ADVISORY, TravelAlertSeverity.CLEAR, summary = "No advisory."),
                ready(TravelAlertKind.SECURITY, TravelAlertSeverity.CLEAR, summary = "No incidents."),
            ),
        )

        assertThat(card.badge).isEqualTo(TravelAlertsBadge("Clear", CardTone.GOOD))
        assertThat(card.rows.map { it.summary }).containsExactly("No advisory.", "No incidents.").inOrder()
        assertThat(card.rows.none { card.showsChip(it) }).isTrue()
    }

    @Test
    fun `no enabled kinds turns the card off`() {
        val card = of(TravelAlertsSnapshot(enabledKinds = emptyList()))

        assertThat(card.isOff).isTrue()
        assertThat(card.badge).isEqualTo(TravelAlertsBadge("Off", CardTone.NEUTRAL))
        assertThat(card.rows).isEmpty()
    }

    @Test
    fun `the subtitle names the country and its neighbours`() {
        fun subtitle(snapshot: TravelAlertsSnapshot, checking: Boolean = false) =
            TravelAlertsCardPresentation.subtitle(snapshot, checking)

        assertThat(subtitle(TravelAlertsSnapshot(primaryCountryCode = "ES", primaryCountryName = "Spain", coverageCountryCodes = listOf("ES", "FR", "PT"))))
            .isEqualTo("Spain + 2 nearby countries")
        assertThat(subtitle(TravelAlertsSnapshot(primaryCountryCode = "ES", primaryCountryName = "Spain", coverageCountryCodes = listOf("ES", "PT"))))
            .isEqualTo("Spain + 1 nearby country")
        assertThat(subtitle(TravelAlertsSnapshot(primaryCountryCode = "IS", primaryCountryName = "Iceland", coverageCountryCodes = listOf("IS"))))
            .isEqualTo("Iceland")
        assertThat(subtitle(TravelAlertsSnapshot(), checking = true))
            .isEqualTo("Checking device location before country-based alerts")
        assertThat(subtitle(TravelAlertsSnapshot())).isEqualTo("Monitoring travel signals")
    }
}
