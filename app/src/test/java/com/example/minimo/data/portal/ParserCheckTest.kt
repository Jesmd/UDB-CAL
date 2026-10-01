package com.example.minimo.data.portal

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserCheckTest {
    @Test
    fun summarizesAPageWithItsOpenDetail() {
        val result = ParserCheck.check(Fixtures.notasDetalleDmd104) as ParserCheckResult.Read
        assertEquals("02 2026", result.cycle)
        assertEquals(3, result.activeModules)
        assertEquals(1, result.withdrawnModules)
        assertEquals(DetailSummary("Datawarehouse y Minería de Datos", 9, BigDecimal("100.00")), result.detail)
    }

    @Test
    fun aPageWithoutDetailHasNoDetailSummary() {
        val result = ParserCheck.check(Fixtures.notasSinDetalle) as ParserCheckResult.Read
        assertEquals(null, result.detail)
    }

    @Test
    fun anAlteredCaptureIsReportedAsChanged() {
        val altered = Fixtures.notasDetalleDmd104.replace("<th>Asignatura</th>", "<th>Materia</th>")
        assertTrue(ParserCheck.check(altered) is ParserCheckResult.Changed)
        assertTrue(ParserCheck.check("<html><body>Página de inicio</body></html>") is ParserCheckResult.Changed)
    }
}
