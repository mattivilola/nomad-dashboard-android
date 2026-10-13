package com.iloapps.nomaddashboard.feature.dashboard

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EmbassyCardSupportTest {
    @Test
    fun `a dialable number keeps the digits and the leading plus`() {
        assertThat(dialableNumber("+358 9 1605 5555")).isEqualTo("+358916055555")
        assertThat(dialableNumber("  +1 (202) 501-4444 ")).isEqualTo("+12025014444")
        assertThat(dialableNumber("0800 123 456")).isEqualTo("0800123456")
    }

    @Test
    fun `text without digits cannot be dialled`() {
        assertThat(dialableNumber("")).isNull()
        assertThat(dialableNumber("n/a")).isNull()
    }
}
