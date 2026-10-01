package com.example.minimo.data.portal

import com.example.minimo.data.portal.SessionState.Expired
import com.example.minimo.data.portal.SessionState.LoggedIn
import com.example.minimo.data.portal.SessionState.LoggedOut
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStateTest {
    private val login = "https://admacad.udb.edu.sv/PortalWeb/"
    private val home = "https://portal.udb.edu.sv/EstudiantesPlus/"

    private fun visit(start: SessionState, vararg pages: String?) = pages.fold(start) { state, page -> state.afterPageLoaded(page) }

    @Test
    fun staysLoggedOutOnTheLoginPage() {
        assertEquals(LoggedOut, visit(LoggedOut, login))
    }

    @Test
    fun logsInWhenAPageBehindTheLoginLoads() {
        assertEquals(LoggedIn, visit(LoggedOut, login, home))
    }

    @Test
    fun expiresWhenSentBackToTheLoginAfterBeingLoggedIn() {
        assertEquals(Expired, visit(LoggedOut, login, home, login))
    }

    @Test
    fun staysExpiredOnTheLoginPageAndRecoversAfterLoggingInAgain() {
        assertEquals(Expired, visit(Expired, login))
        assertEquals(LoggedIn, visit(Expired, login, home))
    }

    @Test
    fun ignoresBlankAndUnknownPages() {
        assertEquals(LoggedIn, visit(LoggedIn, "about:blank", null, "https://evil.com/"))
        assertEquals(LoggedOut, visit(LoggedOut, "about:blank"))
    }
}
