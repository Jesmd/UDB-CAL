package com.example.minimo.data.portal

import com.example.minimo.domain.DecimalInput
import com.example.minimo.domain.Validation
import java.math.BigDecimal
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** A row of the "Módulos inscritos" table. */
data class PortalModule(val code: String, val name: String, val withdrawn: Boolean)

/** What the Notas page shows before any detail is opened. [cycle] is the selected one, e.g. "02 2026". */
data class NotasPage(val cycle: String?, val modules: List<PortalModule>)

/** A row of the "Detalle módulo" table, with the exact grade and weight shown by the portal. */
data class PortalActivity(val name: String, val grade: BigDecimal, val weight: BigDecimal)

data class PortalDetail(val moduleName: String, val activities: List<PortalActivity>)

/**
 * Reads the portal's Notas page. Written only against real captures in `src/test/resources/fixtures`;
 * anything unexpected throws [PortalException.FormatChanged] instead of guessing.
 */
object GradesParser {
    /** Parses the whole Notas page (`document.documentElement.outerHTML`). */
    fun parseNotasPage(html: String): NotasPage {
        val document = Jsoup.parse(html)
        val container = document.getElementById("divNotasFinales")
            ?: throw PortalException.FormatChanged("Missing #divNotasFinales")
        val table = container.selectFirst("table")
            ?: throw PortalException.FormatChanged("Missing modules table")
        val headers = table.select("thead th").map { it.cleanText() }
        val codeColumn = headers.column("Código")
        val nameColumn = headers.column("Asignatura")
        val resultColumn = headers.column("Resultado")

        val modules = table.select("tbody > tr").map { row ->
            val cells = row.select("> td")
            if (cells.size != headers.size) throw PortalException.FormatChanged("Unexpected modules row")
            val code = cells[codeColumn].cleanText()
            if (code.isEmpty()) throw PortalException.FormatChanged("Module without code")
            PortalModule(
                code = code,
                name = cells[nameColumn].cleanText(),
                withdrawn = cells[resultColumn].cleanText().equals("Retirada", ignoreCase = true),
            )
        }
        val cycle = document.selectFirst("select#DDLModulo option[selected]")?.cleanText()?.ifEmpty { null }
        return NotasPage(cycle, modules)
    }

    /**
     * Parses the content of `#divNotasAsignatura` after pressing a module's NF button.
     * Returns `null` when the container is empty (the portal shows no detail).
     */
    fun parseDetail(containerHtml: String): PortalDetail? {
        val body = Jsoup.parseBodyFragment(containerHtml).body()
        if (body.cleanText().isEmpty() && body.selectFirst("table") == null) return null

        val moduleName = body.select("small")
            .firstOrNull { it.cleanText().startsWith("Detalle módulo") }
            ?.nextElementSibling()
            ?.cleanText()
            ?: throw PortalException.FormatChanged("Missing module name in detail")

        // Assumption until a capture shows it: a module without activities has no table rows (or no table).
        val table = body.selectFirst("table") ?: return PortalDetail(moduleName, emptyList())
        val headers = table.select("thead th").map { it.cleanText() }
        val nameColumn = headers.column("Actividad")
        val gradeColumn = headers.column("Calificación")
        val weightColumn = headers.column("Porcentaje")

        val activities = table.select("tbody > tr").map { row ->
            val cells = row.select("> td")
            if (cells.size != headers.size) throw PortalException.FormatChanged("Unexpected activity row")
            val grade = number(cells[gradeColumn].cleanText())
                ?.takeIf { Validation.isValidGrade(it) }
                ?: throw PortalException.FormatChanged("Unexpected grade")
            val weight = number(cells[weightColumn].cleanText().removeSuffix("%").trim())
                ?.takeIf { Validation.isValidWeight(it) }
                ?: throw PortalException.FormatChanged("Unexpected weight")
            PortalActivity(name = cells[nameColumn].cleanText(), grade = grade, weight = weight)
        }
        return PortalDetail(moduleName, activities)
    }

    /** Numbers come as "9.00" or "9,00". */
    private fun number(text: String): BigDecimal? = DecimalInput.parse(text)

    private fun List<String>.column(name: String): Int =
        indexOfFirst { it.equals(name, ignoreCase = true) }
            .takeIf { it >= 0 }
            ?: throw PortalException.FormatChanged("Missing column $name")

    private fun Element.cleanText(): String = text().replace(' ', ' ').trim()
}
