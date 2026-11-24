package com.apuntescroll.dominio

/**
 * Divide un párrafo en oraciones, pensado para apuntes en castellano.
 *
 * Una oración termina en `.` `!` `?` `…` (o una seguidilla como `?!` o `...`), con comillas o
 * paréntesis de cierre opcionales (`."`, `?»`, `.)`), seguida de espacio y de algo que puede
 * abrir una oración: mayúscula, `¿`, `¡`, comillas o paréntesis de apertura, o un número.
 *
 * No se corta:
 * - Después de la marca de enumeración con que empieza el texto ("1. Hay muchos...", "IV. ...").
 * - Después de abreviaturas que van antes de otra cosa: Sr., Dra., Lic., Prof., pág., p., pp.,
 *   art., inc., núm., cap., fig., vol., ed., cf., ej., Av., Ud., vs., y las de citas legales y
 *   lugares (Bs., Cód., Civ., Cám., fs.).
 * - Después de una inicial ("J. M. Keynes"), salvo que la siga una palabra que suele abrir
 *   oraciones ("el bien X. El precio...").
 * - Después de abreviaturas que también pueden cerrar una oración (etc., aprox., EE. UU., S.A.,
 *   hs., As., Nac., Com., Prov., Res., Pcia., Cdor., Univ., Dec., Expte.), salvo que las siga una
 *   palabra que suele abrir oraciones ("..., etc. La demanda...", "la Pcia. Luego...").
 * - Dentro de números ("3.5", "1.000"): no hay espacio después del punto.
 * - En unos puntos suspensivos seguidos de minúscula, o en "(...)".
 */
object DivisorOraciones {

    private const val TERMINALES = ".!?…"
    private const val CIERRES = "\"'»”’)]"
    private const val APERTURAS = "¿¡\"'«“‘(["

    /** Abreviaturas (en minúscula, sin el punto) después de las cuales nunca termina la oración. */
    private val ABREVIATURAS_FIJAS = setOf(
        "sr", "sra", "srta", "sres", "srs", "dr", "dra", "dres", "lic", "ing", "prof", "profa",
        "arq", "cr", "cra", "pág", "págs", "pag", "pags", "pp", "art", "arts", "inc", "incs",
        "núm", "num", "nro", "nros", "nº", "n°", "cap", "caps", "fig", "figs", "vol", "vols",
        "ed", "eds", "cf", "cfr", "av", "avda", "ud", "uds", "vd", "vds", "ej", "ejs", "tel",
        "dto", "depto", "dpto", "gral", "pto", "ptos", "ib", "ibíd", "ibid", "op", "cit", "loc",
        "ca", "cía", "cia", "máx", "mín", "ap", "apdo", "vs", "sta", "sto", "sgte", "sgtes", "ss",
        // Citas legales y nombres de lugares: Bs. As., Cód. Civ. y Com., Cám. Nac., fs. 12.
        "bs", "cód", "cod", "civ", "cám", "cam", "fs",
    )

    /**
     * Abreviaturas que pueden cerrar una oración: se corta solo si sigue una palabra de inicio
     * típica ("la Pcia. Luego...", pero no "Pcia. de Buenos Aires", "Dec. 1023/01", "Cdor. Pérez").
     */
    private val ABREVIATURAS_FINALES = setOf(
        "etc", "aprox", "ee", "uu", "hs", "hrs", "min", "seg", "kg", "km", "cm", "mm", "mts",
        "lts", "sa", "srl", "ltda", "as", "nac", "com", "prov", "res", "pcia", "cdor", "cdora",
        "univ", "dec", "expte",
    )

    /** Marca de enumeración al principio del texto ("1.", "2.3.", "a.", "IV."): no cierra una oración. */
    private val MARCA_INICIAL = Regex("^(?:\\d{1,3}(?:\\.\\d{1,3})*|[a-zA-Z]|[IVXLC]{1,6})$")

    /** Palabras (en minúscula) con las que suele empezar una oración en los apuntes. */
    private val INICIOS_COMUNES = setOf(
        "a", "además", "ahora", "al", "algunas", "algunos", "allí", "ambos", "ante", "antes", "aquí",
        "así", "asimismo", "aunque", "cada", "como", "con", "cuando", "de", "debe", "del", "desde", "después",
        "dicha", "dicho", "donde", "durante", "e", "el", "ella", "ello", "en", "entonces", "entre",
        "era", "es", "esa", "esas", "ese", "eso", "esos", "esta", "estas", "este", "esto", "estos",
        "existe", "existen", "finalmente", "fue", "hay", "hoy", "la", "las", "le", "les", "lo",
        "los", "luego", "más", "mientras", "muchas", "muchos", "no", "nos", "o", "otra", "otras", "otro",
        "otros", "para", "pero", "por", "posteriormente", "primero", "puede", "pueden", "que", "se", "segundo",
        "según", "si", "sin", "sobre", "son", "su", "sus", "también", "tal", "toda", "todas",
        "todo", "todos", "tras", "un", "una", "unas", "uno", "unos", "y", "ya",
    )

    /** Las oraciones de [texto], recortadas y en orden. */
    fun dividir(texto: String): List<String> = rangos(texto).map { texto.substring(it) }

    /** Las posiciones de cada oración dentro de [texto] (sin espacios al principio ni al final). */
    internal fun rangos(texto: String): List<IntRange> {
        val n = texto.length
        val resultado = mutableListOf<IntRange>()
        var inicio = 0
        var i = 0
        while (i < n) {
            if (texto[i] !in TERMINALES) {
                i++
                continue
            }
            var j = i
            while (j < n && texto[j] in TERMINALES) j++
            var k = j
            while (k < n && texto[k] in CIERRES) k++
            if (k >= n) break
            if (!texto[k].isWhitespace()) {
                i = k
                continue
            }
            var m = k
            while (m < n && texto[m].isWhitespace()) m++
            if (m >= n) break
            if (puedeAbrirOracion(texto[m]) && !esFinFalso(texto, i, j, m)) {
                agregar(resultado, texto, inicio, k)
                inicio = m
            }
            i = m
        }
        agregar(resultado, texto, inicio, n)
        return resultado
    }

    private fun agregar(resultado: MutableList<IntRange>, texto: String, desde: Int, hasta: Int) {
        var a = desde
        var b = hasta
        while (a < b && texto[a].isWhitespace()) a++
        while (b > a && texto[b - 1].isWhitespace()) b--
        if (a < b) resultado.add(a until b)
    }

    private fun puedeAbrirOracion(c: Char) = c.isUpperCase() || c.isDigit() || c in APERTURAS

    /**
     * true si los terminales entre [i] y [j] no cierran la oración. [m] es donde empieza lo que
     * sigue, ya sin espacios.
     */
    private fun esFinFalso(texto: String, i: Int, j: Int, m: Int): Boolean {
        val corrida = texto.substring(i, j)
        if (corrida == ".") return esAbreviatura(texto, i, m)
        if (corrida.all { it == '.' || it == '…' }) {
            // "(...)" o "[…]": texto omitido dentro de una cita.
            return i > 0 && texto[i - 1] in "(["
        }
        return false
    }

    private fun esAbreviatura(texto: String, punto: Int, m: Int): Boolean {
        var a = punto
        while (a > 0 && !texto[a - 1].isWhitespace()) a--
        val token = texto.substring(a, punto).trimStart { it in APERTURAS }
        if (token.isEmpty()) return false
        if (texto.substring(0, a).isBlank() && MARCA_INICIAL.matches(token)) return true
        if (token.all { it.isDigit() || it == '.' || it == ',' }) return false
        val sigueInicioComun = texto[m] in "¿¡" || palabraSiguiente(texto, m).lowercase() in INICIOS_COMUNES
        if (token.length == 1 && token[0].isLetter()) {
            if (token[0].isLowerCase()) return true
            if (esInicial(tokenDesde(texto, m))) return true
            if (esInicial(tokenAnterior(texto, a))) return true
            return !sigueInicioComun
        }
        val minus = token.lowercase()
        if (minus in ABREVIATURAS_FIJAS) return true
        if (minus in ABREVIATURAS_FINALES || '.' in token) return !sigueInicioComun
        return false
    }

    private fun esInicial(token: String) = token.length == 2 && token[0].isUpperCase() && token[1] == '.'

    private fun tokenDesde(texto: String, desde: Int): String {
        var b = desde
        while (b < texto.length && !texto[b].isWhitespace()) b++
        return texto.substring(desde, b)
    }

    private fun tokenAnterior(texto: String, inicioToken: Int): String {
        var b = inicioToken
        while (b > 0 && texto[b - 1].isWhitespace()) b--
        var a = b
        while (a > 0 && !texto[a - 1].isWhitespace()) a--
        return texto.substring(a, b)
    }

    private fun palabraSiguiente(texto: String, desde: Int): String {
        var a = desde
        while (a < texto.length && texto[a] in APERTURAS) a++
        var b = a
        while (b < texto.length && texto[b].isLetter()) b++
        return texto.substring(a, b)
    }
}
