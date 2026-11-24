package com.apuntescroll.dominio

/** Pieza de un documento ya entendido: un título, un párrafo del cuerpo o una lista. */
internal sealed interface Bloque {
    val pagina: Int

    data class Titulo(val texto: String, override val pagina: Int) : Bloque

    /** [esLinea]: es una línea suelta (un subtítulo sin numerar, por ejemplo) y va en su propio renglón. */
    class Parrafo(val contenido: TextoConPaginas, val esLinea: Boolean = false) : Bloque {
        override val pagina: Int get() = contenido.paginaInicial
    }

    /** Lista: cada ítem conserva su marca ("• ", "a) ", "1. "); las líneas sueltas van sin marca. */
    class Lista(val items: List<TextoConPaginas>) : Bloque {
        override val pagina: Int get() = items.first().paginaInicial
    }
}

/**
 * Decide qué es cada párrafo (PLAN.md, sección 3.2): título, ítem de lista o cuerpo.
 *
 * - Ítem: empieza con viñeta o con una enumeración con paréntesis ("a)", "1)", "iv)"), o con
 *   "1. " cuando no es candidato a título. Los ítems seguidos forman una sola lista.
 * - Candidato a título: un párrafo de hasta 2 líneas que [DetectorTitulos.pareceTitulo].
 * - Un candidato numerado que tiene al lado (antes o después, sin cuerpo en el medio) otro
 *   párrafo numerado del mismo tipo y nivel ("1." y "2.", no "3." y "3.1") es un ítem: una lista
 *   numerada, no una serie de títulos.
 * - Un título tiene que estar seguido de cuerpo: los candidatos del final del documento son
 *   cuerpo.
 * - Un título que es solo "Capítulo N" (o Unidad, Tema...) seguido de un subtítulo corto sin
 *   punto final y después texto: un solo título ("Capítulo 2 · El Derecho privado: ...").
 * - Varios candidatos seguidos: los dos últimos forman un solo título, unidos con " · "
 *   ("Unidad 3: Elasticidad · 3.1 Concepto"); los anteriores (por ejemplo, la portada) quedan
 *   como un bloque de líneas sueltas, sin viñetas.
 * - Línea suelta: un párrafo de una línea, de hasta [MAX_LINEA_SUELTA] caracteres, que no
 *   termina frase ni queda abierta ("Preguntas de repaso", la fila de una tabla). Varias seguidas
 *   forman un bloque de líneas; si vienen justo antes de una lista, encabezan la lista; una sola
 *   va en su propio renglón.
 */
internal object Estructura {

    const val SEPARADOR_TITULOS = " · "
    const val MAX_LINEA_SUELTA = 60

    private enum class Tipo { CUERPO, LINEA, ITEM, CANDIDATO }

    fun clasificar(parrafos: List<ParrafoCrudo>): List<Bloque> {
        val n = parrafos.size
        val numeraciones = parrafos.map { DetectorTitulos.numeracion(it.texto) }
        val tipos = parrafos.map(::tipoInicial).toMutableList()

        // Numerados cortos seguidos, del mismo nivel: lista, no títulos.
        fun hermano(i: Int, j: Int): Boolean {
            if (j !in 0 until n || tipos[j] == Tipo.CUERPO) return false
            val a = numeraciones[i] ?: return false
            val b = numeraciones[j] ?: return false
            return DetectorTitulos.sonHermanas(a, b)
        }
        val enLista = (0 until n).filter { i ->
            tipos[i] == Tipo.CANDIDATO && numeraciones[i] != null && (hermano(i, i - 1) || hermano(i, i + 1))
        }
        for (i in enLista) tipos[i] = Tipo.ITEM

        val bloques = mutableListOf<Bloque>()
        var i = 0
        while (i < n) {
            when (tipos[i]) {
                Tipo.CUERPO -> {
                    bloques.add(Bloque.Parrafo(parrafos[i].contenido))
                    i++
                }
                Tipo.LINEA, Tipo.ITEM -> {
                    // Líneas sueltas seguidas, con la lista que venga justo después.
                    var j = i
                    while (j < n && tipos[j] == Tipo.LINEA) j++
                    while (j < n && tipos[j] == Tipo.ITEM) j++
                    if (j == i + 1 && tipos[i] == Tipo.LINEA) {
                        bloques.add(Bloque.Parrafo(parrafos[i].contenido, esLinea = true))
                    } else {
                        bloques.add(Bloque.Lista(parrafos.subList(i, j).map { it.contenido }))
                    }
                    i = j
                }
                Tipo.CANDIDATO -> {
                    var j = i
                    while (j < n && tipos[j] == Tipo.CANDIDATO) j++
                    val corrida = parrafos.subList(i, j)
                    if (j == n) {
                        // Al final del documento no hay cuerpo que titular.
                        corrida.forEach { bloques.add(Bloque.Parrafo(it.contenido)) }
                    } else {
                        val sueltas = corrida.dropLast(2)
                        if (sueltas.isNotEmpty()) bloques.add(Bloque.Lista(sueltas.map { it.contenido }))
                        val titulares = corrida.takeLast(2)
                        var texto = titulares.joinToString(SEPARADOR_TITULOS) { DetectorTitulos.normalizarTitulo(it.texto) }
                        // "Capítulo 2" y su subtítulo en el párrafo de abajo: un solo título.
                        if (corrida.size == 1 && DetectorTitulos.esSoloPalabraClave(corrida[0].texto) && esSubtitulo(parrafos, tipos, j)) {
                            texto += SEPARADOR_TITULOS + parrafos[j].texto
                            j++
                        }
                        bloques.add(Bloque.Titulo(texto, titulares.first().pagina))
                    }
                    i = j
                }
            }
        }
        return bloques
    }

    private fun tipoInicial(p: ParrafoCrudo): Tipo {
        val texto = p.texto
        return when {
            // "1 Ibíd., p. 23" parece un título numerado, pero es una nota al pie.
            p.esNota -> Tipo.CUERPO
            Renglones.esItemMarcado(texto) -> Tipo.ITEM
            p.lineas.size <= 2 && DetectorTitulos.pareceTitulo(texto) -> Tipo.CANDIDATO
            Renglones.esNumeradoSimple(texto) -> Tipo.ITEM
            esLineaSuelta(p) -> Tipo.LINEA
            else -> Tipo.CUERPO
        }
    }

    /** Largo máximo del subtítulo que se une a "Capítulo N". */
    private const val MAX_LARGO_SUBTITULO = 120

    /**
     * true si el párrafo [j] es el subtítulo de un "Capítulo N" de arriba: de hasta 2 líneas y
     * [MAX_LARGO_SUBTITULO] caracteres, no termina frase, no es nota ni ítem, y lo sigue texto (no
     * otro título).
     */
    private fun esSubtitulo(parrafos: List<ParrafoCrudo>, tipos: List<Tipo>, j: Int): Boolean {
        val p = parrafos.getOrNull(j) ?: return false
        if (tipos[j] != Tipo.CUERPO && tipos[j] != Tipo.LINEA) return false
        if (p.esNota || p.lineas.size > 2 || p.texto.length > MAX_LARGO_SUBTITULO || Renglones.terminaFrase(p.texto)) return false
        return j + 1 < parrafos.size && tipos[j + 1] != Tipo.CANDIDATO
    }

    private fun esLineaSuelta(p: ParrafoCrudo): Boolean {
        val texto = p.texto
        return p.lineas.size == 1 && texto.length <= MAX_LINEA_SUELTA &&
            !Renglones.terminaFrase(texto) && !Renglones.terminaAbierta(texto)
    }
}
