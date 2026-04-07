package com.iloapps.nomaddashboard.core.data.travelalerts

import java.io.IOException

internal interface TravelAlertDiagnosticError {
    val diagnosticSummary: String
}

internal open class TravelAlertSourceException(
    override val diagnosticSummary: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause), TravelAlertDiagnosticError

internal sealed class ReliefWebProviderError(
    override val diagnosticSummary: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause), TravelAlertDiagnosticError {
    class RequestFailed(cause: IOException) : ReliefWebProviderError(
        diagnosticSummary = "ReliefWeb could not be reached.",
        message = "ReliefWeb request failed before a response was received.",
        cause = cause,
    )

    class AppNameApprovalRequired(message: String?) : ReliefWebProviderError(
        diagnosticSummary = "ReliefWeb app name approval required.",
        message = message?.takeIf { it.isNotBlank() } ?: "ReliefWeb app name approval required.",
    )

    class AppNameMissing(message: String?) : ReliefWebProviderError(
        diagnosticSummary = "ReliefWeb app name missing from request.",
        message = message?.takeIf { it.isNotBlank() } ?: "ReliefWeb app name missing from request.",
    )

    class UnexpectedStatus(statusCode: Int, bodySnippet: String?) : ReliefWebProviderError(
        diagnosticSummary = "ReliefWeb returned HTTP $statusCode.",
        message = bodySnippet?.takeIf { it.isNotBlank() }?.let {
            "ReliefWeb returned HTTP $statusCode. Body snippet: $it"
        } ?: "ReliefWeb returned HTTP $statusCode.",
    )

    class InvalidPayload(message: String) : ReliefWebProviderError(
        diagnosticSummary = "ReliefWeb response format changed.",
        message = "ReliefWeb response format changed: $message",
    )
}
