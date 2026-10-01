package com.example.minimo.data.portal

/** The authenticated in-app browser, as seen by the sync. Implemented on top of the WebView. */
interface PortalBrowser {
    /**
     * Loads [url] and returns the URL of the page that finally loaded (after redirects).
     * @throws PortalException.LoadFailed or [PortalException.Timeout].
     */
    suspend fun load(url: String): String?

    /**
     * Runs a read-only [script] on the current page and returns its result JSON-encoded, as
     * `WebView.evaluateJavascript` does. Implementations must refuse to run on the login page.
     */
    suspend fun evaluate(script: String): String
}
