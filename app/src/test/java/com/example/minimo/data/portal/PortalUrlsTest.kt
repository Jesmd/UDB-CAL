package com.example.minimo.data.portal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalUrlsTest {
    @Test
    fun constantsAreAllowed() {
        assertTrue(PortalUrls.isAllowed(PortalUrls.LOGIN_URL))
        assertTrue(PortalUrls.isAllowed(PortalUrls.NOTAS_URL))
        assertTrue(PortalUrls.isAllowed("https://portal.udb.edu.sv/EstudiantesPlus/"))
        assertTrue(PortalUrls.isAllowed("https://PORTAL.UDB.EDU.SV/EstudiantesPlus/?x=1#y"))
    }

    @Test
    fun otherHostsAreBlocked() {
        listOf(
            "https://example.com/",
            "https://udb.edu.sv/",
            "https://evil-portal.udb.edu.sv/",
            "https://portal.udb.edu.sv.evil.com/",
            "https://admacad.udb.edu.sv@evil.com/",
            "https://evil.com/?https://portal.udb.edu.sv/",
            "https://evil.com/#@portal.udb.edu.sv",
        ).forEach { assertFalse("$it should be blocked", PortalUrls.isAllowed(it)) }
    }

    @Test
    fun onlyHttpsOnTheDefaultPortIsAllowed() {
        listOf(
            "http://portal.udb.edu.sv/EstudiantesPlus/",
            "ftp://portal.udb.edu.sv/",
            "intent://portal.udb.edu.sv/#Intent;end",
            "javascript:alert(1)",
            "file:///etc/passwd",
            "https://portal.udb.edu.sv:8443/",
            "https://user:pass@portal.udb.edu.sv/",
            "about:blank",
            "",
            "not a url",
        ).forEach { assertFalse("$it should be blocked", PortalUrls.isAllowed(it)) }
        assertFalse(PortalUrls.isAllowed(null))
    }

    @Test
    fun distinguishesLoginFromPortalPages() {
        assertTrue(PortalUrls.isLoginPage("https://admacad.udb.edu.sv/PortalWeb/"))
        assertFalse(PortalUrls.isPortalPage("https://admacad.udb.edu.sv/PortalWeb/"))
        assertTrue(PortalUrls.isPortalPage("https://portal.udb.edu.sv/EstudiantesPlus/"))
        assertFalse(PortalUrls.isLoginPage("https://portal.udb.edu.sv/EstudiantesPlus/"))
        assertFalse(PortalUrls.isLoginPage("https://evil.com/"))
    }

    @Test
    fun describeShowsHostAndPathWithoutQuery() {
        assertEquals(
            "portal.udb.edu.sv/EstudiantesPlus/Notas",
            PortalUrls.describe("https://portal.udb.edu.sv/EstudiantesPlus/Notas?token=secret#frag"),
        )
        assertNull(PortalUrls.describe("http://portal.udb.edu.sv/"))
        assertNull(PortalUrls.describe(null))
    }
}
