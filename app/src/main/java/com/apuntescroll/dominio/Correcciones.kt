package com.apuntescroll.dominio

/**
 * Correcciones de una línea que se hacen al normalizar el texto: letras espaciadas de los títulos
 * de los PDF digitales y errores típicos del OCR en castellano. Solo se corrige lo que no puede ser
 * una palabra (no hay diccionario).
 */
internal object Correcciones {

    private val ENTRE_PALABRAS = Regex(" {2,}")

    /** Palabras cortas comunes: un grupo hecho solo de estas es texto normal, no letras espaciadas. */
    private val PALABRAS_CORTAS = setOf(
        "a", "e", "o", "u", "y", "al", "de", "el", "en", "es", "la", "le", "lo", "me", "mi", "ni", "no",
        "se", "si", "su", "te", "tu", "un", "ya",
    )

    /**
     * Une las letras espaciadas ("tracking") que PdfBox saca de algunos títulos: las letras (o pares
     * de letras) van separadas por un espacio y las palabras por dos o más ("G U Í A  O P E R AT I
     * VA" → "GUÍA OPERATIVA", "PA RT E  I I  ·  1" → "PARTE II · 1"). Se hace solo si la línea
     * tiene un grupo de al menos 3 fichas de 1 o 2 letras y la mitad no son palabras cortas comunes
     * ("y la de" no se toca). Recibe la línea sin colapsar los espacios.
     */
    fun unirLetrasEspaciadas(linea: String): String {
        if (!linea.contains(' ')) return linea
        val grupos = linea.trim().split(ENTRE_PALABRAS)
        fun fichas(grupo: String) = grupo.split(' ').filter { it.isNotEmpty() }
        fun espaciado(f: List<String>) = f.size >= 2 && f.all { ficha -> ficha.length <= 2 && ficha.any { it.isLetter() } }
        val conFirma = grupos.any { g ->
            val f = fichas(g)
            f.size >= 3 && espaciado(f) && f.count { it.lowercase() !in PALABRAS_CORTAS } * 2 >= f.size
        }
        if (!conFirma) return linea
        return grupos.joinToString(" ") { g ->
            val f = fichas(g)
            if (espaciado(f)) f.joinToString("") else f.joinToString(" ")
        }
    }

    private val EI = Regex("(?<![\\p{L}\\d])EI(?![\\p{L}\\d])")
    private val GUE = Regex("(?<!\\p{L})(gue|Gue|GUE)(?!\\p{L})")
    private val PALABRA_CON_CERO = Regex("[\\p{L}\\d]*\\p{L}0[\\p{L}\\d]*")
    private val LLAMADA_VACIA = Regex("\\s*\\(\\s*[\"']?\\s*\\)")

    /**
     * Corrige errores del OCR que no pueden ser palabras:
     * - "EI" (E + i mayúscula) es "El" (o "EL" en una línea en mayúsculas).
     * - "gue" suelto es "que".
     * - Un cero pegado después de una letra, en una palabra de 3 letras o más sin otras cifras, es
     *   una "o" ("Asimism0", "sujet0S", y el "n0" de dos letras); no se tocan "P0", "x0", "F0",
     *   "G20" ni "0km".
     * - Las llamadas a notas que el OCR leyó vacías ("civil (").", "una causa () que") se quitan,
     *   con el espacio de antes, solo si van pegadas a una palabra.
     */
    fun corregirOcr(linea: String): String {
        if (linea.isEmpty()) return linea
        var l = linea
        if (l.contains("EI")) l = EI.replace(l, if (DetectorTitulos.esMayusculas(l)) "EL" else "El")
        if (l.contains("ue") || l.contains("UE")) l = GUE.replace(l) { m ->
            when (m.value) { "gue" -> "que"; "Gue" -> "Que"; else -> "QUE" }
        }
        if (l.contains('0')) l = PALABRA_CON_CERO.replace(l) { m -> ceroPorO(m.value) }
        if (l.contains('(')) l = quitarLlamadasVacias(l)
        return l
    }

    private fun ceroPorO(palabra: String): String {
        if (palabra.any { it.isDigit() && it != '0' }) return palabra
        // "P0", "x0", "F0": variables y siglas cortas, no errores (salvo el "n0" del OCR).
        if (palabra.length == 2 && palabra != "n0") return palabra
        if (palabra.length == 3 && palabra.none { it.isLowerCase() }) return palabra
        val sb = StringBuilder(palabra)
        for (i in sb.indices) {
            if (sb[i] != '0' || i == 0 || !sb[i - 1].isLetter()) continue
            sb.setCharAt(i, if (sb[i - 1].isLowerCase()) 'o' else 'O')
        }
        return sb.toString()
    }

    private fun quitarLlamadasVacias(linea: String): String {
        val sb = StringBuilder()
        var desde = 0
        for (m in LLAMADA_VACIA.findAll(linea)) {
            val antes = linea.getOrNull(m.range.first - 1)
            val primero = linea.substring(m.range).firstOrNull { !it.isWhitespace() }
            // Solo una llamada pegada a una palabra ("civil (") ") y no una casilla "( ) Verdadero".
            val pegada = m.range.first > 0 && (linea[m.range.first].isWhitespace() || antes?.isLetterOrDigit() == true) &&
                linea.substring(0, m.range.first).trimEnd().lastOrNull()?.isLetterOrDigit() == true
            if (primero != '(' || !pegada) continue
            sb.append(linea, desde, m.range.first)
            val siguiente = linea.getOrNull(m.range.last + 1)
            if (siguiente != null && siguiente.isLetterOrDigit()) sb.append(' ')
            desde = m.range.last + 1
        }
        sb.append(linea, desde, linea.length)
        return sb.toString()
    }
}
