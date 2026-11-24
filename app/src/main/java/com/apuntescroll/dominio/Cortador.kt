package com.apuntescroll.dominio

/**
 * Corta los bloques de una sección en tarjetas (PLAN.md, sección 3.3).
 *
 * - Se llenan las tarjetas con oraciones enteras, en orden, mientras quepan en el máximo.
 * - Un párrafo nuevo empieza tarjeta nueva si la actual ya está llena al menos en un 60 %.
 * - Una oración más larga que el máximo se parte con [Partidor].
 * - Una lista entra entera en una tarjeta si cabe, junto con la oración que la presenta si esa
 *   oración termina en ":" (si hace falta, la oración se pasa a la tarjeta de la lista). Si la
 *   lista no cabe en una tarjeta, se reparte entre ítems; un ítem más largo que el máximo se parte
 *   entre sus oraciones y, si una oración sola no entra, con [Partidor].
 * - Al final, cada tarjeta de menos de [MIN_FRAGMENTO] caracteres se une con la anterior de la
 *   sección o, si no se puede, con la siguiente, siempre que el resultado no pase del máximo por
 *   [TOLERANCIA_FUSION]. Si no se puede con ninguna, queda sola.
 *
 * El texto de cada tarjeta va con un espacio entre oraciones y un salto de línea entre ítems de
 * lista (y entre una lista o una línea suelta y el texto de al lado). La página de la tarjeta es
 * la de su primer carácter.
 */
internal object Cortador {

    const val MIN_FRAGMENTO = 80
    const val TOLERANCIA_FUSION = 1.15
    const val LLENADO_PARA_CORTAR = 0.6

    /**
     * Un pedazo de texto que no se parte más: una oración, parte de una oración larga o un ítem.
     * [continua] marca la segunda parte (o siguientes) de un ítem partido, que no lleva salto de
     * línea antes.
     */
    private class Pieza(val texto: String, val pagina: Int, val esItem: Boolean, val continua: Boolean = false)

    private fun separador(a: Pieza, b: Pieza): String = when {
        b.continua -> " "
        a.esItem || b.esItem -> "\n"
        else -> " "
    }

    private fun largo(piezas: List<Pieza>): Int {
        if (piezas.isEmpty()) return 0
        var total = piezas[0].texto.length
        for (i in 1 until piezas.size) total += separador(piezas[i - 1], piezas[i]).length + piezas[i].texto.length
        return total
    }

    fun cortar(bloques: List<Bloque>, max: Int): List<TarjetaArmada> {
        val tarjetas = mutableListOf<MutableList<Pieza>>()
        var actual = mutableListOf<Pieza>()
        fun cerrar() {
            if (actual.isNotEmpty()) {
                tarjetas.add(actual)
                actual = mutableListOf()
            }
        }
        fun cabe(extra: List<Pieza>) = largo(actual + extra) <= max
        fun agregarUnaPorUna(piezas: List<Pieza>) {
            for (p in piezas) {
                if (actual.isNotEmpty() && !cabe(listOf(p))) cerrar()
                actual.add(p)
            }
        }
        val umbral = max * LLENADO_PARA_CORTAR

        for (bloque in bloques) {
            when (bloque) {
                is Bloque.Titulo -> Unit
                is Bloque.Parrafo -> {
                    if (largo(actual) >= umbral) cerrar()
                    // Una línea suelta va en su propio renglón, como un ítem.
                    val piezas = if (bloque.esLinea) piezasDeItem(bloque.contenido, max) else piezasDeParrafo(bloque.contenido, max)
                    agregarUnaPorUna(piezas)
                }
                is Bloque.Lista -> {
                    val piezas = bloque.items.flatMap { piezasDeItem(it, max) }
                    val largoLista = largo(piezas)
                    // La oración que presenta la lista: "... son los siguientes:".
                    val intro = actual.lastOrNull()?.takeIf { !it.esItem && it.texto.endsWith(':') }
                    when {
                        actual.isEmpty() -> agregarUnaPorUna(piezas)
                        cabe(piezas) && (intro != null || largo(actual) < umbral) -> actual.addAll(piezas)
                        intro != null && actual.size > 1 && largo(listOf(intro) + piezas) <= max -> {
                            actual.removeAt(actual.lastIndex)
                            cerrar()
                            actual.add(intro)
                            actual.addAll(piezas)
                        }
                        largoLista <= max -> {
                            cerrar()
                            actual.addAll(piezas)
                        }
                        else -> {
                            // Lista larga: se reparte entre ítems.
                            if (intro == null && largo(actual) >= umbral) {
                                cerrar()
                            } else if (intro != null && actual.size > 1 && !cabe(listOf(piezas.first())) &&
                                largo(listOf(intro, piezas.first())) <= max
                            ) {
                                actual.removeAt(actual.lastIndex)
                                cerrar()
                                actual.add(intro)
                            }
                            agregarUnaPorUna(piezas)
                        }
                    }
                }
            }
        }
        cerrar()
        fusionarFragmentos(tarjetas, max)
        return tarjetas.map(::aTarjeta)
    }

    private fun piezasDeParrafo(contenido: TextoConPaginas, max: Int): List<Pieza> {
        val texto = contenido.texto
        val piezas = mutableListOf<Pieza>()
        for (rango in DivisorOraciones.rangos(texto)) {
            val oracion = texto.substring(rango)
            if (oracion.length <= max) {
                piezas.add(Pieza(oracion, contenido.paginaEn(rango.first), esItem = false))
            } else {
                for (parte in Partidor.partir(oracion, max)) {
                    piezas.add(Pieza(oracion.substring(parte), contenido.paginaEn(rango.first + parte.first), esItem = false))
                }
            }
        }
        return piezas
    }

    /**
     * Un ítem que entra es una sola pieza. Si no entra, se parte entre oraciones y solo una
     * oración más larga que el máximo se parte con [Partidor]; todas las piezas después de la
     * primera continúan el ítem (sin salto de línea).
     */
    private fun piezasDeItem(contenido: TextoConPaginas, max: Int): List<Pieza> {
        val texto = contenido.texto
        if (texto.length <= max) return listOf(Pieza(texto, contenido.paginaInicial, esItem = true))
        val piezas = mutableListOf<Pieza>()
        for (rango in DivisorOraciones.rangos(texto)) {
            val oracion = texto.substring(rango)
            val partes = if (oracion.length <= max) listOf(oracion.indices) else Partidor.partir(oracion, max)
            for (parte in partes) {
                val pagina = contenido.paginaEn(rango.first + parte.first)
                piezas.add(Pieza(oracion.substring(parte), pagina, esItem = true, continua = piezas.isNotEmpty()))
            }
        }
        return piezas
    }

    private fun fusionarFragmentos(tarjetas: MutableList<MutableList<Pieza>>, max: Int) {
        val limite = (max * TOLERANCIA_FUSION).toInt()
        var i = 0
        while (i < tarjetas.size) {
            val tarjeta = tarjetas[i]
            if (tarjetas.size > 1 && largo(tarjeta) < MIN_FRAGMENTO) {
                if (i > 0 && largo(tarjetas[i - 1] + tarjeta) <= limite) {
                    tarjetas[i - 1].addAll(tarjeta)
                    tarjetas.removeAt(i)
                    continue
                }
                if (i + 1 < tarjetas.size && largo(tarjeta + tarjetas[i + 1]) <= limite) {
                    tarjetas[i + 1].addAll(0, tarjeta)
                    tarjetas.removeAt(i)
                    continue
                }
            }
            i++
        }
    }

    private fun aTarjeta(piezas: List<Pieza>): TarjetaArmada {
        val sb = StringBuilder(piezas[0].texto)
        for (i in 1 until piezas.size) sb.append(separador(piezas[i - 1], piezas[i])).append(piezas[i].texto)
        return TarjetaArmada(sb.toString().trim(), piezas[0].pagina)
    }
}
