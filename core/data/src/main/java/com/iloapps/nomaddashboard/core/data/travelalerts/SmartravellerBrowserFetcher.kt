package com.iloapps.nomaddashboard.core.data.travelalerts

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject

interface SmartravellerBrowserFetcher {
    suspend fun destinationsHtml(): String
}

@Singleton
class AndroidWebViewSmartravellerBrowserFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
) : SmartravellerBrowserFetcher {
    override suspend fun destinationsHtml(): String = suspendCancellableCoroutine { continuation ->
        val mainHandler = Handler(Looper.getMainLooper())
        val completed = AtomicBoolean(false)
        var webView: WebView? = null

        fun finishWithResult(result: Result<String>) {
            if (completed.compareAndSet(false, true).not()) {
                return
            }
            val target = webView
            mainHandler.post {
                target?.stopLoading()
                target?.destroy()
                if (continuation.isActive) {
                    result.fold(
                        onSuccess = continuation::resume,
                        onFailure = continuation::resumeWithException,
                    )
                }
            }
        }

        val timeoutRunnable = Runnable {
            finishWithResult(
                Result.failure(
                    TravelAlertSourceException(
                        diagnosticSummary = "Smartraveller browser fallback timed out.",
                        message = "Timed out while loading Smartraveller in WebView fallback.",
                    ),
                ),
            )
        }

        continuation.invokeOnCancellation {
            if (completed.compareAndSet(false, true)) {
                mainHandler.post {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    webView?.stopLoading()
                    webView?.destroy()
                }
            }
        }

        mainHandler.post {
            val target = WebView(context)
            webView = target
            mainHandler.postDelayed(timeoutRunnable, BrowserFetchTimeoutMillis)
            target.settings.javaScriptEnabled = true
            target.settings.domStorageEnabled = true
            target.settings.loadsImagesAutomatically = false
            target.settings.blockNetworkImage = true
            target.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    if (request?.isForMainFrame == false) {
                        return
                    }
                    finishWithResult(
                        Result.failure(
                            TravelAlertSourceException(
                                diagnosticSummary = "Smartraveller browser fallback failed.",
                                message = error?.description?.toString()
                                    ?: "WebView failed while loading Smartraveller.",
                            ),
                        ),
                    )
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    if (request?.isForMainFrame == false) {
                        return
                    }
                    finishWithResult(
                        Result.failure(
                            TravelAlertSourceException(
                                diagnosticSummary = "Smartraveller browser fallback returned HTTP ${errorResponse?.statusCode ?: 0}.",
                                message = "WebView fallback received HTTP ${errorResponse?.statusCode ?: 0} from Smartraveller.",
                            ),
                        ),
                    )
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    view.evaluateJavascript(
                        "(function(){return document.documentElement.outerHTML;})()",
                    ) { htmlValue ->
                        mainHandler.removeCallbacks(timeoutRunnable)
                        val html = decodeEvaluateJavascriptResult(htmlValue)
                        if (html.isNullOrBlank()) {
                            finishWithResult(
                                Result.failure(
                                    TravelAlertSourceException(
                                        diagnosticSummary = "Smartraveller browser fallback returned empty HTML.",
                                        message = "WebView fallback produced no HTML content.",
                                    ),
                                ),
                            )
                        } else {
                            finishWithResult(Result.success(html))
                        }
                    }
                }
            }
            target.loadUrl(SmartravellerDestinationsUrl)
        }
    }

    private fun decodeEvaluateJavascriptResult(value: String?): String? {
        if (value.isNullOrBlank() || value == "null") {
            return null
        }
        return runCatching {
            JSONObject("""{"html":$value}""").getString("html")
        }.getOrNull()
    }

    private companion object {
        const val SmartravellerDestinationsUrl = "https://www.smartraveller.gov.au/destinations"
        const val BrowserFetchTimeoutMillis = 20_000L
    }
}
