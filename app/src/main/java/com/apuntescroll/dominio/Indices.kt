package com.apuntescroll.dominio

import java.text.Normalizer

/**
 * Quita los índices (tablas de contenido): si no, cada renglón del índice se vuelve una sección
 * falsa con una o dos tarjetas. Trabaja sobre las líneas de cada página, ya sin encabezados ni pies.
 *
 * - El índice empieza en una línea "Índice", "Índice general", "Contenido(s)", "Tabla de
 *   contenido(s)" o "Sumario general" que es la primera de la página o está sola en su bloque.
 * - Termina donde vuelve a aparecer su primera entrada como línea del texto ("Capítulo 1" en el
 *   índice y otra vez donde empieza el capítulo), aunque sea en la misma página del PDF.
 * - Si no reaparece, sigue en las páginas siguientes mientras parezcan índice: la mayoría de los
 *   renglones (al menos 5 y el 60 %, o al menos 3 y el 80 %) terminan en un número de página que
 *   no baja, o (páginas largas, como las de un
 *   escaneo) casi todos los renglones son títulos, renglones cortos sin punto o enumeraciones
 *   corridas ("8. Agentes. 8.1. Factor."). Como mucho, max(3, páginas / 10) páginas más.
 *
 * Las líneas del índice quedan vacías, así que no generan secciones ni tarjetas.
 */
internal object Indices {

    private val TITULO_DE_INDICE = Regex("^(?:indice(?: general| tematico)?|contenidos?|tabla de contenidos?|sumario general)$")
    private val ENTRADA_CON_PAGINA = Regex("^(.*\\p{L}.*?)(?:\\s*[.·…_]{2,}\\s*|\\s+)(\\d{1,3})$")
    private val ENUMERACION_EN_LINEA = Regex("(?:^|\\s)\\d{1,2}(?:\\.\\d{1,2})*\\.\\s+\\p{Lu}")
    private val MARCAS = Regex("\\p{Mn}+")
    private val NO_ALFANUMERICO = Regex("[^\\p{L}\\d]+")
    private const val MIN_ENTRADAS = 5
    private const val MIN_LINEAS_ESCANEO = 15

    fun quitar(paginas: List<List<String>>): List<List<String>> {
        val resultado = paginas.map { it.toMutableList() }
        var p = 0
        while (p < resultado.size) {
            val inicio = inicioDeIndice(resultado[p])
            p = if (inicio == null) p + 1 else quitarDesde(resultado, p, inicio) + 1
        }
        return resultado
    }

    private fun sinAcentos(texto: String) = Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD).replace(MARCAS, "")

    /** La línea que abre un índice: es la primera de la página o está sola entre líneas vacías. */
    private fun inicioDeIndice(lineas: List<String>): Int? {
        val primera = lineas.indexOfFirst { it.isNotEmpty() }
        return lineas.indices.firstOrNull { j ->
            val sola = (j == 0 || lineas[j - 1].isEmpty()) && (j == lineas.lastIndex || lineas[j + 1].isEmpty())
            (j == primera || sola) && TITULO_DE_INDICE.matches(sinAcentos(lineas[j]).trim().replace(Regex("\\s+"), " "))
        }
    }

    /** Clave para reconocer una entrada fuera del índice: sin la página, sin acentos ni signos ni espacios. */
    private fun clave(linea: String): String {
        val texto = ENTRADA_CON_PAGINA.matchEntire(linea)?.groupValues?.get(1) ?: linea
        return sinAcentos(texto).replace(NO_ALFANUMERICO, "")
    }

    /** Vacía el índice que empieza en la línea [inicio] de la página [p0]; devuelve la última página tocada. */
    private fun quitarDesde(paginas: List<MutableList<String>>, p0: Int, inicio: Int): Int {
        val lineas0 = paginas[p0]
        val indicePrimera = (inicio + 1 until lineas0.size).firstOrNull { lineas0[it].count(Char::isLetter) >= 3 }
        val primera = indicePrimera?.let { clave(lineas0[it]) }?.takeIf { it.length >= 3 }
        fun reinicio(lineas: List<String>, desde: Int): Int? = primera?.let { buscada ->
            (desde until lineas.size).firstOrNull { k ->
                lineas[k].isNotEmpty() && ENTRADA_CON_PAGINA.matchEntire(lineas[k]) == null && clave(lineas[k]) == buscada
            }
        }
        val enLaMisma = indicePrimera?.let { reinicio(lineas0, it + 1) }
        vaciar(lineas0, inicio, enLaMisma ?: lineas0.size)
        if (enLaMisma != null) return p0
        val tope = minOf(paginas.lastIndex, p0 + maxOf(3, paginas.size / 10))
        for (p in p0 + 1..tope) {
            val r = reinicio(paginas[p], 0)
            if (r != null) {
                vaciar(paginas[p], 0, r)
                return p
            }
            if (!pareceIndice(paginas[p])) return p - 1
            vaciar(paginas[p], 0, paginas[p].size)
        }
        return tope
    }

    private fun vaciar(lineas: MutableList<String>, desde: Int, hasta: Int) {
        for (k in desde until hasta) lineas[k] = ""
    }

    /** true si la página sigue un índice (ver la descripción del objeto). */
    private fun pareceIndice(lineas: List<String>): Boolean {
        val noVacias = lineas.filter { it.isNotEmpty() }
        if (noVacias.isEmpty()) return false
        val numeros = noVacias.mapNotNull { ENTRADA_CON_PAGINA.matchEntire(it)?.groupValues?.get(2)?.toInt() }
        // Muchas entradas, o pocas (el final del índice) pero casi todas las líneas.
        val bastantes = numeros.size >= MIN_ENTRADAS && numeros.size * 10 >= noVacias.size * 6
        val casiTodas = numeros.size >= 3 && numeros.size * 10 >= noVacias.size * 8
        if (bastantes || casiTodas) {
            val sinBajar = numeros.zipWithNext().count { (a, b) -> b >= a }
            if (sinBajar * 10 >= (numeros.size - 1) * 8) return true
        }
        if (noVacias.size < MIN_LINEAS_ESCANEO) return false
        val entradas = noVacias.count { l ->
            DetectorTitulos.pareceTitulo(l) || (l.length <= 70 && !Renglones.terminaFrase(l)) || ENUMERACION_EN_LINEA.containsMatchIn(l)
        }
        return entradas * 10 >= noVacias.size * 7
    }
}
