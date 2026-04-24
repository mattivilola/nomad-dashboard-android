package com.iloapps.nomaddashboard.core.network.api

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.Query

class IpifyServiceTest {
    @Test
    fun `lookup ip uses ipify json query on the service root`() {
        val method = IpifyService::class.java.declaredMethods.single { it.name == "lookupIp" }

        assertThat(method.getAnnotation(GET::class.java)?.value).isEqualTo(".")
        val query = method.parameterAnnotations
            .flatMap { annotations -> annotations.filterIsInstance<Query>() }
            .single()
        assertThat(query.value).isEqualTo("format")
    }
}
