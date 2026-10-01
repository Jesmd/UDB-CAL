package com.example.minimo.ui.portal

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.minimo.data.portal.PortalUrls

/** Events the in-app browser reports to its screen. */
internal interface PortalWebListener {
    fun onPageStarted(url: String?)
    fun onPageFinished(url: String?)
    fun onProgress(percent: Int)
    /** @param detail short technical reason (e.g. "net::ERR_NAME_NOT_RESOLVED"), never a URL. */
    fun onLoadError(detail: String)
    fun onLinkBlocked()
}

/**
 * The in-app browser for the university's portal.
 *
 * Rules: navigation only to [PortalUrls] hosts; no JavaScript bridge to the page (no `addJavascriptInterface`);
 * no file access. JavaScript is enabled because the portal needs it. The app never injects any script from
 * here: the only script it ever runs is the read-only snapshot on pages behind the login (see PortalScreen).
 */
@SuppressLint("SetJavaScriptEnabled")
internal fun createPortalWebView(context: Context, listener: PortalWebListener): WebView =
    WebView(context).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
        }

        webViewClient = object : WebViewClient() {
            // After the first page behind the login, drop the history so "back" never returns to the login form.
            private var historyCleared = false

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (PortalUrls.isAllowed(request.url.toString())) return false
                listener.onLinkBlocked()
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                listener.onPageStarted(url)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (PortalUrls.isLoginPage(url)) historyCleared = false
                if (PortalUrls.isPortalPage(url) && !historyCleared) {
                    view.clearHistory()
                    historyCleared = true
                }
                listener.onPageFinished(url)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) listener.onLoadError(error.description.toString())
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                listener.onLoadError("SSL: ${sslReason(error.primaryError)}")
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                listener.onLoadError("RENDER_PROCESS_GONE")
                return true
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                listener.onProgress(newProgress)
            }
        }
    }

private fun sslReason(primaryError: Int): String = when (primaryError) {
    SslError.SSL_NOTYETVALID -> "CERT_NOT_YET_VALID"
    SslError.SSL_EXPIRED -> "CERT_EXPIRED"
    SslError.SSL_IDMISMATCH -> "CERT_HOST_MISMATCH"
    SslError.SSL_UNTRUSTED -> "CERT_UNTRUSTED"
    SslError.SSL_DATE_INVALID -> "CERT_DATE_INVALID"
    else -> "CERT_INVALID"
}
