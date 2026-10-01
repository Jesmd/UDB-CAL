package com.example.minimo

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards against format strings that would crash at runtime, e.g. an unescaped `%` ("100%." must be "100%%."). */
class StringsResourceTest {
    private fun strings(): Map<String, String> {
        val file = File("src/main/res/values/strings.xml")
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate { nodes.item(it).attributes.getNamedItem("name").nodeValue to nodes.item(it).textContent }
    }

    @Test
    fun everyStringThatTakesArgumentsFormatsWithoutCrashing() {
        val arguments = Array<Any>(5) { "x" }
        val broken = strings().filter { (_, text) -> text.contains(Regex("%\\d\\$")) }.filter { (_, text) ->
            runCatching { String.format(Locale.ROOT, text, *arguments) }.isFailure
        }
        assertTrue("Strings with invalid format: ${broken.keys}", broken.isEmpty())
    }

    @Test
    fun stringsWithoutArgumentsDoNotUseFormatSpecifiers() {
        // Such strings are read without formatting, so a doubled "%%" would show up literally.
        val doubled = strings().filter { (_, text) -> !text.contains(Regex("%\\d\\$")) && text.contains("%%") }
        assertTrue("Strings with a literal '%%': ${doubled.keys}", doubled.isEmpty())
    }
}
