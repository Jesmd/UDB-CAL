package com.example.minimo.data.portal

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalAcademicDataSourceTest {
    /**
     * Plays the portal from the real captures. ESA501 and IRD101 have no capture of their own, so their detail
     * is the real DMD104 detail with the module name changed: only the orchestration is tested with them.
     */
    private class FakeBrowser(
        val finalUrl: String? = PortalUrls.NOTAS_URL,
        /** Where later loads end (e.g. the login page once the session expired). */
        val laterUrl: String? = finalUrl,
        val page: String = Fixtures.notasSinDetalle,
        val details: Map<String, String> = mapOf(
            "DMD104" to Fixtures.detalleDmd104,
            "ESA501" to Fixtures.detalleDmd104.replace("Datawarehouse y Minería de Datos", "Estadística Aplicada"),
            "IRD101" to Fixtures.detalleDmd104.replace("Datawarehouse y Minería de Datos", "Interconexión de Redes de Datos"),
        ),
        /** How many times the loading indicator is still visible after pressing NF. */
        val busyPolls: Int = 2,
    ) : PortalBrowser {
        val loads = mutableListOf<String>()
        val pressed = mutableListOf<String>()
        var scriptsRun = 0
        private var detail = ""
        private var busyLeft = 0

        override suspend fun load(url: String): String? {
            loads += url
            return if (loads.size == 1) finalUrl else laterUrl
        }

        override suspend fun evaluate(script: String): String {
            scriptsRun++
            return when (script) {
                PortalScripts.PAGE_HTML -> json(page)
                PortalScripts.PAGE_STATE -> json("ready")
                PortalScripts.DETAIL_STATE -> if (busyLeft > 0) {
                    busyLeft--
                    "null"
                } else {
                    json(detail)
                }
                else -> {
                    val code = listOf("CVV501", "DMD104", "ESA501", "IRD101").first { PortalScripts.pressNf(it) == script }
                    pressed += code
                    detail = details[code] ?: return json("missing")
                    busyLeft = busyPolls
                    json("ok")
                }
            }
        }

        private fun json(text: String) = JsonPrimitive(text).toString()
    }

    @Test
    fun readsEveryActiveModuleOnceAndSkipsWithdrawnOnes() = runTest {
        val browser = FakeBrowser()
        val progress = mutableListOf<Pair<Int, Int>>()

        val snapshot = PortalAcademicDataSource(browser).fetchCourses { done, total -> progress += done to total }

        assertEquals("02 2026", snapshot.cycle)
        assertEquals(listOf("DMD104", "ESA501", "IRD101"), snapshot.courses.map { it.module.code })
        assertEquals(listOf("DMD104", "ESA501", "IRD101"), browser.pressed)
        assertEquals(listOf(PortalUrls.NOTAS_URL), browser.loads)
        assertEquals(9, snapshot.courses.first().activities.size)
        assertEquals(listOf(0 to 3, 1 to 3, 2 to 3, 3 to 3), progress)
    }

    @Test
    fun aRedirectToTheLoginMeansNotLoggedInAndNoScriptRuns() = runTest {
        val browser = FakeBrowser(finalUrl = PortalUrls.LOGIN_URL)
        val error = runCatching { PortalAcademicDataSource(browser).fetchCourses() }.exceptionOrNull()
        assertTrue(error is PortalException.NotLoggedIn)
        assertEquals(0, browser.scriptsRun)
    }

    @Test
    fun aDetailThatNeverFinishesTimesOut() = runTest {
        val browser = FakeBrowser(busyPolls = Int.MAX_VALUE)
        val error = runCatching { PortalAcademicDataSource(browser, detailTimeoutMillis = 5_000).fetchCourses() }.exceptionOrNull()
        assertEquals("DMD104", (error as PortalException.Timeout).what)
    }

    @Test
    fun aClearedDetailIsAFailureForThatModule() = runTest {
        val browser = FakeBrowser(details = mapOf("DMD104" to ""))
        val error = runCatching { PortalAcademicDataSource(browser).fetchCourses() }.exceptionOrNull()
        assertEquals("DMD104", (error as PortalException.DetailFailed).moduleCode)
    }

    @Test
    fun aDetailThatFailsBecauseTheSessionExpiredAsksToLogIn() = runTest {
        val browser = FakeBrowser(details = mapOf("DMD104" to ""), laterUrl = PortalUrls.LOGIN_URL)
        val error = runCatching { PortalAcademicDataSource(browser).fetchCourses() }.exceptionOrNull()
        assertTrue(error is PortalException.NotLoggedIn)
        assertEquals(listOf(PortalUrls.NOTAS_URL, PortalUrls.NOTAS_URL), browser.loads)
    }

    @Test
    fun aTimeoutBecauseTheSessionExpiredAsksToLogIn() = runTest {
        val browser = FakeBrowser(busyPolls = Int.MAX_VALUE, laterUrl = PortalUrls.LOGIN_URL)
        val error = runCatching { PortalAcademicDataSource(browser, detailTimeoutMillis = 5_000).fetchCourses() }.exceptionOrNull()
        assertTrue(error is PortalException.NotLoggedIn)
    }

    @Test
    fun aDetailOfAnotherModuleIsAFailure() = runTest {
        val browser = FakeBrowser(details = mapOf("DMD104" to Fixtures.detalleDmd104.replace("Datawarehouse", "Otra")))
        val error = runCatching { PortalAcademicDataSource(browser).fetchCourses() }.exceptionOrNull()
        assertTrue(error is PortalException.DetailFailed)
    }

    @Test
    fun aMissingNfButtonOrChangedPageIsReported() = runTest {
        val noButton = FakeBrowser(details = emptyMap())
        assertTrue(runCatching { PortalAcademicDataSource(noButton).fetchCourses() }.exceptionOrNull() is PortalException.FormatChanged)

        val changed = FakeBrowser(page = "<html><body>Mantenimiento</body></html>")
        assertTrue(runCatching { PortalAcademicDataSource(changed).fetchCourses() }.exceptionOrNull() is PortalException.FormatChanged)
    }

    @Test
    fun onlyPlainModuleCodesAreAcceptedInScripts() {
        assertThrows(PortalException.FormatChanged::class.java) { PortalScripts.pressNf("A\"]');alert(1)//") }
    }
}
