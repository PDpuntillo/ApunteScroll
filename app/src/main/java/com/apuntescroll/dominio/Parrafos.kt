package com.apuntescroll.dominio

/** Una página después de [Limpieza]: sus líneas limpias, con "" donde había una línea vacía. */
internal data class PaginaLimpia(val numero: Int, val lineas: List<String>)

/** Una línea de texto y la página de donde salió. */
internal data class Linea(val texto: String, val pagina: Int)

/**
 * Texto armado a partir de varias líneas, que recuerda de qué página viene cada parte. Así una
 * tarjeta que empieza a mitad de un párrafo que cruza de página sabe en qué página empieza.
 */
internal class TextoConPaginas private constructor(val texto: String, private val tramos: List<Tramo>) {

    private data class Tramo(val desde: Int, val pagina: Int)

    val paginaInicial: Int get() = tramos.first().pagina

    /** Página de donde viene el carácter en la posición [indice]. */
    fun paginaEn(indice: Int): Int {
        var pagina = tramos.first().pagina
        for (t in tramos) {
            if (t.desde <= indice) pagina = t.pagina else break
        }
        return pagina
    }

    companion object {
        fun de(texto: String, pagina: Int) = TextoConPaginas(texto, listOf(Tramo(0, pagina)))

        /**
         * Une las líneas con un espacio. Si una línea termina en letra + "-" y la siguiente empieza
         * en minúscula, la palabra estaba cortada: se une sin el guion ("pala-" + "bra" →
         * "palabra"). Si termina en letra o número + "-" y la siguiente empieza con letra o número
         * (en mayúscula), se une sin espacio y se deja el guion ("Norte-" + "Sur" → "Norte-Sur").
         */
        fun unir(lineas: List<Linea>): TextoConPaginas {
            require(lineas.isNotEmpty())
            val sb = StringBuilder()
            val tramos = mutableListOf(Tramo(0, lineas.first().pagina))
            for (linea in lineas) {
                if (sb.isNotEmpty()) {
                    val ultimo = sb[sb.length - 1]
                    val penultimo = if (sb.length >= 2) sb[sb.length - 2] else ' '
                    val primero = linea.texto.first()
                    when {
                        ultimo == '-' && penultimo.isLetter() && primero.isLowerCase() -> sb.setLength(sb.length - 1)
                        ultimo == '-' && penultimo.isLetterOrDigit() && primero.isLetterOrDigit() -> Unit
                        else -> sb.append(' ')
                    }
                }
                if (linea.pagina != tramos.last().pagina) tramos.add(Tramo(sb.length, linea.pagina))
                sb.append(linea.texto)
            }
            return TextoConPaginas(sb.toString(), tramos)
        }
    }
}

/**
 * Un párrafo tal como salió de unir líneas, antes de decidir si es título, ítem o cuerpo.
 * [esNota]: es una nota al pie apartada del final de la página (nunca es título).
 */
internal class ParrafoCrudo(val lineas: List<Linea>, val esNota: Boolean = false) {
    val contenido: TextoConPaginas = TextoConPaginas.unir(lineas)
    val texto: String get() = contenido.texto
    val pagina: Int get() = lineas.first().pagina
}

/**
 * Une las líneas de todas las páginas en párrafos (PLAN.md, sección 3.2).
 *
 * Reglas, para dos líneas seguidas A y B:
 * - Una línea vacía siempre separa párrafos.
 * - B empieza con viñeta o con un número volado ("¹ Ver..."): párrafo nuevo (también cubre "A
 *   termina en `:` y B es viñeta").
 * - B empieza con una enumeración ("a) ", "1) ", "1. ", "2.3 Título"): párrafo nuevo si A termina
 *   frase o en `;`, si A es corta o si el párrafo actual ya es un ítem; salvo que A sea parte de
 *   una enumeración corrida (tiene un ítem numerado en el medio, como un "Sumario: 1. ... 2. ...").
 * - A termina frase (`.` `:` `?` `!` `»` `"` `)`) y B empieza como oración (mayúscula, ¿, ¡,
 *   número, comillas): párrafo nuevo si A es corta (menos del 85 % del largo típico) o si el
 *   párrafo actual es un ítem de lista.
 * - A parece un título ([DetectorTitulos.pareceTitulo]) aunque sea larga, no termina frase ni
 *   queda abierta, empieza su párrafo o la línea de antes cerró una oración, y B empieza como
 *   oración o con una enumeración: párrafo nuevo, salvo que A y B vayan las dos en mayúsculas (un
 *   párrafo escrito todo en mayúsculas).
 * - A no termina frase pero es muy corta (menos del 65 % del largo típico), no queda abierta (no
 *   termina en coma, guion ni en "de", "la", "y"...) y B empieza como oración: párrafo nuevo. Así
 *   se separan los títulos y las líneas sueltas.
 * - A termina frase, lo que sigue a B empieza como oración (o es el final del bloque) y B parece
 *   un título ([DetectorTitulos.pareceTitulo]) y es corta, o B es muy corta, no termina frase y
 *   no queda abierta (un subtítulo sin numerar, la fila de una tabla): párrafo nuevo, aunque A
 *   sea larga.
 * - En cualquier otro caso, B continúa el párrafo.
 *
 * El largo típico (el de una línea llena) es el percentil 90 del largo de las líneas del bloque
 * (las líneas entre dos vacías), sin contar viñetas ni enumeraciones; se usa el 90 y no la mediana
 * para que una tabla o una lista sin viñetas no lo achiquen. Si el bloque tiene menos de 4 líneas
 * se usa el de la página, si la página tampoco, el del documento y, si el documento tampoco, el
 * largo de su línea más larga. Si el del bloque o la página es menor que el 60 % del del
 * documento (una portada, un índice), se usa el del documento.
 *
 * Notas al pie: las que quedan al final de la página ([inicioDeNotas]) se apartan antes de unir
 * con la página siguiente, y salen como párrafos propios después del párrafo que estaba abierto.
 * Así una frase que sigue en la otra página no queda con la cita en el medio.
 *
 * Entre páginas (A es la última línea del cuerpo, sin las notas): si B es un ítem o una
 * enumeración ("a) ...", "1. ...") no sigue; si no, sigue si B empieza en minúscula, o si B no es
 * título y A no termina frase y, además, queda abierta o es larga. El párrafo pertenece a la
 * página donde empezó, pero recuerda dónde cambia de página.
 */
internal object Parrafos {

    private const val CORTA = 0.85
    private const val MUY_CORTA = 0.65
    private const val MIN_LINEAS_TIPICO = 4
    private const val PISO_TIPICO = 0.6
    private const val TIPICO_SIN_DATOS = 60

    private class Bloque(val lineas: List<String>, val tipico: Int)

    fun armar(paginas: List<PaginaLimpia>): List<ParrafoCrudo> {
        val todasLasLineas = paginas.flatMap { it.lineas }.filter { it.isNotEmpty() }
        // Con muy pocas líneas de texto corrido el percentil no dice nada: se usa la más larga.
        val tipicoDocumento = percentil90(todasLasLineas.filter(::cuentaParaTipico).map { it.length }, MIN_LINEAS_TIPICO)
            ?: todasLasLineas.maxOfOrNull { it.length }
            ?: TIPICO_SIN_DATOS

        val resultado = mutableListOf<ParrafoCrudo>()
        var actual = mutableListOf<Linea>()
        // Notas al pie de la página anterior: van después del párrafo que estaba abierto.
        val notasPendientes = mutableListOf<ParrafoCrudo>()
        fun cerrar() {
            if (actual.isNotEmpty()) {
                resultado.add(ParrafoCrudo(actual))
                actual = mutableListOf()
            }
            resultado.addAll(notasPendientes)
            notasPendientes.clear()
        }

        var tipicoAnterior = tipicoDocumento
        for (pagina in paginas) {
            val inicioNotas = inicioDeNotas(pagina.lineas, tipicoDocumento)
            val cuerpo = if (inicioNotas == null) pagina.lineas else pagina.lineas.subList(0, inicioNotas)
            val bloques = bloquesDe(cuerpo, tipicoDocumento)
            for ((indiceBloque, bloque) in bloques.withIndex()) {
                for ((i, texto) in bloque.lineas.withIndex()) {
                    if (actual.isNotEmpty()) {
                        val a = actual.last().texto
                        val esItem = esItem(actual.first().texto)
                        val antesDeA = actual.getOrNull(actual.size - 2)?.texto
                        val corta = when {
                            i > 0 -> hayCorte(antesDeA, a, texto, bloque.lineas.getOrNull(i + 1), bloque.tipico, esItem)
                            indiceBloque > 0 -> true
                            else -> !sigueEnOtraPagina(a, texto, tipicoAnterior)
                        }
                        if (corta) cerrar()
                    }
                    actual.add(Linea(texto, pagina.numero))
                }
                tipicoAnterior = bloque.tipico
            }
            if (inicioNotas != null) notasPendientes.addAll(notasDe(pagina.lineas.subList(inicioNotas, pagina.lineas.size), pagina.numero))
        }
        cerrar()
        return resultado
    }

    /** Un ítem numerado en el medio del renglón, después de un signo: "...del derecho. 4. Clases...". */
    private val ENUMERACION_EN_LINEA = Regex("[.;:]\\s+\\d{1,2}(?:\\.\\d{1,2})*\\.\\s+\\p{Lu}")

    /** Las notas al pie se buscan entre las últimas líneas de la página. */
    private const val MAX_LINEAS_NOTAS = 6

    /** Con números volados que el cuerpo llama, el bloque de notas puede ser más largo (Derecho, Historia). */
    private const val MAX_LINEAS_NOTAS_LLAMADAS = 15
    private val NOTA_NUMERADA = Regex("^(\\d{1,2})\\s+\\p{Lu}")
    private val VOLADOS = "⁰¹²³⁴⁵⁶⁷⁸⁹"
    private val LLAMADA_CON_ASTERISCO = Regex("\\S\\*")
    private val SIGNOS_DE_CITA = Regex("[.(\\d]")

    /**
     * Dónde empiezan las notas al pie del final de la página (PdfBox las da después del cuerpo), o
     * null si no hay. Tiene que quedar al menos una línea de cuerpo arriba.
     * - Número volado ("¹ Ver..."): entre las últimas [MAX_LINEAS_NOTAS] líneas, siempre; más arriba
     *   (hasta [MAX_LINEAS_NOTAS_LLAMADAS]), si el cuerpo tiene la llamada ("agropecuarios¹").
     * - Número común ("1 Ver...") o asterisco pegado ("*Fuente..."): entre las últimas
     *   [MAX_LINEAS_NOTAS] líneas y solo si el cuerpo tiene la llamada ("agropecuarios1",
     *   "agropecuarios¹", "INDEC*"). Con número común, además, el texto tiene que tener algo de cita
     *   (un punto, un paréntesis o un número): "3 Elasticidad cruzada" es un subtítulo.
     * - En ningún caso si la línea es corta (menos del 65 % del largo típico) y la sigue una línea
     *   sin marca de nota: es un subtítulo o la fuente de un cuadro, y lo que sigue es cuerpo.
     */
    private fun inicioDeNotas(lineas: List<String>, tipico: Int): Int? {
        val noVacias = lineas.indices.filter { lineas[it].isNotEmpty() }
        if (noVacias.size < 2) return null
        for (pos in maxOf(1, noVacias.size - MAX_LINEAS_NOTAS_LLAMADAS) until noVacias.size) {
            val i = noVacias[pos]
            val linea = lineas[i]
            if (!tieneMarcaDeNota(linea)) continue
            val cerca = pos >= noVacias.size - MAX_LINEAS_NOTAS
            val llamada = hayLlamada(linea, lineas.subList(0, i).joinToString(" "))
            val valida = when {
                Renglones.empiezaConVolado(linea) -> cerca || llamada
                linea[0] == '*' -> cerca && llamada
                else -> cerca && llamada && SIGNOS_DE_CITA.containsMatchIn(linea.substringAfter(' '))
            }
            if (!valida) continue
            val siguiente = noVacias.getOrNull(pos + 1)?.let { lineas[it] }
            if (linea.length < tipico * MUY_CORTA && siguiente != null && !tieneMarcaDeNota(siguiente)) continue
            return i
        }
        return null
    }

    /** true si la línea arranca como una nota al pie: "¹ ...", "*Ver ..." o "1 Ver ...". */
    private fun tieneMarcaDeNota(linea: String): Boolean =
        Renglones.empiezaConVolado(linea) ||
            (linea.length > 1 && linea[0] == '*' && !linea[1].isWhitespace()) ||
            NOTA_NUMERADA.containsMatchIn(linea)

    /** true si el cuerpo de la página llama a la nota que empieza en [linea]. */
    private fun hayLlamada(linea: String, cuerpo: String): Boolean {
        if (linea[0] == '*') return LLAMADA_CON_ASTERISCO.containsMatchIn(cuerpo)
        val volados = linea.takeWhile { it.category == CharCategory.OTHER_NUMBER }
        if (volados.isNotEmpty()) return cuerpo.contains(volados)
        val numero = NOTA_NUMERADA.find(linea)?.groupValues?.get(1) ?: return false
        val volado = numero.map { VOLADOS[it - '0'] }.joinToString("")
        return cuerpo.contains(volado) || Regex("\\p{L}[.,;:)]?$numero(?!\\d)").containsMatchIn(cuerpo)
    }

    /** Las líneas de notas al pie como párrafos: cada nota empieza un párrafo y sus renglones siguientes se le unen. */
    private fun notasDe(lineas: List<String>, pagina: Int): List<ParrafoCrudo> {
        val notas = mutableListOf<MutableList<Linea>>()
        for (l in lineas) {
            if (l.isEmpty()) continue
            if (notas.isEmpty() || tieneMarcaDeNota(l)) notas.add(mutableListOf())
            notas.last().add(Linea(l, pagina))
        }
        return notas.map { ParrafoCrudo(it, esNota = true) }
    }

    private fun bloquesDe(lineas: List<String>, tipicoDocumento: Int): List<Bloque> {
        val grupos = mutableListOf<List<String>>()
        var grupo = mutableListOf<String>()
        for (l in lineas) {
            if (l.isEmpty()) {
                if (grupo.isNotEmpty()) grupos.add(grupo)
                grupo = mutableListOf()
            } else {
                grupo.add(l)
            }
        }
        if (grupo.isNotEmpty()) grupos.add(grupo)
        val largosPagina = grupos.flatten().filter(::cuentaParaTipico).map { it.length }
        val tipicoPagina = conPiso(percentil90(largosPagina, MIN_LINEAS_TIPICO) ?: tipicoDocumento, tipicoDocumento)
        return grupos.map { g ->
            val tipico = percentil90(g.filter(::cuentaParaTipico).map { it.length }, MIN_LINEAS_TIPICO) ?: tipicoPagina
            Bloque(g, conPiso(tipico, tipicoDocumento))
        }
    }

    /**
     * En una página donde todas las líneas son cortas (una portada, un índice) el largo típico
     * propio no sirve para decir qué línea es corta: si es mucho menor que el del documento, se usa
     * el del documento.
     */
    private fun conPiso(tipico: Int, tipicoDocumento: Int) =
        if (tipico < tipicoDocumento * PISO_TIPICO) tipicoDocumento else tipico

    private fun cuentaParaTipico(linea: String) = !Renglones.esItemMarcado(linea) && !Renglones.esEnumeracion(linea)

    private fun percentil90(largos: List<Int>, minimo: Int): Int? {
        if (largos.size < minimo || largos.isEmpty()) return null
        val ordenados = largos.sorted()
        return ordenados[(ordenados.size - 1) * 9 / 10]
    }

    private fun esItem(primeraLinea: String) = Renglones.esItemMarcado(primeraLinea) || Renglones.esEnumeracion(primeraLinea)

    /**
     * true si entre las líneas [a] y [b] de un mismo bloque termina un párrafo. [antesDeA] es la
     * línea anterior a [a] en el párrafo (null si [a] lo empieza) y [c], la línea después de [b].
     */
    private fun hayCorte(antesDeA: String?, a: String, b: String, c: String?, tipico: Int, parrafoEsItem: Boolean): Boolean {
        if (Renglones.esVineta(b) || Renglones.empiezaConVolado(b)) return true
        val aTermina = Renglones.terminaFrase(a)
        val aCorta = a.length < tipico * CORTA
        // Una enumeración corrida ("Sumario: 1. X. 2. Y. 3. Z.") sigue en el renglón de abajo aunque
        // este empiece con un número.
        val enumeracionCorrida = ENUMERACION_EN_LINEA.containsMatchIn(a)
        if (Renglones.esEnumeracion(b) && !enumeracionCorrida && (aTermina || a.endsWith(';') || aCorta || parrafoEsItem)) return true
        if (esTituloLargo(antesDeA, a, b)) return true
        if (!Renglones.empiezaComoOracion(b)) return false
        if (aTermina && (aCorta || parrafoEsItem)) return true
        if (!aTermina && a.length < tipico * MUY_CORTA && !Renglones.terminaAbierta(a)) return true
        if (aTermina) {
            // Después de B arranca otra cosa (o se termina el bloque): B puede estar sola.
            val cAbre = c == null || Renglones.empiezaComoOracion(c) || Renglones.esVineta(c)
            if (cAbre && b.length < tipico * CORTA && DetectorTitulos.pareceTitulo(b)) return true
            // B es una línea suelta (un subtítulo sin numerar, la fila de una tabla).
            if (cAbre && b.length < tipico * MUY_CORTA && !Renglones.terminaFrase(b) && !Renglones.terminaAbierta(b)) return true
        }
        return false
    }

    /**
     * true si [a] es un título, aunque sea largo ("3.1.2 Efectos de los impuestos sobre el
     * equilibrio del mercado"), seguido directamente del texto: [a] parece título, no termina frase
     * ni queda abierta, empieza su párrafo o la línea de antes cerró una oración, y [b] empieza como
     * oración o con una enumeración. Si [a] y [b] van los dos en mayúsculas, es un párrafo escrito
     * en mayúsculas y no se corta.
     */
    private fun esTituloLargo(antesDeA: String?, a: String, b: String): Boolean {
        if (!DetectorTitulos.pareceTitulo(a) || Renglones.terminaFrase(a) || Renglones.terminaAbierta(a)) return false
        if (antesDeA != null && !Renglones.terminaFrase(antesDeA)) return false
        if (!Renglones.empiezaComoOracion(b) && !Renglones.esEnumeracion(b)) return false
        return !(DetectorTitulos.esMayusculas(a) && DetectorTitulos.esMayusculas(b))
    }

    /** true si el párrafo que termina la página con la línea [a] sigue en la línea [b] de la página siguiente. */
    private fun sigueEnOtraPagina(a: String, b: String, tipico: Int): Boolean {
        // Un ítem ("a) ...", "i) ...") empieza en minúscula pero no continúa la frase de arriba.
        if (Renglones.esItemMarcado(b) || Renglones.esEnumeracion(b)) return false
        if (Renglones.empiezaEnMinuscula(b)) return true
        if (DetectorTitulos.pareceTitulo(b)) return false
        if (Renglones.terminaFrase(a)) return false
        return Renglones.terminaAbierta(a) || a.length >= tipico * CORTA
    }
}
