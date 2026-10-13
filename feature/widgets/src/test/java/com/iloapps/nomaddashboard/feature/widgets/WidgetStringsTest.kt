package com.iloapps.nomaddashboard.feature.widgets

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.travel.widget.WidgetKind
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test

/** The picker strings in `strings.xml` must stay the words of [WidgetKind], which the macOS widgets share. */
class WidgetStringsTest {
    private val strings: Map<String, String> by lazy {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent.replace("\\'", "'")
        }
    }

    @Test
    fun `the widget picker names and descriptions match WidgetKind`() {
        assertThat(strings["widget_glance_label"]).isEqualTo(WidgetKind.GLANCE.displayName)
        assertThat(strings["widget_glance_description"]).isEqualTo(WidgetKind.GLANCE.description)
        assertThat(strings["widget_safety_label"]).isEqualTo(WidgetKind.SAFETY.displayName)
        assertThat(strings["widget_safety_description"]).isEqualTo(WidgetKind.SAFETY.description)
        assertThat(strings["widget_daylight_label"]).isEqualTo(WidgetKind.DAYLIGHT.displayName)
        assertThat(strings["widget_daylight_description"]).isEqualTo(WidgetKind.DAYLIGHT.description)
    }
}
