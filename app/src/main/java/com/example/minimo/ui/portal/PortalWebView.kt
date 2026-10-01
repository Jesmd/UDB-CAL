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
    fun onPageStarted()
    fun onPageFinished(url: String?)
    fun onProgress(percent: Int)
    fun onLoadError()
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
                listener.onPageStarted()
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
                if (request.isForMainFrame) listener.onLoadError()
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                listener.onLoadError()
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                listener.onLoadError()
                return true
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                listener.onProgress(newProgress)
            }
        }
    }
