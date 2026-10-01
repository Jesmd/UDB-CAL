package com.example.minimo.data.portal

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GradesParserTest {
    private val expectedModules = listOf(
        PortalModule("CVV501", "Cálculo de Varias Variables", withdrawn = true),
        PortalModule("DMD104", "Datawarehouse y Minería de Datos", withdrawn = false),
        PortalModule("ESA501", "Estadística Aplicada", withdrawn = false),
        PortalModule("IRD101", "Interconexión de Redes de Datos", withdrawn = false),
    )

    @Test
    fun readsModulesAndCycleFromTheNotasPage() {
        val page = GradesParser.parseNotasPage(Fixtures.notasSinDetalle)
        assertEquals("02 2026", page.cycle)
        assertEquals(expectedModules, page.modules)
    }

    @Test
    fun theModulesTableIsTheSameWithTheDetailOpen() {
        assertEquals(expectedModules, GradesParser.parseNotasPage(Fixtures.notasDetalleDmd104).modules)
    }

    @Test
    fun readsTheDetailExactly() {
        val detail = checkNotNull(GradesParser.parseDetail(Fixtures.detalleDmd104))
        assertEquals("Datawarehouse y Minería de Datos", detail.moduleName)
        val expected = listOf(
            Triple("Investigación documental 1", "10.00", "9.00"),
            Triple("Desafío práctico 1", "9.40", "10.00"),
            Triple("Evaluación de proyecto primera fase", "9.75", "7.00"),
            Triple("Investigación documental 2", "10.00", "10.00"),
            Triple("Guía de ejercicios prácticos", "0.00", "8.00"),
            Triple("Desafío Practico 2", "10.00", "13.00"),
            Triple("Defensa de proyecto fase 2", "10.00", "15.00"),
            Triple("Desafío práctico 3", "0.00", "13.00"),
            Triple("Defensa final de proyecto", "0.00", "15.00"),
        ).map { (name, grade, weight) -> PortalActivity(name, BigDecimal(grade), BigDecimal(weight)) }
        assertEquals(expected, detail.activities)
        assertEquals(0, BigDecimal("100").compareTo(detail.activities.sumOf { it.weight }))
    }

    @Test
    fun anEmptyDetailContainerMeansNoDetail() {
        assertNull(GradesParser.parseDetail(Fixtures.detailContainer(Fixtures.notasSinDetalle)))
        assertNull(GradesParser.parseDetail("  \n "))
    }

    @Test
    fun acceptsCommaDecimals() {
        val detail = checkNotNull(GradesParser.parseDetail(Fixtures.detalleDmd104.replace("9.00 %", "9,00 %")))
        assertEquals(0, BigDecimal("9").compareTo(detail.activities.first().weight))
    }

    @Test
    fun aDetailWithoutRowsHasNoActivities() {
        // Assumption until a real capture shows a module without activities.
        val withoutRows = Fixtures.detalleDmd104.replace(Regex("(?s)<tbody>.*</tbody>"), "<tbody></tbody>")
        val detail = checkNotNull(GradesParser.parseDetail(withoutRows))
        assertTrue(detail.activities.isEmpty())
    }

    @Test
    fun aChangedPageIsReportedInsteadOfGuessed() {
        listOf(
            Fixtures.notasSinDetalle.replace("id=\"divNotasFinales\"", "id=\"otro\""),
            Fixtures.notasSinDetalle.replace("<th>Asignatura</th>", "<th>Materia</th>"),
            Fixtures.notasSinDetalle.replace("<th>Resultado</th>", ""),
        ).forEach { html ->
            assertThrows(PortalException.FormatChanged::class.java) { GradesParser.parseNotasPage(html) }
        }
    }

    @Test
    fun aChangedDetailIsReportedInsteadOfGuessed() {
        listOf(
            Fixtures.detalleDmd104.replace("<th width=\"12%\">Calificación</th>", "<th width=\"12%\">Nota</th>"),
            Fixtures.detalleDmd104.replace(">9.40<", ">9.4x<"),
            Fixtures.detalleDmd104.replace(">9.40<", ">94.0<"),
            Fixtures.detalleDmd104.replace("9.00 %", "109.00 %"),
            Fixtures.detalleDmd104.replace("Detalle módulo:", "Otro título:"),
        ).forEach { html ->
            assertThrows(PortalException.FormatChanged::class.java) { GradesParser.parseDetail(html) }
        }
    }
}
