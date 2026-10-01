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
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = document.getElementsByTagName("string").let { nodes ->
            (0 until nodes.length).associate { nodes.item(it).attributes.getNamedItem("name").nodeValue to nodes.item(it).textContent }
        }
        // Plural items are named after their plural and quantity, e.g. "sync_done/one".
        val plurals = document.getElementsByTagName("item").let { nodes ->
            (0 until nodes.length).associate {
                val item = nodes.item(it)
                val plural = item.parentNode.attributes.getNamedItem("name").nodeValue
                "$plural/${item.attributes.getNamedItem("quantity").nodeValue}" to item.textContent
            }
        }
        return strings + plurals
    }

    @Test
    fun everyStringThatTakesArgumentsFormatsWithoutCrashing() {
        val broken = strings().filter { (_, text) -> text.contains(Regex("%\\d\\$")) }.filter { (_, text) ->
            runCatching { String.format(Locale.ROOT, text, *argumentsFor(text)) }.isFailure
        }
        assertTrue("Strings with invalid format: ${broken.keys}", broken.isEmpty())
    }

    /** One argument per `%n$` specifier, of the type the specifier expects (as the app passes them). */
    private fun argumentsFor(text: String): Array<Any> {
        val types = Regex("%(\\d)\\$([a-zA-Z])").findAll(text).associate { it.groupValues[1].toInt() to it.groupValues[2] }
        val count = types.keys.maxOrNull() ?: 0
        return Array(count) { index -> if (types[index + 1] == "d") 1 else "x" }
    }

    @Test
    fun stringsWithoutArgumentsDoNotUseFormatSpecifiers() {
        // Such strings are read without formatting, so a doubled "%%" would show up literally.
        val doubled = strings().filter { (_, text) -> !text.contains(Regex("%\\d\\$")) && text.contains("%%") }
        assertTrue("Strings with a literal '%%': ${doubled.keys}", doubled.isEmpty())
    }
}
