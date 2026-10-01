package com.example.minimo.data.portal

import java.math.BigDecimal
import org.jsoup.Jsoup

/** What the parser understood from a saved portal page. Used by diagnostic mode; it never changes any data. */
sealed interface ParserCheckResult {
    /**
     * @property detail the open detail, if the page had one.
     */
    data class Read(
        val cycle: String?,
        val activeModules: Int,
        val withdrawnModules: Int,
        val detail: DetailSummary?,
    ) : ParserCheckResult

    /** The page does not have the expected structure. [reason] is the parser's technical message. */
    data class Changed(val reason: String) : ParserCheckResult
}

data class DetailSummary(val moduleName: String, val activities: Int, val totalWeight: BigDecimal)

object ParserCheck {
    /** Runs the same parser the sync uses over a saved Notas page ("Guardar HTML de esta página"). */
    fun check(pageHtml: String): ParserCheckResult = try {
        val page = GradesParser.parseNotasPage(pageHtml)
        val container = Jsoup.parse(pageHtml).getElementById("divNotasAsignatura")?.html().orEmpty()
        val detail = GradesParser.parseDetail(container)
        ParserCheckResult.Read(
            cycle = page.cycle,
            activeModules = page.modules.count { !it.withdrawn },
            withdrawnModules = page.modules.count { it.withdrawn },
            detail = detail?.let { d -> DetailSummary(d.moduleName, d.activities.size, d.activities.sumOf { it.weight }) },
        )
    } catch (e: PortalException.FormatChanged) {
        ParserCheckResult.Changed(e.message.orEmpty())
    }
}
