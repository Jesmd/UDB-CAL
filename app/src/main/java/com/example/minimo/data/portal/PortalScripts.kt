package com.example.minimo.data.portal

/**
 * The only scripts the app runs on the portal, and only on pages behind the login.
 * They read the DOM or press an NF button; they never submit forms or change anything else.
 */
internal object PortalScripts {
    private val MODULE_CODE = Regex("^[A-Za-z0-9_-]+$")

    /** The whole page, to parse in Kotlin. */
    const val PAGE_HTML = "document.documentElement.outerHTML"

    /**
     * `null` while the portal's loading indicator is visible (a request is in flight); otherwise the
     * content of the detail container (empty when there is no detail or the portal cleared it).
     */
    const val DETAIL_STATE = "(function(){" +
        "var l=document.getElementById('loader');" +
        "if(l&&window.getComputedStyle(l).display!=='none'){return null;}" +
        "var c=document.getElementById('divNotasAsignatura');" +
        "return c?c.innerHTML:'';})()"

    /** `null` while the loading indicator is visible; otherwise "ready". */
    const val PAGE_STATE = "(function(){" +
        "var l=document.getElementById('loader');" +
        "return (l&&window.getComputedStyle(l).display!=='none')?null:'ready';})()"

    /** Presses the NF button of the module; returns "ok", or "missing" when there is no such button. */
    fun pressNf(moduleCode: String): String {
        // The code goes inside the script, so only plain codes are accepted.
        if (!MODULE_CODE.matches(moduleCode)) throw PortalException.FormatChanged("Unexpected module code")
        return "(function(){" +
            "var r=document.querySelector('#divNotasFinales tr[data-codigo=\"$moduleCode\"]');" +
            "var b=r&&r.querySelector('button');" +
            "if(!b){return 'missing';}" +
            "b.click();return 'ok';})()"
    }
}
