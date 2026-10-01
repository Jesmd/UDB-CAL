package com.example.minimo.data.portal

/** Why reading the portal failed. Messages are technical; the UI shows its own text for each case. */
sealed class PortalException(message: String) : Exception(message) {
    /** The portal sent the browser to the login page. */
    class NotLoggedIn : PortalException("Not logged in")

    /** The page did not load (no network, server error...). [detail] is the browser's reason. */
    class LoadFailed(val detail: String) : PortalException("Load failed: $detail")

    /** The portal did not answer in time. */
    class Timeout(val what: String) : PortalException("Timeout: $what")

    /** The portal could not show the detail of [moduleCode] (it cleared the detail instead). */
    class DetailFailed(val moduleCode: String) : PortalException("Detail failed: $moduleCode")

    /** The HTML no longer has the expected structure: the parser must be updated with a new capture. */
    class FormatChanged(detail: String) : PortalException(detail)
}
