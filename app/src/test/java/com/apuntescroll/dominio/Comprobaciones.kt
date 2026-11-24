package com.apuntescroll.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.text.Normalizer

/** Comprobaciones que tienen que cumplirse para cualquier documento armado. */
object Comprobaciones {

    private val PALABRA = Regex("[\\p{L}\\p{N}]+")

    fun palabras(texto: String): List<String> = PALABRA.findAll(texto.lowercase()).map { it.value }.toList()

    /** Las palabras de los títulos y las tarjetas, en el orden del documento. */
    fun palabrasDelDocumento(doc: DocumentoArmado): List<String> =
        doc.secciones.flatMap { s -> palabras(s.titulo ?: "") + s.tarjetas.flatMap { palabras(it.texto) } }

    /**
     * Las palabras del cuerpo de [paginas]: sin las líneas que [descartar] dice (encabezados, pies,
     * números de página), con las palabras cortadas por guion ya unidas y sin ligaduras.
     */
    fun palabrasEsperadas(paginas: List<PaginaExtraida>, descartar: (String) -> Boolean = { false }): List<String> {
        val texto = paginas.sortedBy { it.numero }.joinToString("\n") { p ->
            p.texto.replace("\r\n", "\n").lines().filterNot { descartar(it.trim()) }.joinToString("\n")
        }
        // NFKC solo para las ligaduras: el resto (por ejemplo "¹") la app lo deja como viene.
        val sinLigaduras = Regex("[\\uFB00-\\uFB06]").replace(Normalizer.normalize(texto, Normalizer.Form.NFC)) {
            Normalizer.normalize(it.value, Normalizer.Form.NFKC)
        }
        val sinInvisibles = sinLigaduras.replace(Regex("\\u00AD[ \\t]*\\n"), "-\n").replace(Regex("[\\u00AD\\u200B-\\u200D\\u2060\\uFEFF]"), "")
        val unido = sinInvisibles.replace(Regex("(\\p{L})-\\s*\\n\\s*(\\p{Ll})"), "$1$2")
        return palabras(unido)
    }

    /** Ninguna palabra del cuerpo se pierde ni cambia de orden. */
    fun assertConservaPalabras(esperadas: List<String>, doc: DocumentoArmado) {
        val obtenidas = palabrasDelDocumento(doc)
        if (esperadas != obtenidas) {
            val i = esperadas.indices.firstOrNull { it >= obtenidas.size || esperadas[it] != obtenidas[it] } ?: esperadas.size
            val desde = maxOf(0, i - 5)
            throw AssertionError(
                "Las palabras difieren en la posición $i.\n" +
                    "Esperadas: ${esperadas.subList(desde, minOf(esperadas.size, i + 6))}\n" +
                    "Obtenidas: ${obtenidas.subList(minOf(desde, obtenidas.size), minOf(obtenidas.size, i + 6))}",
            )
        }
    }

    /** Las reglas de forma de todas las tarjetas: no vacías, recortadas, sin espacios dobles y dentro del largo. */
    fun assertTarjetasBienFormadas(doc: DocumentoArmado, max: Int) {
        val limite = (maxOf(max, ArmadorTarjetas.MAX_MINIMO) * Cortador.TOLERANCIA_FUSION).toInt()
        for (seccion in doc.secciones) {
            assertTrue("Sección vacía: ${seccion.titulo}", seccion.tarjetas.isNotEmpty())
            seccion.titulo?.let { titulo ->
                assertEquals(titulo.trim(), titulo)
                assertTrue("Título vacío", titulo.isNotBlank())
            }
            for (t in seccion.tarjetas) {
                assertTrue("Tarjeta vacía", t.texto.isNotBlank())
                assertEquals("Tarjeta sin recortar: «${t.texto}»", t.texto.trim(), t.texto)
                assertFalse("Espacios dobles: «${t.texto}»", t.texto.contains("  "))
                assertFalse("Líneas vacías: «${t.texto}»", t.texto.contains("\n\n"))
                for (linea in t.texto.lines()) assertEquals("Línea sin recortar: «$linea»", linea.trim(), linea)
                assertTrue("Tarjeta de ${t.texto.length} caracteres (límite $limite): «${t.texto}»", t.texto.length <= limite)
                assertTrue("Página imposible: ${t.pagina}", t.pagina >= seccion.paginaInicio || doc.secciones.size == 1)
            }
        }
    }

    fun todasLasTarjetas(doc: DocumentoArmado): List<TarjetaArmada> = doc.secciones.flatMap { it.tarjetas }

    /** Texto legible del documento, para los mensajes de error y para mirar resultados. */
    fun describir(doc: DocumentoArmado): String = buildString {
        for (s in doc.secciones) {
            appendLine("## ${s.titulo ?: "(sin título)"} [p. ${s.paginaInicio}]")
            for (t in s.tarjetas) appendLine("  [p. ${t.pagina}, ${t.texto.length}] ${t.texto.replace("\n", " ⏎ ")}")
        }
    }
}
