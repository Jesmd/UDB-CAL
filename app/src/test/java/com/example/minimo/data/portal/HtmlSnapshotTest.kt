package com.example.minimo.data.portal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlSnapshotTest {
    @Test
    fun blanksTheTokenValueWhateverTheAttributeOrder() {
        val html = """
            <form>
            <input name="__RequestVerificationToken" type="hidden" value="SECRET-ONE" />
            <input type="hidden" value='SECRET-TWO' name='__RequestVerificationToken'>
            <INPUT NAME=__RequestVerificationToken VALUE=SECRET-THREE>
            </form>
        """.trimIndent()
        val result = HtmlSnapshot.redactAntiForgeryToken(html)
        assertFalse(result.contains("SECRET"))
        assertEquals(3, Regex("value=\"REDACTED\"", RegexOption.IGNORE_CASE).findAll(result).count())
    }

    @Test
    fun leavesEverythingElseUntouched() {
        val html = """<input name="Codigo" value="MAT101"><td>9.00 %</td><input type="hidden" name="__RequestVerificationToken" value="x">"""
        val result = HtmlSnapshot.redactAntiForgeryToken(html)
        assertTrue(result.startsWith("""<input name="Codigo" value="MAT101"><td>9.00 %</td>"""))
        assertTrue(result.contains("""value="REDACTED""""))
    }

    @Test
    fun pagesWithoutTheTokenAreUnchanged() {
        val html = "<html><body><input name=\"q\" value=\"hola\"></body></html>"
        assertEquals(html, HtmlSnapshot.redactAntiForgeryToken(html))
    }
}
