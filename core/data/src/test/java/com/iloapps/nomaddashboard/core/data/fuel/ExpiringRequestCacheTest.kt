package com.iloapps.nomaddashboard.core.data.fuel

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ExpiringRequestCacheTest {
    @Test
    fun `coalesces concurrent loads for one key`() = runTest {
        val cache = ExpiringRequestCache<String, String>(maxEntries = 2)
        var calls = 0
        val loaderStarted = CompletableDeferred<Unit>()
        val allowLoaderToFinish = CompletableDeferred<Unit>()

        val first = async {
            cache.getOrLoad(key = "it", ttlMillis = 1_000) {
                calls += 1
                loaderStarted.complete(Unit)
                allowLoaderToFinish.await()
                "catalog"
            }
        }
        loaderStarted.await()
        val second = async {
            cache.getOrLoad(key = "it", ttlMillis = 1_000) {
                calls += 1
                "duplicate"
            }
        }
        runCurrent()

        assertThat(calls).isEqualTo(1)
        allowLoaderToFinish.complete(Unit)

        assertThat(first.await()).isEqualTo("catalog")
        assertThat(second.await()).isEqualTo("catalog")
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `cancellation removes in-flight loader so a later request can proceed`() = runTest {
        val cache = ExpiringRequestCache<String, String>(maxEntries = 2)
        val cancelled = async {
            cache.getOrLoad(key = "it", ttlMillis = 1_000) {
                awaitCancellation()
            }
        }
        runCurrent()
        cancelled.cancelAndJoin()

        assertThat(
            cache.getOrLoad(key = "it", ttlMillis = 1_000) { "fresh" },
        ).isEqualTo("fresh")
    }
}
