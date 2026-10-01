package com.example.minimo.data.portal

import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads courses through the authenticated in-app browser: loads Notas, presses each NF button and waits
 * for the portal's own request to finish (its loading indicator hides), then reads the DOM.
 * One page load plus one detail per course; withdrawn courses are skipped.
 */
class PortalAcademicDataSource(
    private val browser: PortalBrowser,
    private val pollIntervalMillis: Long = 300,
    private val detailTimeoutMillis: Long = 30_000,
) : AcademicDataSource {

    override suspend fun fetchCourses(onProgress: (done: Int, total: Int) -> Unit): PortalSnapshot {
        val url = browser.load(PortalUrls.NOTAS_URL)
        if (!PortalUrls.isPortalPage(url)) throw PortalException.NotLoggedIn()

        waitUntil("Notas") { readString(PortalScripts.PAGE_STATE) }
        val page = GradesParser.parseNotasPage(
            readString(PortalScripts.PAGE_HTML) ?: throw PortalException.FormatChanged("Empty page"),
        )
        val modules = page.modules.filterNot { it.withdrawn }
        val courses = modules.mapIndexed { index, module ->
            onProgress(index, modules.size)
            PortalCourseData(module, readDetail(module).activities)
        }
        onProgress(modules.size, modules.size)
        return PortalSnapshot(page.cycle, courses)
    }

    private suspend fun readDetail(module: PortalModule): PortalDetail {
        if (readString(PortalScripts.pressNf(module.code)) != "ok") {
            throw PortalException.FormatChanged("Missing NF button")
        }
        val html = waitUntil(module.code) { readString(PortalScripts.DETAIL_STATE) }
        val detail = GradesParser.parseDetail(html) ?: throw PortalException.DetailFailed(module.code)
        if (!normalize(detail.moduleName).equals(normalize(module.name), ignoreCase = true)) throw PortalException.DetailFailed(module.code)
        return detail
    }

    /** Polls [read] until it returns a value, or fails with [PortalException.Timeout]. */
    private suspend fun waitUntil(what: String, read: suspend () -> String?): String {
        var waited = 0L
        while (true) {
            read()?.let { return it }
            if (waited >= detailTimeoutMillis) throw PortalException.Timeout(what)
            delay(pollIntervalMillis)
            waited += pollIntervalMillis
        }
    }

    /** Decodes the JSON value returned by the browser; `null` for JSON null or non-strings. */
    private suspend fun readString(script: String): String? {
        val raw = browser.evaluate(script)
        val element = runCatching { Json.parseToJsonElement(raw) }.getOrNull() ?: return null
        if (element is JsonNull) return null
        return (element as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    private fun normalize(text: String) = text.replace(Regex("\\s+"), " ").trim()
}
