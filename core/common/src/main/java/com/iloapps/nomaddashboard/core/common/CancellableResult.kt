package com.iloapps.nomaddashboard.core.common

import kotlinx.coroutines.CancellationException

/** A fallback is an error policy, never a reason to keep cancelled work alive. */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}
