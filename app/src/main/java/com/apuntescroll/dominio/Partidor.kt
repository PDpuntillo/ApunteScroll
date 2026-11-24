package com.apuntescroll.dominio

/**
 * Parte una oración más larga que el máximo de una tarjeta (PLAN.md, sección 3.3).
 *
 * Se busca un corte en este orden de preferencia: después de `;`, después de `,` o `:`, antes de
 * una conjunción (" y ", " o ", " que ", " pero "...) y, por último, en un espacio. Nunca se
 * corta dentro de una palabra, salvo que la palabra sola sea más larga que el máximo.
 *
 * Para que las partes queden parejas, se calcula cuántas hacen falta y se apunta a ese largo: de
 * cada tipo de corte se elige el más cercano al objetivo, entre la mitad del objetivo y el
 * máximo. Si un tipo de corte no aparece en esa ventana, se pasa al siguiente.
 */
internal object Partidor {

    private val CONJUNCIONES = listOf("y", "e", "o", "u", "ni", "que", "pero", "porque", "aunque", "sino")

    /** Las partes de [texto], como posiciones dentro de él; ninguna supera [max] caracteres. */
    fun partir(texto: String, max: Int): List<IntRange> {
        val piezas = mutableListOf<IntRange>()
        var inicio = saltarEspacios(texto, 0)
        var fin = texto.length
        while (fin > inicio && texto[fin - 1].isWhitespace()) fin--
        while (fin - inicio > max) {
            val restante = fin - inicio
            val partes = (restante + max - 1) / max
            val objetivo = restante / partes
            val corte = elegirCorte(texto, inicio, max, objetivo)
            var finPieza = corte
            while (finPieza > inicio && texto[finPieza - 1].isWhitespace()) finPieza--
            piezas.add(inicio until finPieza)
            inicio = saltarEspacios(texto, corte)
        }
        if (inicio < fin) piezas.add(inicio until fin)
        return piezas
    }

    private fun saltarEspacios(texto: String, desde: Int): Int {
        var i = desde
        while (i < texto.length && texto[i].isWhitespace()) i++
        return i
    }

    /** Dónde termina la próxima pieza (posición exclusiva) si empieza en [inicio]. */
    private fun elegirCorte(texto: String, inicio: Int, max: Int, objetivo: Int): Int {
        val limite = inicio + max
        val desde = inicio + maxOf(1, objetivo / 2)
        val meta = inicio + objetivo
        val tipos: List<(Int) -> Boolean> = listOf(
            { p -> despuesDe(texto, p, ";") },
            { p -> despuesDe(texto, p, ",:") },
            { p -> antesDeConjuncion(texto, p) },
            { p -> texto[p] == ' ' },
        )
        for (esCorte in tipos) {
            var mejor = -1
            for (p in desde..minOf(limite, texto.length - 1)) {
                if (esCorte(p) && (mejor < 0 || kotlin.math.abs(p - meta) <= kotlin.math.abs(mejor - meta))) mejor = p
            }
            if (mejor >= 0) return mejor
        }
        // Una palabra muy larga ocupa casi todo: se usa el último espacio que haya.
        for (p in minOf(limite, texto.length - 1) downTo inicio + 1) if (texto[p] == ' ') return p
        // Una sola palabra más larga que el máximo: se corta a la fuerza, sin partir un par sustituto.
        return if (texto[limite - 1].isHighSurrogate()) limite - 1 else limite
    }

    /** true si en [p] hay un espacio y justo antes uno de los [signos]. La pieza termina en [p]. */
    private fun despuesDe(texto: String, p: Int, signos: String) = p > 0 && texto[p] == ' ' && texto[p - 1] in signos

    /** true si en [p] hay un espacio seguido de una conjunción y otro espacio. */
    private fun antesDeConjuncion(texto: String, p: Int): Boolean {
        if (texto[p] != ' ') return false
        return CONJUNCIONES.any { c ->
            val fin = p + 1 + c.length
            fin < texto.length && texto.regionMatches(p + 1, c, 0, c.length, ignoreCase = true) && texto[fin] == ' '
        }
    }
}
