package com.example.minimo.data.portal

/**
 * What the app believes about the portal session. It is inferred only from which page the in-app browser
 * lands on; no cookie is ever read.
 */
enum class SessionState {
    /** Not logged in (also the state when the app starts: nothing has been verified yet). */
    LoggedOut,

    /** A page behind the login was reached. */
    LoggedIn,

    /** The session had been active and the portal sent the browser back to the login page. */
    Expired,
    ;

    /** The state after a page on [pageUrl] finished loading. */
    fun afterPageLoaded(pageUrl: String?): SessionState = when {
        PortalUrls.isPortalPage(pageUrl) -> LoggedIn
        PortalUrls.isLoginPage(pageUrl) -> if (this == LoggedIn) Expired else this
        else -> this
    }
}
