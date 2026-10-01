package com.example.minimo.data.portal

/** Prepares a saved copy of a portal page. */
object HtmlSnapshot {
    private val INPUT_TAG = Regex("""<input\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val VALUE_ATTRIBUTE =
        Regex("""(\bvalue\s*=\s*)("[^"]*"|'[^']*'|[^\s>"']+)""", RegexOption.IGNORE_CASE)
    private const val TOKEN_FIELD = "__RequestVerificationToken"
    const val REDACTED = "REDACTED"

    /**
     * Blanks the value of the anti-forgery token field (ASP.NET `__RequestVerificationToken`), so the token
     * is never written to a file. The rest of the page is left untouched.
     */
    fun redactAntiForgeryToken(html: String): String =
        INPUT_TAG.replace(html) { tag ->
            if (tag.value.contains(TOKEN_FIELD)) {
                VALUE_ATTRIBUTE.replace(tag.value) { match -> "${match.groupValues[1]}\"$REDACTED\"" }
            } else {
                tag.value
            }
        }
}
