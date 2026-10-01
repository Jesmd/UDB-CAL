package com.example.minimo.data.portal

import java.net.URI

/** The only places the in-app browser may go. */
object PortalUrls {
    const val LOGIN_HOST = "admacad.udb.edu.sv"
    const val PORTAL_HOST = "portal.udb.edu.sv"

    const val LOGIN_URL = "https://admacad.udb.edu.sv/PortalWeb/"
    const val NOTAS_URL = "https://portal.udb.edu.sv/EstudiantesPlus/Notas"

    private val ALLOWED_HOSTS = setOf(LOGIN_HOST, PORTAL_HOST)

    /** True for https URLs on the allowed hosts only. */
    fun isAllowed(url: String?): Boolean = host(url) in ALLOWED_HOSTS

    /** True for the university's login pages. No JavaScript may ever run there. */
    fun isLoginPage(url: String?): Boolean = host(url) == LOGIN_HOST

    /** True for pages served after logging in. */
    fun isPortalPage(url: String?): Boolean = host(url) == PORTAL_HOST

    /** Host and path without query or fragment, safe to show on screen (e.g. "portal.udb.edu.sv/EstudiantesPlus/"). */
    fun describe(url: String?): String? {
        val uri = parse(url) ?: return null
        return (uri.host ?: return null) + uri.path.orEmpty()
    }

    /** The host of an https URL (default port only), or `null` for anything else. */
    private fun host(url: String?): String? {
        val uri = parse(url) ?: return null
        return uri.host?.lowercase()
    }

    private fun parse(url: String?): URI? {
        if (url == null) return null
        val uri = try {
            URI(url)
        } catch (_: java.net.URISyntaxException) {
            return null
        }
        val defaultPort = uri.port == -1 || uri.port == 443
        return if (uri.scheme.equals("https", ignoreCase = true) && defaultPort && uri.userInfo == null) uri else null
    }
}
