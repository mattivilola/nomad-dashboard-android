package com.iloapps.nomaddashboard.core.data.repository

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.delay
import retrofit2.HttpException

internal suspend fun <T> retryTransient(
    maxAttempts: Int = 3,
    initialDelayMillis: Long = 300,
    block: suspend () -> T,
): T {
    var currentDelay = initialDelayMillis
    var lastError: Throwable? = null

    repeat(maxAttempts) { attempt ->
        try {
            return block()
        } catch (error: Throwable) {
            lastError = error
            if (attempt == maxAttempts - 1 || error.isTransientRetryable().not()) {
                throw error
            }
        }
        delay(currentDelay)
        currentDelay *= 2
    }

    throw lastError ?: IllegalStateException("retryTransient exhausted without a captured error")
}

internal fun Throwable.isTransientRetryable(): Boolean {
    val cause = cause
    return when {
        this is HttpException && code() >= 500 -> true
        this is SocketTimeoutException -> true
        this is UnknownHostException -> true
        this is IOException -> true
        message?.contains("HTTP 5", ignoreCase = true) == true -> true
        cause != null -> cause.isTransientRetryable()
        else -> false
    }
}
