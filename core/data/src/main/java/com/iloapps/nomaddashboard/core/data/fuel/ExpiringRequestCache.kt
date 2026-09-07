package com.iloapps.nomaddashboard.core.data.fuel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A small, in-memory cache for expensive public fuel feeds. Concurrent requests for the same
 * key share one loader so a refresh burst does not duplicate a country catalog download.
 */
internal class ExpiringRequestCache<K, V>(
    private val maxEntries: Int,
) {
    private val mutex = Mutex()
    private val cached = object : LinkedHashMap<K, CachedValue<V>>(maxEntries, 0.75f, true) {}
    private val inFlight = mutableMapOf<K, CompletableDeferred<V>>()

    suspend fun getOrLoad(
        key: K,
        ttlMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        loader: suspend () -> V,
    ): V {
        val (deferred, isLoader) = mutex.withLock {
            cached[key]?.takeIf { nowMillis - it.savedAtMillis in 0..ttlMillis }?.let { cachedValue ->
                return cachedValue.value
            }
            inFlight[key]?.let { existing ->
                return@withLock existing to false
            }
            CompletableDeferred<V>().also { created ->
                inFlight[key] = created
            } to true
        }

        if (!isLoader) {
            return deferred.await()
        }

        try {
            val value = loader()
            mutex.withLock {
                cached[key] = CachedValue(value = value, savedAtMillis = nowMillis)
                while (cached.size > maxEntries) {
                    cached.entries.iterator().next().also { cached.remove(it.key) }
                }
            }
            deferred.complete(value)
            return value
        } catch (error: CancellationException) {
            deferred.cancel(error)
            throw error
        } catch (error: Throwable) {
            deferred.completeExceptionally(error)
            throw error
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (inFlight[key] === deferred) {
                        inFlight.remove(key)
                    }
                }
            }
        }
    }

    suspend fun clear() {
        mutex.withLock { cached.clear() }
    }

    private data class CachedValue<V>(
        val value: V,
        val savedAtMillis: Long,
    )
}
