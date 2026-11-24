package com.apuntescroll.dominio

/**
 * Reconoce el texto de un título (PLAN.md, sección 3.2) y lo deja presentable.
 *
 * Acá solo se mira el texto. Que la línea esté sola, que no forme parte de una lista numerada y
 * que la siga texto del cuerpo lo decide [Estructura].
 */
internal object DetectorTitulos {

    /** Largo máximo de un título, en caracteres. */
    const val MAX_LARGO = 80

    enum class TipoNumeracion { ARABIGA, ROMANA, LETRA }

    /**
     * Numeración al principio de un título: "2.3 " es [TipoNumeracion.ARABIGA] con 2 [niveles];
     * "IV - " es romana; "A. " es una letra. [ambigua] marca una sola letra que también es un
     * número romano ("C.", "I."), que puede ser hermana de cualquiera de las dos. [prefijo] es el
     * texto de la numeración con sus espacios.
     */
    data class Numeracion(
        val tipo: TipoNumeracion,
        val niveles: Int,
        val ambigua: Boolean,
        val prefijo: String,
    )

    // "1 ", "1. ", "2.3 ", "1.2.3. ", "4 - ", "4.- ", "4: " seguido de mayúscula.
    private val ARABIGA = Regex("^(\\d{1,3}(?:\\.\\d{1,3}){0,4})\\.?(?:-|\\s*[-–—:])?\\s+")
    // "I. ", "IV - ", "XII: "
    private val ROMANA = Regex("^([IVXLC]{1,7})(?:\\.|\\s*[-–—:])\\s+")
    private val ROMANO_VALIDO = Regex("^(?=[IVXLC])C{0,3}(?:XC|XL|L?X{0,3})(?:IX|IV|V?I{0,3})$")
    // "A. ", "B - "
    private val LETRA = Regex("^([A-Z])(?:\\.|\\s+[-–—])\\s+")
    private val INICIAL = Regex("^[A-Z]\\.(?:\\s|$)")

    /** La numeración del principio de [texto], si el resto empieza en mayúscula; si no, null. */
    fun numeracion(texto: String): Numeracion? {
        ARABIGA.find(texto)?.let { m ->
            val resto = texto.substring(m.range.last + 1)
            if (!empiezaEnMayuscula(resto)) return null
            val niveles = m.groupValues[1].count { it == '.' } + 1
            return Numeracion(TipoNumeracion.ARABIGA, niveles, ambigua = false, prefijo = m.value)
        }
        ROMANA.find(texto)?.let { m ->
            val numero = m.groupValues[1]
            val resto = texto.substring(m.range.last + 1)
            if (ROMANO_VALIDO.matches(numero) && empiezaEnMayuscula(resto)) {
                return Numeracion(TipoNumeracion.ROMANA, 1, ambigua = numero.length == 1, prefijo = m.value)
            }
        }
        LETRA.find(texto)?.let { m ->
            val resto = texto.substring(m.range.last + 1)
            // "J. M. Keynes" son iniciales, no una numeración.
            if (empiezaEnMayuscula(resto) && !INICIAL.containsMatchIn(resto)) {
                return Numeracion(TipoNumeracion.LETRA, 1, ambigua = false, prefijo = m.value)
            }
        }
        return null
    }

    /** true si [a] y [b] numeran ítems del mismo nivel ("1." y "2.", "a." y "b."), no título y subtítulo. */
    fun sonHermanas(a: Numeracion, b: Numeracion): Boolean {
        if (a.niveles != b.niveles) return false
        if (a.tipo == b.tipo) return true
        val sinArabiga = a.tipo != TipoNumeracion.ARABIGA && b.tipo != TipoNumeracion.ARABIGA
        return sinArabiga && (a.ambigua || b.ambigua)
    }

    private fun empiezaEnMayuscula(texto: String): Boolean {
        val c = texto.firstOrNull() ?: return false
        return c.isUpperCase() || c in "¿¡\"«'"
    }

    private val PALABRA_CLAVE = Regex(
        "^(unidad|cap[ií]tulo|m[oó]dulo|tema|parte|secci[oó]n|bloque)(?!\\p{L})[\\s.:–—-]*(\\S.*)$",
        RegexOption.IGNORE_CASE,
    )
    private val PRIMERA_PALABRA = Regex("^\\p{L}+")
    private val NUMEROS_EN_LETRAS = setOf(
        "uno", "una", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez",
        "once", "doce", "trece", "catorce", "quince", "dieciséis", "dieciseis", "diecisiete",
        "dieciocho", "diecinueve", "veinte", "primero", "primera", "segundo", "segunda", "tercero",
        "tercera", "cuarto", "cuarta", "quinto", "quinta", "sexto", "sexta", "séptimo", "séptima",
        "septimo", "septima", "octavo", "octava", "noveno", "novena", "décimo", "décima", "decimo",
        "decima", "único", "única", "unico", "unica", "final", "preliminar", "introductorio",
        "introductoria",
    )

    /** "Unidad Temática 2", "Unidad didáctica 4": el calificativo va antes del número. */
    private val CALIFICATIVO = Regex("^(?:tem[aá]tica|did[aá]ctica)(?!\\p{L})[\\s.:]*", RegexOption.IGNORE_CASE)

    /** "Unidad Nº 3", "N° 3", "N.º 3", "Nro. 3", "Núm. 3", "No. 3". */
    private val MARCA_DE_NUMERO = Regex("^(?:n\\.?\\s?[°º]\\.?|nro\\.?|n[uú]m\\.?|no\\.)\\s*", RegexOption.IGNORE_CASE)

    /** Después de un número en letras: fin de la línea o un signo ("Capítulo uno: ..."). */
    private val CIERRE_DE_NUMERO_EN_LETRAS = Regex("^\\s*(?:$|[:.\\-–—])")

    /**
     * true si empieza con Unidad, Capítulo, Módulo, Tema, Parte, Sección o Bloque seguida de un
     * número ("Unidad 3"), un romano ("Parte II", "Capítulo iv"), una letra mayúscula ("Sección A")
     * o un número en letras que cierra la línea o va antes de un signo ("Capítulo uno", "Módulo
     * introductorio: ..."). Antes del número puede ir un calificativo ("Unidad Temática 2") y una
     * marca de número ("Unidad Nº 3", "Nro. 3"). Todas son palabras comunes: "Unidad de cuenta",
     * "Módulo de elasticidad", "Parte civil", "Tema a desarrollar" o "Parte una de las causas" no
     * son títulos (si van en MAYÚSCULAS, igual los toma [esMayusculas]).
     */
    fun empiezaConPalabraClave(texto: String): Boolean {
        var resto = PALABRA_CLAVE.find(texto)?.groupValues?.get(2) ?: return false
        CALIFICATIVO.find(resto)?.let { resto = resto.substring(it.range.last + 1) }
        MARCA_DE_NUMERO.find(resto)?.let { resto = resto.substring(it.range.last + 1) }
        val primero = resto.firstOrNull() ?: return false
        if (primero.isDigit()) return true
        val palabra = PRIMERA_PALABRA.find(resto)?.value ?: return false
        if (palabra.length == 1) return palabra[0].isUpperCase()
        if (ROMANO_VALIDO.matches(palabra.uppercase())) return true
        return palabra.lowercase() in NUMEROS_EN_LETRAS && CIERRE_DE_NUMERO_EN_LETRAS.containsMatchIn(resto.substring(palabra.length))
    }

    private val PALABRA_LARGA = Regex("\\p{L}{4,}")

    /**
     * true si está en MAYÚSCULAS: al menos 4 letras, el 80 % o más en mayúscula, al menos la mitad
     * de los caracteres visibles son letras y hay una palabra de 4 letras o más (para que
     * "PBI = C + I + G" no cuente).
     */
    fun esMayusculas(texto: String): Boolean {
        val letras = texto.count { it.isLetter() }
        if (letras < 4) return false
        val mayusculas = texto.count { it.isLetter() && it.isUpperCase() }
        if (mayusculas * 5 < letras * 4) return false
        val visibles = texto.count { !it.isWhitespace() }
        if (letras * 2 < visibles) return false
        return PALABRA_LARGA.containsMatchIn(texto)
    }

    /**
     * true si el texto, solo, puede ser un título: hasta [MAX_LARGO] caracteres, no es una
     * viñeta, no termina en `.` `,` `;` `:`, no tiene otro ítem numerado adentro (el final de un
     * sumario: "15.6. Atributos. 16. Persona y empresa") y está numerado ([numeracion]), empieza con
     * una palabra clave ([empiezaConPalabraClave]) o va en mayúsculas ([esMayusculas]).
     */
    fun pareceTitulo(texto: String): Boolean {
        val t = texto.trim()
        if (t.length < 3 || t.length > MAX_LARGO) return false
        if (t.startsWith("•")) return false
        if (t.last() in ".,;:") return false
        if (t.count { it.isLetter() } < 2) return false
        if (OTRO_ITEM.containsMatchIn(t)) return false
        return numeracion(t) != null || empiezaConPalabraClave(t) || esMayusculas(t)
    }

    /** Un segundo ítem numerado dentro del texto: ". 16. Persona". */
    private val OTRO_ITEM = Regex("[.;]\\s+\\d{1,2}(?:\\.\\d{1,2})*\\.\\s+\\p{Lu}")

    /**
     * Deja el título listo para mostrar: sin espacios de más y, si venía en MAYÚSCULAS, en tipo
     * oración ("3.1 ELASTICIDAD PRECIO" → "3.1 Elasticidad precio"). La numeración se conserva.
     * Al pasar a minúscula se respetan los números romanos chicos (I a XXXIX) y lo que parece una
     * sigla (palabras de hasta 3 letras que no son comunes, como PBI o UBA, y palabras sin vocales
     * o que empiezan con dos consonantes que no van juntas en castellano, como BCRA). Después de
     * ":" o de un guion separador sigue mayúscula. Los nombres propios en mayúsculas quedan en
     * minúscula: es la limitación conocida de esta conversión. Una palabra clave pegada a su número,
     * como la deja el OCR ("Capitulo1"), se separa ("Capitulo 1").
     */
    fun normalizarTitulo(texto: String): String {
        val t = CLAVE_PEGADA.replace(texto.trim().replace(ESPACIOS, " "), "$1 $2")
        if (!esMayusculas(t)) return t
        val prefijo = numeracion(t)?.prefijo ?: ""
        return prefijo + aTipoOracion(t.substring(prefijo.length))
    }

    private val CLAVE_PEGADA = Regex("^(unidad|cap[ií]tulo|m[oó]dulo|tema|parte|secci[oó]n|bloque)(\\d)", RegexOption.IGNORE_CASE)

    private val SOLO_PALABRA_CLAVE = Regex(
        "^(?:unidad|cap[ií]tulo|m[oó]dulo|tema|parte|secci[oó]n|bloque)\\s*(?:n\\.?\\s?[°º]\\.?\\s*|nro\\.?\\s*)?(?:\\d{1,3}|[IVXLC]{1,6})\\.?$",
        RegexOption.IGNORE_CASE,
    )

    /** true si el título es solo la palabra clave y su número ("Capítulo 2", "Unidad III", "Capitulo1"). */
    fun esSoloPalabraClave(texto: String): Boolean = SOLO_PALABRA_CLAVE.matches(texto.trim())

    private val ESPACIOS = Regex("\\s+")
    private val PALABRA = Regex("\\p{L}+")
    private val ROMANO_CHICO = Regex("^X{0,3}(?:IX|IV|V?I{0,3})$")
    private const val VOCALES = "aeiouáéíóúü"
    private val GRUPOS_INICIALES = setOf(
        "bl", "br", "cl", "cr", "dr", "fl", "fr", "gl", "gr", "kl", "kr", "pl", "pr", "tr",
        "ch", "ll", "rr", "ps", "tl", "gn", "pn",
    )

    /** Palabras cortas comunes: van en minúscula aunque tengan pocas letras (no son siglas). */
    private val PALABRAS_COMUNES = setOf(
        "a", "e", "o", "u", "y", "al", "da", "de", "el", "en", "es", "ha", "he", "ir", "la", "le",
        "lo", "me", "mi", "ni", "no", "se", "si", "sí", "su", "te", "tu", "un", "va", "ya",
        "año", "con", "dar", "del", "día", "dos", "era", "fin", "fue", "han", "hay", "las", "les",
        "ley", "los", "luz", "mar", "más", "mes", "muy", "nos", "par", "paz", "por", "qué", "que",
        "red", "rol", "sal", "sea", "ser", "sin", "sol", "son", "sus", "tal", "uno", "una", "ver",
        "vez", "voz",
    )

    private fun aTipoOracion(texto: String): String {
        val minusculas = PALABRA.replace(texto) { m ->
            if (conservarMayusculas(m.value)) m.value else m.value.lowercase()
        }
        val sb = StringBuilder(minusculas)
        var capitalizar = true
        for (i in sb.indices) {
            val c = sb[i]
            when {
                c.isLetter() -> {
                    if (capitalizar) sb.setCharAt(i, c.uppercaseChar())
                    capitalizar = false
                }
                c.isDigit() -> capitalizar = false
                c == ':' || c == '–' || c == '—' || c == '·' -> capitalizar = true
                c == '-' && i > 0 && sb[i - 1] == ' ' -> capitalizar = true
            }
        }
        return sb.toString()
    }

    private fun conservarMayusculas(palabra: String): Boolean {
        if (palabra.any { it.isLowerCase() }) return true
        val minus = palabra.lowercase()
        if (minus in PALABRAS_COMUNES) return false
        if (ROMANO_CHICO.matches(palabra)) return true
        if (palabra.length <= 3) return true
        if (minus.none { it in VOCALES }) return true
        val dosConsonantes = minus[0] !in VOCALES && minus[1] !in VOCALES
        return dosConsonantes && minus.substring(0, 2) !in GRUPOS_INICIALES
    }
}
