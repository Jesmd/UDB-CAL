package com.example.minimo.ui.portal

import android.webkit.WebView
import com.example.minimo.data.portal.PortalBrowser
import com.example.minimo.data.portal.PortalException
import com.example.minimo.data.portal.PortalUrls
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** [PortalBrowser] backed by the visible in-app WebView. Its screen forwards the WebView events here. */
class WebViewPortalBrowser : PortalBrowser {
    lateinit var webView: WebView

    private var pendingLoad: CompletableDeferred<String?>? = null
    private var requestedUrl: String? = null

    fun onPageFinished(url: String?) {
        // Only the page that was asked for, or the login page it may redirect to, ends a load.
        val sameAsRequested = PortalUrls.describe(url) != null && PortalUrls.describe(url) == PortalUrls.describe(requestedUrl)
        if (sameAsRequested || PortalUrls.isLoginPage(url)) pendingLoad?.complete(url)
    }

    fun onLoadError(detail: String) {
        pendingLoad?.completeExceptionally(PortalException.LoadFailed(detail))
    }

    fun onLinkBlocked() {
        pendingLoad?.completeExceptionally(PortalException.LoadFailed("BLOCKED_REDIRECT"))
    }

    override suspend fun load(url: String): String? = withContext(Dispatchers.Main) {
        val done = CompletableDeferred<String?>()
        pendingLoad = done
        requestedUrl = url
        webView.loadUrl(url)
        try {
            withTimeout(LOAD_TIMEOUT_MILLIS) { done.await() }
        } catch (_: TimeoutCancellationException) {
            throw PortalException.Timeout("page")
        } finally {
            if (pendingLoad === done) pendingLoad = null
        }
    }

    override suspend fun evaluate(script: String): String = withContext(Dispatchers.Main) {
        // Scripts only ever run behind the login, never on the login page.
        if (!PortalUrls.isPortalPage(webView.url)) throw PortalException.NotLoggedIn()
        suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(script) { result -> if (continuation.isActive) continuation.resume(result ?: "null") }
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MILLIS = 45_000L
    }
}
