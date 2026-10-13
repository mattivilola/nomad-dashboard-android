package com.iloapps.nomaddashboard.core.travel.glance

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.travel.glance.GlanceHeader.CaptionLayout
import org.junit.Test

class GlanceHeaderCaptionTest {
    private val header = GlanceHeader.of(
        device = GlancePlace("Saint-Jean-de-Luz", "Nouvelle-Aquitaine", "France"),
        ip = GlancePlace("Paris", country = "France"),
    )

    @Test
    fun `the first variant that fits on one line wins`() {
        val layout = header.captionLayout { it.device == "France" && it.ip == "Paris, France" }

        assertThat(layout).isEqualTo(CaptionLayout.SingleLine(GlanceHeader.DetailVariant("France", "Paris, France")))
    }

    @Test
    fun `the longest variant is used when everything fits`() {
        val layout = header.captionLayout { true } as CaptionLayout.SingleLine

        assertThat(layout.variant.device).isEqualTo("Nouvelle-Aquitaine, France")
    }

    @Test
    fun `when nothing fits the shortest variant is stacked so the IP part is not cut`() {
        val layout = header.captionLayout { false }

        assertThat(layout).isEqualTo(CaptionLayout.Stacked(GlanceHeader.DetailVariant("France", "Paris")))
    }

    @Test
    fun `no location detail means no caption`() {
        assertThat(GlanceHeader.of(null, null).captionLayout { true }).isNull()
    }
}
