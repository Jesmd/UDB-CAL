package com.example.minimo.data.portal

import org.jsoup.Jsoup

/** Real, anonymized captures of the portal (see src/test/resources/fixtures). */
object Fixtures {
    /** Notas page before pressing any NF button. */
    val notasSinDetalle: String by lazy { read("notas_sin_detalle.html") }

    /** Notas page after pressing the NF button of DMD104. */
    val notasDetalleDmd104: String by lazy { read("notas_detalle_dmd104.html") }

    /** What the detail container holds after pressing DMD104's NF button (its innerHTML). */
    val detalleDmd104: String by lazy { detailContainer(notasDetalleDmd104) }

    fun detailContainer(pageHtml: String): String =
        checkNotNull(Jsoup.parse(pageHtml).getElementById("divNotasAsignatura")).html()

    private fun read(name: String): String =
        checkNotNull(Fixtures::class.java.getResource("/fixtures/$name")) { "Missing fixture $name" }.readText()
}
